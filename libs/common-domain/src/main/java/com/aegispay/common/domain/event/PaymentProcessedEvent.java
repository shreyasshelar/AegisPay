package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class PaymentProcessedEvent extends BaseEvent {

    private UUID transactionId;
    private UUID sagaId;
    private boolean success;
    private String externalReference;
    private String failureCode;
    private String failureMessage;
}
