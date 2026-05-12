package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class TransactionRolledBackEvent extends BaseEvent {

    private UUID transactionId;
    private UUID userId;
    private String rollbackReason;
    private String sagaId;
}
