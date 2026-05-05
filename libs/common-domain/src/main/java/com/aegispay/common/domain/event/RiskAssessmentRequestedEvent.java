package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@SuperBuilder
public class RiskAssessmentRequestedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID sagaId;
    private final UUID userId;
    private final BigDecimal amount;
    private final String currency;
    private final String payeeCountry;
    private final String deviceFingerprint;
    private final String ipAddress;
}
