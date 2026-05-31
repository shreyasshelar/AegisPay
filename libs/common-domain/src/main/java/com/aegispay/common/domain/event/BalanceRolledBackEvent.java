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
public class BalanceRolledBackEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID accountId;
    private BigDecimal releasedAmount;
    private BigDecimal availableBalanceAfter;
}
