package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class BalanceReservedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID accountId;
    private BigDecimal reservedAmount;
    private BigDecimal availableBalanceAfter;
}
