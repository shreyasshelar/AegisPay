package com.aegispay.common.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotFoundException extends AegisPayException {

    public TransactionNotFoundException(UUID transactionId) {
        super("TXN_NOT_FOUND",
              "Transaction not found: " + transactionId,
              HttpStatus.NOT_FOUND);
    }
}
