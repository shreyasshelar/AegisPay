package com.aegispay.reconciliation.batch;

import com.aegispay.reconciliation.domain.LedgerEntry;
import com.aegispay.reconciliation.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Spring-managed component that wraps {@link LedgerEntryRepository} for batch use.
 *
 * <p>Extracted from {@link ReconciliationJobConfig} so the {@code @StepScope} reader bean
 * can call this without a circular proxy issue (the reader bean is request-scoped;
 * this component is a singleton that holds the repository).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEntryBatchReader {

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Returns all COMMIT-type ledger entries whose {@code createdAt} falls within
     * the half-open window {@code [from, to)}.
     *
     * @param from start of the window (inclusive), UTC midnight of report date
     * @param to   end of the window (exclusive), UTC midnight of next day
     * @return ordered list of committed ledger entries
     */
    public List<LedgerEntry> findCommittedEntries(Instant from, Instant to) {
        log.debug("Loading COMMIT ledger entries from={} to={}", from, to);
        return ledgerEntryRepository.findCommittedEntriesForPeriod(from, to);
    }
}
