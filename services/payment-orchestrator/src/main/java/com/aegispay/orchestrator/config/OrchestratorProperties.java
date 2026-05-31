package com.aegispay.orchestrator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aegispay.saga")
@Getter
@Setter
public class OrchestratorProperties {
    private int timeoutMinutes = 10;
    private long timeoutCheckIntervalMs = 60_000;
}
