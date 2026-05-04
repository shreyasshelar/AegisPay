package com.aegispay.orchestrator.repository;

import com.aegispay.orchestrator.domain.entity.Saga;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepository extends JpaRepository<Saga, UUID> {

    Optional<Saga> findByTransactionId(UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Saga s WHERE s.transactionId = :txnId")
    Optional<Saga> findByTransactionIdForUpdate(@Param("txnId") UUID transactionId);

    @Query("SELECT s FROM Saga s WHERE s.status IN ('RUNNING','COMPENSATING') AND s.timeoutAt < :now")
    List<Saga> findTimedOutSagas(@Param("now") Instant now);
}
