package com.aegispay.transaction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aegispay")
public class TransactionServiceProperties {

    private Outbox outbox = new Outbox();
    private Idempotency idempotency = new Idempotency();

    @Data
    public static class Outbox {
        private int batchSize = 100;
        private long pollIntervalMs = 500;
    }

    @Data
    public static class Idempotency {
        private long ttlSeconds = 86400;
    }
}
