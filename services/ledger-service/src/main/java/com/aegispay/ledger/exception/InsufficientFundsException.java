package com.aegispay.ledger.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal available, BigDecimal requested) {
        super(String.format("Insufficient funds for account %s: available=%s requested=%s",
                accountId, available, requested));
    }
}
