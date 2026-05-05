package com.aegispay.risk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "aegispay.risk")
@Getter
@Setter
public class RiskProperties {

    private int scoreThresholdApprove = 30;
    private int scoreThresholdReject = 70;
    private Velocity velocity = new Velocity();
    private AmountThreshold amountThreshold = new AmountThreshold();
    private Blacklist blacklist = new Blacklist();

    @Getter @Setter
    public static class Velocity {
        private int windowMinutes = 5;
        private int maxTransactions = 10;
    }

    @Getter @Setter
    public static class AmountThreshold {
        private BigDecimal unverifiedKycLimit = new BigDecimal("10000");
    }

    @Getter @Setter
    public static class Blacklist {
        private String redisKeyPrefix = "aegispay:blacklist:";
    }
}
