package com.aegispay.reconciliation.batch;

import com.aegispay.reconciliation.domain.LedgerEntry;
import com.aegispay.reconciliation.domain.ReconciliationBreak;
import com.aegispay.reconciliation.stripe.StripeSettlementFetcher;
import com.aegispay.reconciliation.stripe.StripeSettlementFetcher.StripeSettlementEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Spring Batch job: reconcile AegisPay ledger vs Stripe settlements.
 *
 * <p>Flow:
 * <ol>
 *   <li>Read all COMMIT ledger entries for report_date from PostgreSQL
 *   <li>Fetch all Stripe balance transactions for report_date
 *   <li>For each ledger entry: find matching Stripe PI → compare amount
 *   <li>Write breaks to ClickHouse reconciliation_breaks table
 *   <li>Also detect Stripe transactions with no matching ledger entry
 * </ol>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReconciliationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StripeSettlementFetcher stripeFetcher;

    @Value("${aegispay.reconciliation.chunk-size:500}")
    private int chunkSize;

    @Value("${aegispay.reconciliation.tolerance-minor-units:1}")
    private long toleranceMinorUnits;

    // ── Job ────────────────────────────────────────────────────────────────────

    @Bean
    public Job reconciliationJob(Step reconciliationStep, Step stripeOnlyBreakStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(reconciliationStep)
                .next(stripeOnlyBreakStep)
                .build();
    }

    // ── Step 1: Ledger → Stripe comparison ────────────────────────────────────

    @Bean
    public Step reconciliationStep(
            @Qualifier("ledgerReader") ListItemReader<LedgerEntry> reader,
            ItemProcessor<LedgerEntry, Optional<ReconciliationBreak>> processor,
            ItemWriter<Optional<ReconciliationBreak>> writer) {
        return new StepBuilder("reconcileStep", jobRepository)
                .<LedgerEntry, Optional<ReconciliationBreak>>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        String date = stepExecution.getJobParameters()
                                .getString("reportDate", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString());
                        log.info("Starting reconciliation step for date={}", date);
                    }
                })
                .build();
    }

    // ── Step 2: Find Stripe transactions missing from ledger ──────────────────

    @Bean
    public Step stripeOnlyBreakStep(
            @Qualifier("stripeOnlyReader") ListItemReader<StripeSettlementEntry> stripeReader,
            ItemProcessor<StripeSettlementEntry, Optional<ReconciliationBreak>> stripeProcessor,
            ItemWriter<Optional<ReconciliationBreak>> writer) {
        return new StepBuilder("stripeOnlyBreakStep", jobRepository)
                .<StripeSettlementEntry, Optional<ReconciliationBreak>>chunk(chunkSize, transactionManager)
                .reader(stripeReader)
                .processor(stripeProcessor)
                .writer(writer)
                .build();
    }

    // ── Reader: COMMIT ledger entries for report date ─────────────────────────

    @Bean("ledgerReader")
    @StepScope
    public ListItemReader<LedgerEntry> ledgerReader(
            LedgerEntryBatchReader batchReader,
            @Value("#{jobParameters['reportDate']}") String reportDateStr) {
        LocalDate date = reportDateStr != null
                ? LocalDate.parse(reportDateStr)
                : LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<LedgerEntry> entries = batchReader.findCommittedEntries(from, to);
        log.info("Loaded {} COMMIT ledger entries for date={}", entries.size(), date);
        return new ListItemReader<>(entries);
    }

    // ── Reader: Stripe-side entries (for MISSING_IN_LEDGER detection) ─────────

    @Bean("stripeOnlyReader")
    @StepScope
    public ListItemReader<StripeSettlementEntry> stripeOnlyReader(
            @Value("#{jobParameters['reportDate']}") String reportDateStr,
            @Value("#{jobExecutionContext['processedStripeIds']}") Set<String> processedIds) {
        LocalDate date = reportDateStr != null
                ? LocalDate.parse(reportDateStr)
                : LocalDate.now(ZoneOffset.UTC).minusDays(1);
        List<StripeSettlementEntry> allStripe = stripeFetcher.fetchForDate(date);

        // Filter to entries NOT matched in step 1
        Set<String> matched = processedIds != null ? processedIds : Collections.emptySet();
        List<StripeSettlementEntry> unmatched = allStripe.stream()
                .filter(e -> !matched.contains(e.paymentIntentId()))
                .toList();
        log.info("Stripe-only breaks to check: {}/{} entries unmatched", unmatched.size(), allStripe.size());
        return new ListItemReader<>(unmatched);
    }

    // ── Processor: Ledger entry → break detection ─────────────────────────────

    @Bean
    @StepScope
    public ItemProcessor<LedgerEntry, Optional<ReconciliationBreak>> ledgerEntryProcessor(
            StripeSettlementFetcher fetcher,
            @Value("#{jobParameters['reportDate']}") String reportDateStr) {
        // Fetch Stripe data once, cache in processor
        LocalDate date = reportDateStr != null
                ? LocalDate.parse(reportDateStr)
                : LocalDate.now(ZoneOffset.UTC).minusDays(1);
        List<StripeSettlementEntry> stripeEntries = fetcher.fetchForDate(date);
        Map<String, StripeSettlementEntry> stripeByPiId = new HashMap<>();
        for (StripeSettlementEntry e : stripeEntries) {
            stripeByPiId.put(e.paymentIntentId(), e);
        }

        return ledgerEntry -> {
            // We need the Stripe PI ID — stored in the saga's externalReference
            // For now we match by transactionId pattern in metadata
            // In production: join via a view or use the saga table to get externalReference
            String txIdStr = ledgerEntry.getTransactionId().toString();

            // Find matching Stripe entry where metadata.transaction_id == txIdStr
            Optional<StripeSettlementEntry> stripeMatch = stripeByPiId.values().stream()
                    .filter(se -> se.paymentIntentId() != null &&
                            se.paymentIntentId().contains(txIdStr.replace("-", "").substring(0, 8)))
                    .findFirst();

            if (stripeMatch.isEmpty()) {
                // Ledger has COMMIT but Stripe has no matching PI → MISSING_IN_STRIPE
                return Optional.of(ReconciliationBreak.builder()
                        .breakId(UUID.randomUUID())
                        .reportDate(date)
                        .transactionId(ledgerEntry.getTransactionId())
                        .stripePiId(null)
                        .ledgerAmount(ledgerEntry.getAmount())
                        .stripeAmount(null)
                        .breakAmount(ledgerEntry.getAmount())
                        .currency("INR")  // default; improve by joining with transaction table
                        .breakType("MISSING_IN_STRIPE")
                        .ledgerStatus("COMMITTED")
                        .stripeStatus(null)
                        .build());
            }

            StripeSettlementEntry stripe = stripeMatch.get();
            BigDecimal diff = ledgerEntry.getAmount().subtract(stripe.amount()).abs();
            // Tolerance: 1 minor unit = 0.01 INR / 0.01 USD
            boolean withinTolerance = diff.movePointRight(2).longValue() <= toleranceMinorUnits;

            if (withinTolerance) {
                return Optional.empty(); // MATCHED — no break
            }

            return Optional.of(ReconciliationBreak.builder()
                    .breakId(UUID.randomUUID())
                    .reportDate(date)
                    .transactionId(ledgerEntry.getTransactionId())
                    .stripePiId(stripe.paymentIntentId())
                    .ledgerAmount(ledgerEntry.getAmount())
                    .stripeAmount(stripe.amount())
                    .breakAmount(diff)
                    .currency(stripe.currency())
                    .breakType("AMOUNT_MISMATCH")
                    .ledgerStatus("COMMITTED")
                    .stripeStatus(stripe.status())
                    .build());
        };
    }

    // ── Processor: Stripe-only entry → MISSING_IN_LEDGER break ───────────────

    @Bean
    @StepScope
    public ItemProcessor<StripeSettlementEntry, Optional<ReconciliationBreak>> stripeEntryProcessor(
            @Value("#{jobParameters['reportDate']}") String reportDateStr) {
        LocalDate date = reportDateStr != null
                ? LocalDate.parse(reportDateStr)
                : LocalDate.now(ZoneOffset.UTC).minusDays(1);
        return entry -> Optional.of(ReconciliationBreak.builder()
                .breakId(UUID.randomUUID())
                .reportDate(date)
                .transactionId(null)
                .stripePiId(entry.paymentIntentId())
                .ledgerAmount(null)
                .stripeAmount(entry.amount())
                .breakAmount(entry.amount())
                .currency(entry.currency())
                .breakType("MISSING_IN_LEDGER")
                .ledgerStatus(null)
                .stripeStatus(entry.status())
                .build());
    }

    // ── Writer: write breaks to ClickHouse ────────────────────────────────────

    @Bean
    public ItemWriter<Optional<ReconciliationBreak>> breakWriter(
            @Qualifier("clickHouseJdbcTemplate") JdbcTemplate ch) {
        return items -> {
            List<Object[]> rows = new ArrayList<>();
            for (Optional<ReconciliationBreak> opt : items) {
                opt.ifPresent(b -> rows.add(new Object[]{
                        b.getBreakId().toString(),
                        b.getReportDate().toString(),
                        b.getTransactionId() != null ? b.getTransactionId().toString() : null,
                        b.getStripePiId(),
                        b.getLedgerAmount(),
                        b.getStripeAmount(),
                        b.getBreakAmount(),
                        b.getCurrency(),
                        b.getBreakType(),
                        b.getBreakStatus(),
                        b.getLedgerStatus(),
                        b.getStripeStatus()
                }));
            }
            if (!rows.isEmpty()) {
                ch.batchUpdate("""
                    INSERT INTO aegispay_analytics.reconciliation_breaks
                    (break_id, report_date, transaction_id, stripe_pi_id,
                     ledger_amount, stripe_amount, break_amount, currency,
                     break_type, break_status, ledger_status, stripe_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, rows);
                log.info("Wrote {} reconciliation breaks to ClickHouse", rows.size());
            }
        };
    }
}
