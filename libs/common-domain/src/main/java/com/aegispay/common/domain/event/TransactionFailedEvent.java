package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class TransactionFailedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID userId;
    private final String failureReason;
    private final String failureCode;
}
