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
public class TransactionInitiatedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID userId;
    private UUID payerId;
    private UUID payeeId;
    private BigDecimal amount;
    private String currency;
    private String idempotencyKey;
    private String metadata;
}
