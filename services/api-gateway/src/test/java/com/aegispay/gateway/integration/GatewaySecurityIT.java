package com.aegispay.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for the API Gateway security and filter chain.
 *
 * Uses a real Redis container so rate-limiter headers and Redis INCR/EXPIRE
 * behaviour can be verified end-to-end.
 *
 * Downstream services are not started — routes that would proxy to them are
 * expected to return 503/504, which is fine for these security-focused tests.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewaySecurityIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Disable multi-IdP JWT resolution — no real IdP in tests
        registry.add("aegispay.gateway.oauth2-trusted-issuers[0]",
                () -> "http://localhost:9999/realms/test");
    }

    @Autowired
    private WebTestClient webTestClient;

    // ── Actuator endpoints are public ──────────────────────────────────────────

    @Test
    void actuatorHealthIsPublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Protected routes require authentication ────────────────────────────────

    @Test
    void unauthenticatedRequestToProtectedPathReturns401() {
        webTestClient.get()
                .uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.errorCode").isEqualTo("UNAUTHORIZED");
    }

    // ── Correlation ID filter adds headers ────────────────────────────────────

    @Test
    void responseAlwaysContainsCorrelationId() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectHeader().exists("X-Correlation-ID");
    }

    @Test
    void clientSuppliedCorrelationIdIsEchoed() {
        String clientId = "my-test-correlation-id";
        webTestClient.get()
                .uri("/actuator/health")
                .header("X-Correlation-ID", clientId)
                .exchange()
                .expectHeader().valueEquals("X-Correlation-ID", clientId);
    }

    // ── traceparent filter injects W3C headers ────────────────────────────────

    @Test
    void responseContainsTraceParent() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectHeader().exists("traceparent");
    }
}
