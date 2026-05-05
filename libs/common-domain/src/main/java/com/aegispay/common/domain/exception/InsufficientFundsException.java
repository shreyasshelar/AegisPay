package com.aegispay.common.domain.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends AegisPayException {

    public InsufficientFundsException(UUID accountId, BigDecimal requested, BigDecimal available) {
        super("INSUFFICIENT_FUNDS",
              String.format("Insufficient funds for account %s: requested %s, available %s",
                            accountId, requested, available),
              HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
