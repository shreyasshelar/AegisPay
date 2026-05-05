package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@SuperBuilder
public class TransactionInitiatedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID userId;
    private final UUID payerId;
    private final UUID payeeId;
    private final BigDecimal amount;
    private final String currency;
    private final String idempotencyKey;
    private final String metadata;
}
