package com.aegispay.ledger.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }

    /** Use when the context message is richer than a bare UUID (e.g. commit-balance DLQ path). */
    public AccountNotFoundException(String message) {
        super(message);
    }
}
