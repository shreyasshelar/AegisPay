package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class TransactionRolledBackEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID userId;
    private final String rollbackReason;
    private final String sagaId;
}
