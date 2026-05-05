package com.aegispay.ledger.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aegispay.ledger")
@Getter
@Setter
public class LedgerServiceProperties {

    private int balanceLockExpiryMinutes = 30;
    private int optimisticLockMaxRetries = 3;
    private int outboxPollIntervalMs = 500;
    private int outboxBatchSize = 50;
}
