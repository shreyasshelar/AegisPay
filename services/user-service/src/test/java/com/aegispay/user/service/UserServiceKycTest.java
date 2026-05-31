package com.aegispay.user.service;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.common.domain.exception.AegisPayException;
import com.aegispay.user.domain.dto.*;
import com.aegispay.user.domain.entity.KycDocument;
import com.aegispay.user.domain.entity.User;
import com.aegispay.user.domain.mapper.UserMapperImpl;
import com.aegispay.user.idempotency.IdempotencyService;
import com.aegispay.user.kafka.UserEventProducer;
import com.aegispay.user.kyc.KycStateMachine;
import com.aegispay.user.outbox.OutboxEntry;
import com.aegispay.user.outbox.OutboxEntryRepository;
import com.aegispay.user.repository.KycDocumentRepository;
import com.aegispay.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for KYC flows, multi-tenant registration, push-token registration,
 * and back-office user listing — covering the areas not in UserServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceKycTest {

    @Mock private UserRepository         userRepository;
    @Mock private KycDocumentRepository  kycDocumentRepository;
    @Mock private OutboxEntryRepository  outboxEntryRepository;
    @Spy  private UserMapperImpl         userMapper;
    @Spy  private KycStateMachine        kycStateMachine;
    @Mock private IdempotencyService     idempotencyService;
    @Mock private UserEventProducer      eventProducer;
    @Mock private AiPlatformClient       aiPlatformClient;
    @Mock private KeycloakAdminService   keycloakAdminService;

    UserService userService;

    private static final UUID   USER_ID      = UUID.randomUUID();
    private static final String EXTERNAL_ID  = "ext-abc-999";
    private static final String TENANT_ID    = "tenant-acme";

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, kycDocumentRepository, outboxEntryRepository,
                userMapper, kycStateMachine, idempotencyService, eventProducer,
                aiPlatformClient, keycloakAdminService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-tenant registration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class MultiTenantRegistration {

        @Test
        void tenantId_from_jwt_claim_takes_precedence_over_request_body() {
            Jwt jwt = jwtWith("CUSTOMER", "tenant-jwt");
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("t@example.com")).thenReturn(false);

            User saved = stubSave();
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildUserRegisteredEntry(any())).thenReturn(stubOutbox(saved));

            UserRegistrationRequest request = new UserRegistrationRequest(
                    "t@example.com", null, "Tenant", "User", "tenant-request");

            userService.register(request, "idem-mt-1", jwt);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-jwt");
        }

        @Test
        void tenantId_falls_back_to_request_body_when_jwt_claim_absent() {
            Jwt jwt = Jwt.withTokenValue("tok")
                    .header("alg", "RS256")
                    .subject(EXTERNAL_ID)
                    .claim("aegispay_role", "CUSTOMER")
                    // intentionally no aegispay_tenant_id claim
                    .build();

            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("t2@example.com")).thenReturn(false);

            User saved = stubSave();
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildUserRegisteredEntry(any())).thenReturn(stubOutbox(saved));

            UserRegistrationRequest request = new UserRegistrationRequest(
                    "t2@example.com", null, "Second", "Tenant", "tenant-fallback");

            userService.register(request, "idem-mt-2", jwt);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-fallback");
        }

        @Test
        void role_is_persisted_from_jwt_claim() {
            Jwt jwt = jwtWith("BACK_OFFICE", TENANT_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("bo@example.com")).thenReturn(false);

            User saved = stubSave();
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildUserRegisteredEntry(any())).thenReturn(stubOutbox(saved));

            userService.register(
                    new UserRegistrationRequest("bo@example.com", null, "Back", "Office", TENANT_ID),
                    "idem-mt-3", jwt);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo("BACK_OFFICE");
        }

        @Test
        void keycloak_write_attributes_called_with_new_user_id_and_tenant() {
            Jwt jwt = jwtWith("CUSTOMER", TENANT_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("kc@example.com")).thenReturn(false);

            User saved = stubSave();
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildUserRegisteredEntry(any())).thenReturn(stubOutbox(saved));

            userService.register(
                    new UserRegistrationRequest("kc@example.com", null, "KC", "User", TENANT_ID),
                    "idem-mt-4", jwt);

            // In unit tests with mocked JPA, the entity ID is null (no real @GeneratedValue).
            // We verify the call was made with the correct externalId and tenantId; the UUID
            // arg is matched with any() since it will be null in the mock context.
            verify(keycloakAdminService).writeUserAttributes(
                    eq(EXTERNAL_ID), any(), eq(TENANT_ID));
        }

        /** Simulates Google/Entra/Okta SSO: externalId is the IdP sub claim. */
        @Test
        void social_sso_signup_uses_idp_sub_as_external_id() {
            String googleSub = "google-oauth2|108001234567890";
            Jwt jwt = Jwt.withTokenValue("tok")
                    .header("alg", "RS256")
                    .subject(googleSub)
                    .claim("aegispay_role", "CUSTOMER")
                    .claim("aegispay_tenant_id", "tenant-google")
                    .build();

            when(userRepository.findByExternalId(googleSub)).thenReturn(Optional.empty());
            when(userRepository.existsByEmail("google@example.com")).thenReturn(false);

            User saved = User.builder()
                    .id(UUID.randomUUID())
                    .externalId(googleSub)
                    .email("google@example.com")
                    .firstName("Google")
                    .lastName("User")
                    .role("CUSTOMER")
                    .tenantId("tenant-google")
                    .kycStatus(KycStatus.PENDING)
                    .build();
            when(userRepository.save(any())).thenReturn(saved);
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildUserRegisteredEntry(any())).thenReturn(stubOutbox(saved));

            RegistrationResult result = userService.register(
                    new UserRegistrationRequest("google@example.com", null, "Google", "User", "tenant-google"),
                    "idem-google-1", jwt);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getExternalId()).isEqualTo(googleSub);
            assertThat(result.user().kycStatus()).isEqualTo(KycStatus.PENDING);
            assertThat(result.created()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KYC: confirmKycSubmission
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class ConfirmKycSubmission {

        @Test
        void transitions_pending_to_document_submitted() {
            User user = pendingUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(kycDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildKycStatusChangedEntry(any(), any(), any(), any()))
                    .thenReturn(stubOutbox(user));

            KycStatusResponse resp = userService.confirmKycSubmission(
                    USER_ID,
                    new KycConfirmRequest("AADHAAR"),
                    EXTERNAL_ID);

            assertThat(resp.kycStatus()).isEqualTo(KycStatus.DOCUMENT_SUBMITTED);
            assertThat(resp.ocrStatus()).isEqualTo("PENDING");
            assertThat(resp.tamperedFlag()).isFalse();
        }

        @Test
        void throws_forbidden_when_caller_is_not_owner() {
            User user = pendingUser(); // externalId = EXTERNAL_ID
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.confirmKycSubmission(
                    USER_ID, new KycConfirmRequest("PASSPORT"), "different-user"))
                    .isInstanceOf(AegisPayException.class)
                    .satisfies(e -> assertThat(((AegisPayException) e).getErrorCode()).isEqualTo("FORBIDDEN"));
        }

        @Test
        void creates_pending_kyc_document_record() {
            User user = pendingUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(kycDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildKycStatusChangedEntry(any(), any(), any(), any()))
                    .thenReturn(stubOutbox(user));

            userService.confirmKycSubmission(USER_ID, new KycConfirmRequest("PAN_CARD"), EXTERNAL_ID);

            ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
            verify(kycDocumentRepository).save(captor.capture());
            assertThat(captor.getValue().getDocumentType()).isEqualTo("PAN_CARD");
            assertThat(captor.getValue().getOcrStatus()).isEqualTo("PENDING");
            assertThat(captor.getValue().isTamperedFlag()).isFalse();
        }

        @Test
        void publishes_kyc_status_changed_outbox_event() {
            User user = pendingUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);
            when(kycDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildKycStatusChangedEntry(any(), any(), any(), any()))
                    .thenReturn(stubOutbox(user));

            userService.confirmKycSubmission(USER_ID, new KycConfirmRequest("AADHAAR"), EXTERNAL_ID);

            verify(eventProducer).buildKycStatusChangedEntry(
                    any(User.class), eq(KycStatus.PENDING), eq("AADHAAR"), isNull());
            verify(outboxEntryRepository).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KYC: processAiCallback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class ProcessAiCallback {

        @Test
        void transitions_ai_processing_to_verified() {
            User user = userWithKycStatus(KycStatus.AI_PROCESSING);
            KycDocument doc = kycDocument(user.getId(), KycStatus.AI_PROCESSING);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(kycDocumentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
            when(userRepository.save(any())).thenReturn(user);
            when(kycDocumentRepository.save(any())).thenReturn(doc);
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildKycStatusChangedEntry(any(), any(), any(), any()))
                    .thenReturn(stubOutbox(user));

            KycStatusUpdateRequest request = KycStatusUpdateRequest.builder()
                    .documentId(doc.getId())
                    .newStatus(KycStatus.APPROVED)
                    .tamperedFlag(false)
                    .qualityScore(new java.math.BigDecimal("0.97"))
                    .build();

            KycStatusResponse resp = userService.processAiCallback(USER_ID, request);

            assertThat(resp.kycStatus()).isEqualTo(KycStatus.APPROVED);
            assertThat(resp.tamperedFlag()).isFalse();
        }

        @Test
        void document_submitted_transitions_via_ai_processing_then_rejected() {
            User user = userWithKycStatus(KycStatus.DOCUMENT_SUBMITTED);
            KycDocument doc = kycDocument(user.getId(), KycStatus.DOCUMENT_SUBMITTED);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(kycDocumentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
            when(userRepository.save(any())).thenReturn(user);
            when(kycDocumentRepository.save(any())).thenReturn(doc);
            when(outboxEntryRepository.save(any())).thenReturn(null);
            when(eventProducer.buildKycStatusChangedEntry(any(), any(), any(), any()))
                    .thenReturn(stubOutbox(user));

            KycStatusUpdateRequest request = KycStatusUpdateRequest.builder()
                    .documentId(doc.getId())
                    .newStatus(KycStatus.REJECTED)
                    .tamperedFlag(true)
                    .rejectionReason("Document appears tampered")
                    .build();

            KycStatusResponse resp = userService.processAiCallback(USER_ID, request);

            assertThat(resp.kycStatus()).isEqualTo(KycStatus.REJECTED);
            assertThat(resp.tamperedFlag()).isTrue();
            assertThat(resp.rejectionReason()).isEqualTo("Document appears tampered");
        }

        @Test
        void throws_when_document_not_found() {
            User user = userWithKycStatus(KycStatus.AI_PROCESSING);
            UUID unknownDocId = UUID.randomUUID();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(kycDocumentRepository.findById(unknownDocId)).thenReturn(Optional.empty());

            KycStatusUpdateRequest req = KycStatusUpdateRequest.builder()
                    .documentId(unknownDocId)
                    .newStatus(KycStatus.APPROVED)
                    .build();

            assertThatThrownBy(() -> userService.processAiCallback(USER_ID, req))
                    .isInstanceOf(AegisPayException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push token registration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class PushTokenRegistration {

        @Test
        void stores_fcm_token_for_android() {
            User user = pendingUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            userService.registerPushToken(USER_ID,
                    new PushTokenRequest("fcm-token-abc123", "android"), EXTERNAL_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPushToken()).isEqualTo("fcm-token-abc123");
            assertThat(captor.getValue().getPushTokenPlatform()).isEqualTo("android");
        }

        @Test
        void stores_apns_token_for_ios() {
            User user = pendingUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            userService.registerPushToken(USER_ID,
                    new PushTokenRequest("apns-device-token-xyz", "ios"), EXTERNAL_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPushToken()).isEqualTo("apns-device-token-xyz");
            assertThat(captor.getValue().getPushTokenPlatform()).isEqualTo("ios");
        }

        @Test
        void throws_forbidden_when_caller_is_not_owner() {
            User user = pendingUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.registerPushToken(
                    USER_ID, new PushTokenRequest("tok", "android"), "other-user"))
                    .isInstanceOf(AegisPayException.class)
                    .satisfies(e -> assertThat(((AegisPayException) e).getErrorCode()).isEqualTo("FORBIDDEN"));
        }

        @Test
        void replaces_existing_token_on_repeated_calls() {
            User user = pendingUser();
            user.setPushToken("old-token");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            userService.registerPushToken(USER_ID,
                    new PushTokenRequest("new-token-456", "ios"), EXTERNAL_ID);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPushToken()).isEqualTo("new-token-456");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Back-office: listUsers
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class ListUsers {

        @Test
        void returns_paginated_users_without_filter() {
            User user = pendingUser();
            var page = new PageImpl<>(List.of(user));
            when(userRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

            var result = userService.listUsers(0, 10, null);
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void filters_by_kyc_status() {
            User user = userWithKycStatus(KycStatus.APPROVED);
            var page = new PageImpl<>(List.of(user));
            when(userRepository.findAllByKycStatusOrderByCreatedAtDesc(
                    eq(KycStatus.APPROVED), any())).thenReturn(page);

            var result = userService.listUsers(0, 50, "APPROVED");
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void caps_page_size_at_100() {
            when(userRepository.findAllByOrderByCreatedAtDesc(
                    argThat(p -> p.getPageSize() <= 100)))
                    .thenReturn(new PageImpl<>(List.of()));

            userService.listUsers(0, 999, null);

            verify(userRepository).findAllByOrderByCreatedAtDesc(
                    argThat(p -> p.getPageSize() == 100));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Jwt jwtWith(String role, String tenantId) {
        return Jwt.withTokenValue("tok")
                .header("alg", "RS256")
                .subject(EXTERNAL_ID)
                .claim("aegispay_role", role)
                .claim("aegispay_tenant_id", tenantId)
                .build();
    }

    private User pendingUser() {
        return User.builder()
                .id(USER_ID)
                .externalId(EXTERNAL_ID)
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .role("CUSTOMER")
                .tenantId(TENANT_ID)
                .kycStatus(KycStatus.PENDING)
                .build();
    }

    private User userWithKycStatus(KycStatus status) {
        return User.builder()
                .id(USER_ID)
                .externalId(EXTERNAL_ID)
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .role("CUSTOMER")
                .tenantId(TENANT_ID)
                .kycStatus(status)
                .build();
    }

    private KycDocument kycDocument(UUID userId, KycStatus ignoredStatus) {
        return KycDocument.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .documentType("AADHAAR")
                .documentRef("s3://bucket/doc.jpg")
                .ocrStatus("PENDING")
                .build();
    }

    private User stubSave() {
        User u = User.builder()
                .id(UUID.randomUUID())
                .externalId(EXTERNAL_ID)
                .email("t@example.com")
                .firstName("T")
                .lastName("U")
                .role("CUSTOMER")
                .tenantId(TENANT_ID)
                .kycStatus(KycStatus.PENDING)
                .build();
        when(userRepository.save(any())).thenReturn(u);
        return u;
    }

    private OutboxEntry stubOutbox(User user) {
        return OutboxEntry.builder()
                .aggregateId(user.getId().toString())
                .aggregateType("User")
                .eventType("UserEvent")
                .topic("user.events")
                .messageKey(user.getId().toString())
                .payload("{}")
                .build();
    }
}
