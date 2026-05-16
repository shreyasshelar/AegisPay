package com.aegispay.ledger.repository;

import com.aegispay.ledger.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
