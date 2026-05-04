package com.aegispay.risk.exception;

import java.util.UUID;

public class RiskCaseNotFoundException extends RuntimeException {
    public RiskCaseNotFoundException(UUID transactionId) {
        super("Risk case not found for transactionId: " + transactionId);
    }
}
