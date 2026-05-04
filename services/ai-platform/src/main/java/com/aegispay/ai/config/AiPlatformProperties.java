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
}
