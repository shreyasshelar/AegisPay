package com.aegispay.user.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEntryRepository extends JpaRepository<OutboxEntry, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEntry e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEntry> findPendingForUpdate(Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEntry e SET e.status = 'PUBLISHED', e.publishedAt = :now WHERE e.id = :id")
    void markPublished(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE OutboxEntry e SET e.status = 'FAILED', e.errorMessage = :error, e.attemptCount = e.attemptCount + 1 WHERE e.id = :id")
    void markFailed(@Param("id") UUID id, @Param("error") String error);
}
