package com.aegispay.ledger.repository;

import com.aegispay.ledger.domain.entity.BalanceLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BalanceLockRepository extends JpaRepository<BalanceLock, UUID> {

    Optional<BalanceLock> findByTransactionId(UUID transactionId);

    @Query("SELECT bl FROM BalanceLock bl WHERE bl.expiresAt < :now")
    List<BalanceLock> findExpiredLocks(@Param("now") Instant now);
}
