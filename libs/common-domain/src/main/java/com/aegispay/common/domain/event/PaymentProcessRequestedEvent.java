package com.aegispay.common.domain.event;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@SuperBuilder
public class PaymentProcessRequestedEvent extends BaseEvent {

    private final UUID transactionId;
    private final UUID sagaId;
    private final UUID payerId;
    private final UUID payeeId;
    private final BigDecimal amount;
    private final String currency;
    private final String externalGatewayRef;
}
