package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
public class TransactionCompletedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID userId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant completedAt;
    private final String externalReference;
}
