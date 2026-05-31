package com.aegispay.ledger.exception;

import java.math.BigDecimal;

/**
 * Thrown when a top-up would push the account balance above the configured
 * per-account maximum ({@code aegispay.ledger.topup.max-balance}).
 *
 * <p>Mapped to HTTP 422 Unprocessable Entity with code {@code BALANCE_LIMIT_EXCEEDED}.
 */
public class BalanceLimitExceededException extends RuntimeException {

    public BalanceLimitExceededException(BigDecimal current, BigDecimal requested,
                                          BigDecimal limit, String currency) {
        super(String.format(
                "Top-up of %s %s would exceed the maximum wallet balance of %s %s "
                + "(current balance: %s %s). Please top up a smaller amount.",
                requested.toPlainString(), currency,
                limit.toPlainString(), currency,
                current.toPlainString(), currency));
    }
}
