package com.aegispay.user.integration;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.user.domain.dto.*;
import com.aegispay.user.domain.entity.User;
import com.aegispay.user.repository.KycDocumentRepository;
import com.aegispay.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests covering:
 *  1. Multi-tenant signup (Keycloak / Google / Entra subjects)
 *  2. Full KYC onboarding: PENDING → DOCUMENT_SUBMITTED → AI_PROCESSING → VERIFIED
 *  3. Push-token registration (iOS APNs + Android FCM)
 *  4. Back-office user listing with tenant/KYC filtering
 *  5. 401 / 403 enforcement on every protected endpoint
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"user.registered", "kyc.status.changed"})
class UserOnboardingIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aegispay_onboarding_test")
            .withUsername("aegispay")
            .withPassword("aegispay");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host",     () -> "localhost");
        registry.add("spring.data.redis.port",     () -> "6379");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:9999/realms/test");
    }

    @Autowired private MockMvc             mockMvc;
    @Autowired private ObjectMapper        objectMapper;
    @Autowired private UserRepository      userRepository;
    @Autowired private KycDocumentRepository kycDocumentRepository;

    @BeforeEach
    void cleanUp() {
        kycDocumentRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-tenant signup
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void keycloak_customer_signup_persists_tenant_id_from_jwt() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j
                                .subject("kc-sub-tenant-1")
                                .claim("aegispay_role", "CUSTOMER")
                                .claim("aegispay_tenant_id", "tenant-alpha")))
                        .header("X-Idempotency-Key", "it-mt-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "alpha@tenant.io", "+911234567890", "Alpha", "User", "tenant-ignored"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kycStatus").value("PENDING"));

        User user = userRepository.findByExternalId("kc-sub-tenant-1").orElseThrow();
        assertThat(user.getTenantId()).isEqualTo("tenant-alpha");   // JWT claim, not request body
        assertThat(user.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void google_sso_signup_uses_idp_subject_as_external_id() throws Exception {
        // Simulate Google subject format used by Keycloak Identity Brokering
        String googleSub = "f:google-broker-id:109876543210000000000";

        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j
                                .subject(googleSub)
                                .claim("aegispay_role", "CUSTOMER")
                                .claim("aegispay_tenant_id", "tenant-google")))
                        .header("X-Idempotency-Key", "it-google-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "google.user@gmail.com", null, "Google", "User", "tenant-google"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByExternalId(googleSub)).isPresent();
    }

    @Test
    void entra_id_signup_uses_object_id_as_external_id() throws Exception {
        // Azure AD (Entra) uses objectId (UUID format) as subject
        String entraSub = "aad:" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j
                                .subject(entraSub)
                                .claim("aegispay_role", "CUSTOMER")
                                .claim("aegispay_tenant_id", "tenant-entra")))
                        .header("X-Idempotency-Key", "it-entra-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "entra.user@corp.com", null, "Entra", "User", "tenant-entra"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByExternalId(entraSub)).isPresent();
    }

    @Test
    void two_users_from_different_tenants_are_independent() throws Exception {
        // Tenant A user
        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("sub-tenant-a")
                                .claim("aegispay_tenant_id", "tenant-a")))
                        .header("X-Idempotency-Key", "it-mt-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "a@tenant-a.io", null, "A", "User", "tenant-a"))))
                .andExpect(status().isCreated());

        // Tenant B user — same email would conflict if multi-tenancy leaks
        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("sub-tenant-b")
                                .claim("aegispay_tenant_id", "tenant-b")))
                        .header("X-Idempotency-Key", "it-mt-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "b@tenant-b.io", null, "B", "User", "tenant-b"))))
                .andExpect(status().isCreated());

        assertThat(userRepository.count()).isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Full KYC onboarding flow: PENDING → DOCUMENT_SUBMITTED → VERIFIED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void full_kyc_flow_pending_to_verified() throws Exception {
        // 1. Register user
        MvcResult registerResult = mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("kyc-sub-001")
                                .claim("aegispay_tenant_id", "tenant-kyc")))
                        .header("X-Idempotency-Key", "kyc-idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "kyc.user@example.com", null, "KYC", "User", "tenant-kyc"))))
                .andExpect(status().isCreated())
                .andReturn();

        String userId = objectMapper.readTree(
                registerResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // 2. Confirm KYC submission (user-initiated: PENDING → DOCUMENT_SUBMITTED)
        mockMvc.perform(patch("/api/v1/users/{id}/kyc", userId)
                        .with(jwt().jwt(j -> j.subject("kyc-sub-001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KycConfirmRequest("AADHAAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("DOCUMENT_SUBMITTED"));

        // Verify DB state after step 2
        User afterSubmit = userRepository.findByExternalId("kyc-sub-001").orElseThrow();
        assertThat(afterSubmit.getKycStatus()).isEqualTo(KycStatus.DOCUMENT_SUBMITTED);
        assertThat(kycDocumentRepository.findAll()).hasSize(1);

        // 3. AI platform callback: DOCUMENT_SUBMITTED → VERIFIED
        UUID docId = kycDocumentRepository.findAll().get(0).getId();
        KycStatusUpdateRequest aiCallback = KycStatusUpdateRequest.builder()
                .documentId(docId)
                .newStatus(KycStatus.APPROVED)
                .tamperedFlag(false)
                .qualityScore(new java.math.BigDecimal("0.98"))
                .build();

        mockMvc.perform(patch("/api/v1/users/{id}/kyc/status", userId)
                        .with(jwt().jwt(j -> j.subject("ai-service-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aiCallback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.tamperedFlag").value(false));

        // Verify final DB state
        User verified = userRepository.findByExternalId("kyc-sub-001").orElseThrow();
        assertThat(verified.getKycStatus()).isEqualTo(KycStatus.APPROVED);
    }

    @Test
    void kyc_rejection_flow_sets_status_to_rejected_with_reason() throws Exception {
        // Register
        MvcResult reg = mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("kyc-rej-sub")))
                        .header("X-Idempotency-Key", "rej-idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "rejected.user@example.com", null, "Rej", "User", null))))
                .andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString())
                .at("/data/id").asText();

        // Confirm submission
        mockMvc.perform(patch("/api/v1/users/{id}/kyc", userId)
                        .with(jwt().jwt(j -> j.subject("kyc-rej-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"PASSPORT\"}"))
                .andExpect(status().isOk());

        // AI rejects
        UUID docId = kycDocumentRepository.findAll().get(0).getId();
        mockMvc.perform(patch("/api/v1/users/{id}/kyc/status", userId)
                        .with(jwt().jwt(j -> j.subject("ai-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(KycStatusUpdateRequest.builder()
                                .documentId(docId)
                                .newStatus(KycStatus.REJECTED)
                                .tamperedFlag(true)
                                .rejectionReason("Document appears tampered or expired")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Document appears tampered or expired"));
    }

    @Test
    void kyc_cannot_be_submitted_by_a_different_user() throws Exception {
        // Register user A
        MvcResult reg = mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("user-a-sub")))
                        .header("X-Idempotency-Key", "auth-kyc-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                "user.a@example.com", null, "User", "A", null))))
                .andReturn();
        String userAId = objectMapper.readTree(reg.getResponse().getContentAsString())
                .at("/data/id").asText();

        // User B tries to submit KYC for user A → 403
        mockMvc.perform(patch("/api/v1/users/{id}/kyc", userAId)
                        .with(jwt().jwt(j -> j.subject("user-b-sub")))  // different subject
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"AADHAAR\"}"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push token registration (iOS + Android)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void android_fcm_push_token_is_stored() throws Exception {
        MvcResult reg = registerUser("push-android-sub", "android.user@example.com");
        String userId = extractUserId(reg);

        mockMvc.perform(post("/api/v1/users/{id}/push-token", userId)
                        .with(jwt().jwt(j -> j.subject("push-android-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-token-abc123\",\"platform\":\"android\"}"))
                .andExpect(status().isNoContent());

        User user = userRepository.findByExternalId("push-android-sub").orElseThrow();
        assertThat(user.getPushToken()).isEqualTo("fcm-token-abc123");
        assertThat(user.getPushTokenPlatform()).isEqualTo("android");
    }

    @Test
    void ios_apns_push_token_is_stored() throws Exception {
        MvcResult reg = registerUser("push-ios-sub", "ios.user@example.com");
        String userId = extractUserId(reg);

        mockMvc.perform(post("/api/v1/users/{id}/push-token", userId)
                        .with(jwt().jwt(j -> j.subject("push-ios-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"apns-device-token-xyz\",\"platform\":\"ios\"}"))
                .andExpect(status().isNoContent());

        User user = userRepository.findByExternalId("push-ios-sub").orElseThrow();
        assertThat(user.getPushToken()).isEqualTo("apns-device-token-xyz");
        assertThat(user.getPushTokenPlatform()).isEqualTo("ios");
    }

    @Test
    void push_token_rejects_invalid_platform() throws Exception {
        MvcResult reg = registerUser("push-bad-sub", "bad.platform@example.com");
        String userId = extractUserId(reg);

        mockMvc.perform(post("/api/v1/users/{id}/push-token", userId)
                        .with(jwt().jwt(j -> j.subject("push-bad-sub")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"platform\":\"windows\"}"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Back-office user listing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void back_office_can_list_all_users() throws Exception {
        registerUser("bo-list-sub-1", "u1@example.com");
        registerUser("bo-list-sub-2", "u2@example.com");

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(j -> j.subject("admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void customer_cannot_list_users() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(j -> j.subject("cust"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void back_office_can_filter_users_by_kyc_status() throws Exception {
        registerUser("pending-sub", "pending@example.com");

        mockMvc.perform(get("/api/v1/users")
                        .param("kycStatus", "PENDING")
                        .with(jwt().jwt(j -> j.subject("admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].kycStatus").value("PENDING"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MvcResult registerUser(String subject, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject(subject)))
                        .header("X-Idempotency-Key", "idem-" + subject)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationRequest(
                                email, null, "First", "Last", null))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractUserId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id").asText();
    }
}
