package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@SuperBuilder
public class BalanceRollbackRequestedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID sagaId;
    private final UUID accountId;
    private final BigDecimal amountToRelease;
    private final String rollbackReason;
}
