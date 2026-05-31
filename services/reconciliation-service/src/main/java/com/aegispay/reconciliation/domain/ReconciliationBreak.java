package com.aegispay.reconciliation.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a single discrepancy between AegisPay's ledger and Stripe's settlement data.
 * Written to ClickHouse reconciliation_breaks table.
 */
@Getter
@Builder
public class ReconciliationBreak {

    private final UUID breakId;
    private final LocalDate reportDate;

    /** AegisPay transaction ID — null if transaction exists only in Stripe */
    private final UUID transactionId;

    /** Stripe PaymentIntent ID — null if transaction exists only in ledger */
    private final String stripePiId;

    private final BigDecimal ledgerAmount;
    private final BigDecimal stripeAmount;

    /** Absolute difference |ledger - stripe| */
    private final BigDecimal breakAmount;

    private final String currency;

    /**
     * Classification of why this is a break:
     * - MISSING_IN_STRIPE  : Ledger shows COMMITTED, but no matching Stripe PI found
     * - MISSING_IN_LEDGER  : Stripe shows succeeded, but no matching ledger entry
     * - AMOUNT_MISMATCH    : Both sides found but amounts differ beyond tolerance
     * - STATUS_MISMATCH    : Ledger says COMMITTED but Stripe says failed (or vice versa)
     */
    private final String breakType;

    private final String ledgerStatus;
    private final String stripeStatus;

    @Builder.Default
    private final String breakStatus = "OPEN";
}
