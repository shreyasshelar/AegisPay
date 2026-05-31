package com.aegispay.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aegispay")
public class UserServiceProperties {

    private Outbox outbox = new Outbox();
    private Idempotency idempotency = new Idempotency();
    private AiPlatform aiPlatform = new AiPlatform();

    /**
     * Shared secret for authenticating internal service-to-service calls.
     * The AI Platform passes this in the {@code X-Internal-Api-Key} header when
     * calling back with KYC processing results.
     * Override via {@code USER_SERVICE_INTERNAL_API_KEY} env var in production.
     */
    private String internalApiKey = "aegispay-internal-dev-key";

    @Data
    public static class Outbox {
        private int batchSize = 100;
        private long pollIntervalMs = 1000;
    }

    @Data
    public static class Idempotency {
        private long ttlSeconds = 86400;
    }

    @Data
    public static class AiPlatform {
        private String baseUrl = "http://ai-platform:8088";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;
    }
}
