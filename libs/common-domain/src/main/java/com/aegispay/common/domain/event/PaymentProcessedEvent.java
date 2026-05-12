package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class PaymentProcessedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private boolean success;
    private String externalReference;
    private String failureCode;
    private String failureMessage;
}
