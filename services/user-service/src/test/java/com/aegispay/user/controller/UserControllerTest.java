package com.aegispay.user.controller;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.user.config.SecurityConfig;
import com.aegispay.user.domain.dto.RegistrationResult;
import com.aegispay.user.domain.dto.UserRegistrationRequest;
import com.aegispay.user.domain.dto.UserResponse;
import com.aegispay.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    // Provide a JwtDecoder mock so Spring Security can initialise the resource-server
    // filter chain in the @WebMvcTest slice (no issuer-uri available in test context).
    @MockBean JwtDecoder jwtDecoder;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private UserService userService;

    @Test
    void registerReturns201() throws Exception {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "alice@example.com", "+919876543210", "Alice", "Smith", null);

        UserResponse mockResponse = buildUserResponse();
        // register() now returns RegistrationResult; created=true → controller emits 201
        when(userService.register(any(), eq("idem-123"), any(Jwt.class)))
                .thenReturn(new RegistrationResult(mockResponse, true));

        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("ext-123").claim("aegispay_role", "CUSTOMER")))
                        .header("X-Idempotency-Key", "idem-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kycStatus").value("PENDING"));
    }

    @Test
    void registerReturns401WhenNotAuthenticated() throws Exception {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "alice@example.com", null, "Alice", "Smith", null);

        mockMvc.perform(post("/api/v1/users/register")
                        .header("X-Idempotency-Key", "idem-456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerReturns400WhenEmailMissing() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("ext-123")))
                        .header("X-Idempotency-Key", "idem-789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getMeReturns200() throws Exception {
        UserResponse mockResponse = buildUserResponse();
        when(userService.getByExternalId("ext-123")).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j.subject("ext-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("PENDING"));
    }

    @Test
    void getByIdRequiresAdminRole() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getById(userId)).thenReturn(buildUserResponse());

        // Without back-office role → 403
        mockMvc.perform(get("/api/v1/users/" + userId)
                        .with(jwt().jwt(j -> j.subject("ext-123"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        // With back-office role → 200
        mockMvc.perform(get("/api/v1/users/" + userId)
                        .with(jwt().jwt(j -> j.subject("ext-123"))
                                   .authorities(new SimpleGrantedAuthority("ROLE_BACK_OFFICE"))))
                .andExpect(status().isOk());
    }

    private UserResponse buildUserResponse() {
        return UserResponse.builder()
                .id(UUID.randomUUID())
                .externalId("ext-123")
                .email("a***@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .role("CUSTOMER")
                .kycStatus(KycStatus.PENDING)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
