package com.aegispay.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * All gateway configuration is externalised here — nothing is hardcoded.
 * Values come from application.yml → K8s ConfigMap in production.
 */
@Data
@ConfigurationProperties(prefix = "aegispay.gateway")
public class GatewayProperties {

    /** Ordered list of trusted OAuth2 issuer URIs (Keycloak, Entra, Okta). */
    private List<String> oauth2TrustedIssuers = new ArrayList<>();

    private ServiceUris services = new ServiceUris();
    private RateLimiter rateLimiter = new RateLimiter();
    private Cors cors = new Cors();

    @Data
    public static class ServiceUris {
        private String userService        = "http://user-service:8081";
        private String transactionService = "http://transaction-service:8082";
        private String ledgerService      = "http://ledger-service:8083";
        private String orchestratorService = "http://payment-orchestrator:8084";
        private String riskEngine         = "http://risk-engine:8085";
        private String notificationService = "http://notification-service:8086";
        private String aiPlatform         = "http://ai-platform:8088";
    }

    @Data
    public static class RateLimiter {
        /** Requests allowed per windowSeconds per key. */
        private int maxRequests     = 100;
        /** Rolling window length in seconds. */
        private int windowSeconds   = 60;
        /** Redis TTL for rate limit keys (slightly longer than window to avoid early expiry). */
        private int keyTtlSeconds   = 65;
        /** Header name clients see for remaining quota. */
        private String remainingHeader = "X-RateLimit-Remaining";
        private String limitHeader     = "X-RateLimit-Limit";
        private String resetHeader     = "X-RateLimit-Reset";
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("*");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private boolean allowCredentials = false;
        private long maxAge = 3600L;
    }
}
