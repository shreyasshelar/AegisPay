package com.aegispay.orchestrator.exception;

import java.util.UUID;

public class SagaNotFoundException extends RuntimeException {
    public SagaNotFoundException(UUID transactionId) {
        super("No saga found for transactionId: " + transactionId);
    }
}
