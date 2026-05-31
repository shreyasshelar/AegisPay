package com.aegispay.ledger.outbox;

import com.aegispay.ledger.domain.entity.BalanceLock;
import com.aegispay.ledger.repository.BalanceLockRepository;
import com.aegispay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredLockCleanupScheduler {

    private final BalanceLockRepository balanceLockRepository;
    private final LedgerService ledgerService;

    @Scheduled(fixedDelayString = "${aegispay.ledger.lock-cleanup-interval-ms:60000}")
    public void cleanupExpiredLocks() {
        List<BalanceLock> expired = balanceLockRepository.findExpiredLocks(Instant.now());
        if (expired.isEmpty()) return;

        log.warn("Found {} expired balance lock(s) to release", expired.size());
        for (BalanceLock lock : expired) {
            try {
                ledgerService.releaseExpiredLock(lock);
            } catch (Exception e) {
                log.error("Failed to release expired lock for txn={}: {}",
                        lock.getTransactionId(), e.getMessage(), e);
            }
        }
    }
}
