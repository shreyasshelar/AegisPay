package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class PaymentProcessedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID sagaId;
    private final boolean success;
    private final String externalReference;
    private final String failureCode;
    private final String failureMessage;
}
