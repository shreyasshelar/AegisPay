package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class RiskAssessmentRequestedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID userId;
    /** The recipient of the payment — used to detect self-transfers and repeated-payee failures. */
    private UUID payeeId;
    private BigDecimal amount;
    private String currency;
    private String payeeCountry;
    private String deviceFingerprint;
    private String ipAddress;
    /** ISO-8601 timestamp when the payer account was created — used for new-user risk scoring. */
    private java.time.Instant accountCreatedAt;
}
