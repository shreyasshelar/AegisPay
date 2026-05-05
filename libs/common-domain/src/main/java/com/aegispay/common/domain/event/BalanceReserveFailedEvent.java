package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@SuperBuilder
public class BalanceReserveFailedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID sagaId;
    private final UUID accountId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;
    private final String failureReason;
}
