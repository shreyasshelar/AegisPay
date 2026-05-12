package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class RiskAssessmentRequestedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private String payeeCountry;
    private String deviceFingerprint;
    private String ipAddress;
}
