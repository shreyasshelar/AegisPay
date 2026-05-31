package com.aegispay.reconciliation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of the ledger_entries table in ledger-service's PostgreSQL DB.
 * Reconciliation connects to the ledger DB in read-only mode.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "entry_type", nullable = false)
    private String entryType;   // DEBIT | CREDIT | RESERVE | RELEASE | COMMIT

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
