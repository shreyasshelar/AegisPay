package com.aegispay.reconciliation.repository;

import com.aegispay.reconciliation.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Fetch all COMMIT entries for a given day.
     * COMMIT = the final debit that confirmed a completed payment.
     * These are what we reconcile against Stripe settlements.
     */
    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.entryType = 'COMMIT'
          AND e.createdAt >= :from
          AND e.createdAt < :to
        ORDER BY e.createdAt
        """)
    List<LedgerEntry> findCommittedEntriesForPeriod(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * Find all ledger entries for a specific transaction.
     * Used to look up the full saga trail for a given transaction ID.
     */
    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
