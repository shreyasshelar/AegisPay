package com.aegispay.common.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class RiskRejectedException extends AegisPayException {

    public RiskRejectedException(UUID transactionId, int riskScore, String reason) {
        super("RISK_REJECTED",
              String.format("Transaction %s rejected by risk engine (score: %d): %s",
                            transactionId, riskScore, reason),
              HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
