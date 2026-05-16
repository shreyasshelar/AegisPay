package com.aegispay.user.controller;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.user.config.SecurityConfig;
import com.aegispay.user.domain.dto.*;
import com.aegispay.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for KYC confirm, KYC AI-callback, push-token,
 * and back-office user-listing endpoints.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerKycTest {

    @MockBean JwtDecoder jwtDecoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private UserService userService;

    private static final UUID USER_ID = UUID.randomUUID();

    // ── PATCH /{userId}/kyc  (user confirms KYC submission) ──────────────────

    @Test
    void confirmKyc_returns_200_for_owner() throws Exception {
        KycConfirmRequest req = new KycConfirmRequest("AADHAAR");
        KycStatusResponse resp = kycStatusResponse(KycStatus.DOCUMENT_SUBMITTED);

        when(userService.confirmKycSubmission(eq(USER_ID), any(KycConfirmRequest.class), eq("sub-owner")))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/users/{id}/kyc", USER_ID)
                        .with(jwt().jwt(j -> j.subject("sub-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("DOCUMENT_SUBMITTED"));
    }

    @Test
    void confirmKyc_returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/kyc", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"AADHAAR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmKyc_returns_400_on_missing_document_type() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/kyc", USER_ID)
                        .with(jwt().jwt(j -> j.subject("sub-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))  // documentType missing
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /{userId}/kyc/status  (AI callback, ADMIN only) ─────────────────

    @Test
    void kycCallback_returns_200_for_admin() throws Exception {
        KycStatusUpdateRequest req = KycStatusUpdateRequest.builder()
                .documentId(UUID.randomUUID())
                .newStatus(KycStatus.APPROVED)
                .tamperedFlag(false)
                .build();

        KycStatusResponse resp = kycStatusResponse(KycStatus.APPROVED);
        when(userService.processAiCallback(eq(USER_ID), any(KycStatusUpdateRequest.class)))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/v1/users/{id}/kyc/status", USER_ID)
                        .with(jwt().jwt(j -> j.subject("admin-sub"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("APPROVED"));
    }

    @Test
    void kycCallback_returns_403_for_customer_role() throws Exception {
        KycStatusUpdateRequest req = KycStatusUpdateRequest.builder()
                .documentId(UUID.randomUUID())
                .newStatus(KycStatus.APPROVED)
                .build();

        mockMvc.perform(patch("/api/v1/users/{id}/kyc/status", USER_ID)
                        .with(jwt().jwt(j -> j.subject("cust-sub"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void kycCallback_returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/kyc/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"VERIFIED\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{userId}/push-token ─────────────────────────────────────────────

    @Test
    void registerPushToken_returns_204_for_owner() throws Exception {
        PushTokenRequest req = new PushTokenRequest("fcm-token-123", "android");

        mockMvc.perform(post("/api/v1/users/{id}/push-token", USER_ID)
                        .with(jwt().jwt(j -> j.subject("sub-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerPushToken_returns_400_on_invalid_platform() throws Exception {
        String badReq = "{\"token\":\"tok\",\"platform\":\"windows\"}";

        mockMvc.perform(post("/api/v1/users/{id}/push-token", USER_ID)
                        .with(jwt().jwt(j -> j.subject("sub-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badReq))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerPushToken_returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/push-token", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"platform\":\"ios\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /users  (back-office user listing) ────────────────────────────────

    @Test
    void listUsers_returns_200_for_back_office_role() throws Exception {
        when(userService.listUsers(0, 50, null))
                .thenReturn(new PageImpl<>(List.of(buildUserResponse())));

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(j -> j.subject("bo-sub"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_BACK_OFFICE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].kycStatus").value("PENDING"));
    }

    @Test
    void listUsers_returns_200_for_admin_role() throws Exception {
        when(userService.listUsers(0, 50, "APPROVED"))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/users")
                        .param("kycStatus", "APPROVED")
                        .with(jwt().jwt(j -> j.subject("admin-sub"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void listUsers_returns_403_for_customer_role() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(j -> j.subject("cust-sub"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private KycStatusResponse kycStatusResponse(KycStatus status) {
        return KycStatusResponse.builder()
                .userId(USER_ID)
                .kycStatus(status)
                .documentType("AADHAAR")
                .ocrStatus("PENDING")
                .tamperedFlag(false)
                .build();
    }

    private UserResponse buildUserResponse() {
        return UserResponse.builder()
                .id(USER_ID)
                .externalId("ext-001")
                .email("u***@example.com")
                .firstName("Test")
                .lastName("User")
                .role("CUSTOMER")
                .kycStatus(KycStatus.PENDING)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
