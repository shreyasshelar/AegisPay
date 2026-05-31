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
public class BalanceReserveRequestedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
}
