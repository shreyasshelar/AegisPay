package com.aegispay.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aegispay.ai")
@Getter
@Setter
public class AiPlatformProperties {

    private Rag rag = new Rag();
    private Agent agent = new Agent();
    private UserService userService = new UserService();
    private boolean auditEnabled = true;

    @Getter @Setter
    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.7;
    }

    @Getter @Setter
    public static class Agent {
        private int maxIterations = 10;
    }

    /**
     * Configuration for the User Service HTTP callback that AI Platform calls
     * after async KYC processing completes.
     */
    @Getter @Setter
    public static class UserService {
        /** Base URL of the User Service — direct (bypasses API Gateway). */
        private String baseUrl = "http://user-service:8081";
        /**
         * Shared secret for service-to-service auth.
         * User Service validates this in InternalApiKeyFilter.
         * Override via USER_SERVICE_INTERNAL_API_KEY env var in production.
         */
        private String internalApiKey = "aegispay-internal-dev-key";
    }
}
