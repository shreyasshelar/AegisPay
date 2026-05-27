package com.aegispay.user.integration;

import com.aegispay.user.domain.dto.UserRegistrationRequest;
import com.aegispay.user.domain.entity.User;
import com.aegispay.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"user.registered", "kyc.status.changed"})
class UserServiceIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aegispay_users_test")
            .withUsername("aegispay")
            .withPassword("aegispay");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Disable Redis for integration test (no Redis container)
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        // Disable OAuth2 resource server JWKS resolution
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:9999/realms/test");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @Test
    void registerPersistsUserAndReturns201() throws Exception {
        UserRegistrationRequest request = new UserRegistrationRequest(
                "alice@example.com", "+919876543210", "Alice", "Smith", null);

        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j
                                .subject("ext-int-001")
                                .claim("aegispay_role", "CUSTOMER")))
                        .header("X-Idempotency-Key", "int-idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.email").value("a***@example.com"));

        assertThat(userRepository.findByExternalId("ext-int-001")).isPresent();
    }

    @Test
    void registerIsIdempotent() throws Exception {
        // First call
        UserRegistrationRequest request = new UserRegistrationRequest(
                "bob@example.com", null, "Bob", "Jones", null);

        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("ext-int-002")))
                        .header("X-Idempotency-Key", "int-idem-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second call with same externalId — idempotent: returns existing record with 200 OK (not 201)
        mockMvc.perform(post("/api/v1/users/register")
                        .with(jwt().jwt(j -> j.subject("ext-int-002")))
                        .header("X-Idempotency-Key", "int-idem-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(userRepository.findAll().stream()
                .filter(u -> "ext-int-002".equals(u.getExternalId()))
                .count()).isEqualTo(1);
    }

    @Test
    void getMeReturns404WhenNotRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j.subject("unknown-ext-id"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
