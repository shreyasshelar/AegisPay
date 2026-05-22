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
public class BalanceCommitRequestedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private UUID accountId;   // sender's account ID (to debit reserved balance)
    private UUID payeeId;     // receiver's user ID  (to credit available balance)
    private BigDecimal amount;
}
