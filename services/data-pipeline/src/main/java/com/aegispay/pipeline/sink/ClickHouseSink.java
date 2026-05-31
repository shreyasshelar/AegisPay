package com.aegispay.pipeline.sink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClickHouse write sink for the data-pipeline service.
 *
 * <p>Incoming records are placed into in-memory {@link ConcurrentLinkedQueue}
 * buffers by the Kafka Streams threads, then flushed to ClickHouse on a fixed
 * schedule (default every 5 seconds). This approach:
 * <ul>
 *   <li>Decouples stream processing latency from network I/O to ClickHouse</li>
 *   <li>Enables natural micro-batching, which is optimal for ClickHouse's
 *       columnar, merge-tree engine</li>
 *   <li>Keeps Kafka Streams threads non-blocking</li>
 * </ul>
 *
 * <h3>ClickHouse table schemas expected</h3>
 * <pre>
 * CREATE TABLE IF NOT EXISTS aegispay_analytics.transaction_facts
 * (
 *     transaction_id   UUID,
 *     user_id          UUID,
 *     amount           Decimal(18, 4),
 *     currency         LowCardinality(String),
 *     status           LowCardinality(String),
 *     failure_code     Nullable(String),
 *     event_time       DateTime64(3, 'UTC'),
 *     processing_latency_ms Int64
 * )
 * ENGINE = MergeTree()
 * PARTITION BY toYYYYMM(event_time)
 * ORDER BY (event_time, transaction_id);
 *
 * CREATE TABLE IF NOT EXISTS aegispay_analytics.risk_assessments
 * (
 *     transaction_id UUID,
 *     user_id        UUID,
 *     risk_score     Int32,
 *     decision       LowCardinality(String),
 *     rule_flags     Array(String),
 *     event_time     DateTime64(3, 'UTC')
 * )
 * ENGINE = MergeTree()
 * PARTITION BY toYYYYMM(event_time)
 * ORDER BY (event_time, transaction_id);
 *
 * CREATE TABLE IF NOT EXISTS aegispay_analytics.saga_latencies
 * (
 *     transaction_id UUID,
 *     latency_ms     Int64,
 *     final_status   LowCardinality(String),
 *     completed_at   DateTime64(3, 'UTC')
 * )
 * ENGINE = MergeTree()
 * PARTITION BY toYYYYMM(completed_at)
 * ORDER BY (completed_at, transaction_id);
 * </pre>
 */
@Slf4j
@Component
public class ClickHouseSink {

    // ── Record types ──────────────────────────────────────────────────────────

    public record TransactionFactRecord(
            UUID transactionId,
            UUID userId,
            BigDecimal amount,
            String currency,
            String status,
            String failureCode,
            Instant eventTime,
            long processingLatencyMs
    ) {}

    public record RiskAssessmentRecord(
            UUID transactionId,
            UUID userId,
            int riskScore,
            String decision,
            List<String> ruleFlags,
            Instant eventTime
    ) {}

    public record SagaLatencyRecord(
            UUID transactionId,
            UUID sagaId,          // may be null when saga ID is unavailable
            Instant startedAt,    // may be null when start time is unavailable
            Instant completedAt,
            long latencyMs,
            String finalStatus
    ) {}

    // ── SQL constants ─────────────────────────────────────────────────────────

    private static final String INSERT_TRANSACTION_FACT =
            "INSERT INTO transaction_facts " +
            "(transaction_id, user_id, amount, currency, status, failure_code, event_time, processing_latency_ms) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    // rule_flags is Array(String) — ClickHouse JDBC requires the value as String[]
    private static final String INSERT_RISK_ASSESSMENT =
            "INSERT INTO risk_assessments " +
            "(transaction_id, user_id, risk_score, decision, rule_flags, event_time) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    // saga_latencies table: saga_id/started_at filled with neutral defaults when unavailable
    private static final String INSERT_SAGA_LATENCY =
            "INSERT INTO saga_latencies " +
            "(transaction_id, saga_id, started_at, completed_at, latency_ms, final_status) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    // ── State ─────────────────────────────────────────────────────────────────

    private final JdbcTemplate clickHouseJdbcTemplate;

    private final ConcurrentLinkedQueue<TransactionFactRecord> transactionFactBuffer  = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RiskAssessmentRecord>  riskAssessmentBuffer   = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SagaLatencyRecord>     sagaLatencyBuffer      = new ConcurrentLinkedQueue<>();

    /** Monotonically increasing counter for observability. */
    private final AtomicLong totalFlushed = new AtomicLong(0);

    @Value("${aegispay.pipeline.flush-interval-ms:5000}")
    private long flushIntervalMs;

    public ClickHouseSink(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
    }

    // ── Public write API (called from Kafka Streams threads) ──────────────────

    /**
     * Enqueues a {@link TransactionFactRecord} for the next scheduled flush.
     * This method is non-blocking and thread-safe.
     */
    public void writeTransactionFact(TransactionFactRecord record) {
        if (record == null) {
            return;
        }
        transactionFactBuffer.offer(record);
        log.debug("Buffered TransactionFactRecord: transactionId={}, status={}", record.transactionId(), record.status());
    }

    /**
     * Enqueues a {@link RiskAssessmentRecord} for the next scheduled flush.
     */
    public void writeRiskAssessment(RiskAssessmentRecord record) {
        if (record == null) {
            return;
        }
        riskAssessmentBuffer.offer(record);
        log.debug("Buffered RiskAssessmentRecord: transactionId={}, decision={}", record.transactionId(), record.decision());
    }

    /**
     * Enqueues a {@link SagaLatencyRecord} for the next scheduled flush.
     */
    public void writeSagaLatency(SagaLatencyRecord record) {
        if (record == null) {
            return;
        }
        sagaLatencyBuffer.offer(record);
        log.debug("Buffered SagaLatencyRecord: transactionId={}, latencyMs={}", record.transactionId(), record.latencyMs());
    }

    // ── Scheduled flush ───────────────────────────────────────────────────────

    /**
     * Drains all three buffers and batch-inserts their contents into ClickHouse.
     * Runs every {@code aegispay.pipeline.flush-interval-ms} milliseconds.
     * Each flush is isolated so a failure in one table does not block the others.
     */
    @Scheduled(fixedDelayString = "${aegispay.pipeline.flush-interval-ms:5000}")
    public void flush() {
        flushTransactionFacts();
        flushRiskAssessments();
        flushSagaLatencies();
    }

    // ── Private flush helpers ─────────────────────────────────────────────────

    private void flushTransactionFacts() {
        List<TransactionFactRecord> batch = drain(transactionFactBuffer);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Flushing {} transaction_facts to ClickHouse", batch.size());
        try {
            List<Object[]> batchArgs = batch.stream()
                    .map(r -> new Object[]{
                            r.transactionId().toString(),
                            r.userId().toString(),
                            r.amount(),
                            r.currency(),
                            r.status(),
                            r.failureCode(),
                            r.eventTime().toEpochMilli(),
                            r.processingLatencyMs()
                    })
                    .toList();
            clickHouseJdbcTemplate.batchUpdate(INSERT_TRANSACTION_FACT, batchArgs);
            totalFlushed.addAndGet(batch.size());
            log.info("Flushed {} transaction_facts to ClickHouse (total={}).", batch.size(), totalFlushed.get());
        } catch (Exception ex) {
            log.error("Failed to flush transaction_facts to ClickHouse — {} records dropped. Cause: {}",
                    batch.size(), ex.getMessage(), ex);
            // Records are intentionally dropped rather than re-buffered to avoid
            // unbounded memory growth. The Kafka consumer offset has not yet been
            // committed, so at-least-once delivery will replay these events.
        }
    }

    private void flushRiskAssessments() {
        List<RiskAssessmentRecord> batch = drain(riskAssessmentBuffer);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Flushing {} risk_assessments to ClickHouse", batch.size());
        try {
            List<Object[]> batchArgs = batch.stream()
                    .map(r -> new Object[]{
                            r.transactionId().toString(),
                            r.userId().toString(),
                            r.riskScore(),
                            r.decision(),
                            // ClickHouse JDBC requires String[] for Array(String) columns
                            r.ruleFlags().toArray(new String[0]),
                            r.eventTime().toEpochMilli()
                    })
                    .toList();
            clickHouseJdbcTemplate.batchUpdate(INSERT_RISK_ASSESSMENT, batchArgs);
            log.info("Flushed {} risk_assessments to ClickHouse.", batch.size());
        } catch (Exception ex) {
            log.error("Failed to flush risk_assessments to ClickHouse — {} records dropped. Cause: {}",
                    batch.size(), ex.getMessage(), ex);
        }
    }

    private void flushSagaLatencies() {
        List<SagaLatencyRecord> batch = drain(sagaLatencyBuffer);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Flushing {} saga_latencies to ClickHouse", batch.size());
        try {
            // saga_id / started_at may be null when not available — use sentinel values
            UUID nilUuid = new UUID(0, 0);
            List<Object[]> batchArgs = batch.stream()
                    .map(r -> new Object[]{
                            r.transactionId().toString(),
                            (r.sagaId() != null ? r.sagaId() : nilUuid).toString(),
                            (r.startedAt() != null ? r.startedAt() : r.completedAt()).toEpochMilli(),
                            r.completedAt().toEpochMilli(),
                            r.latencyMs(),
                            r.finalStatus()
                    })
                    .toList();
            clickHouseJdbcTemplate.batchUpdate(INSERT_SAGA_LATENCY, batchArgs);
            log.info("Flushed {} saga_latencies to ClickHouse.", batch.size());
        } catch (Exception ex) {
            log.error("Failed to flush saga_latencies to ClickHouse — {} records dropped. Cause: {}",
                    batch.size(), ex.getMessage(), ex);
        }
    }

    /** Atomically drains the queue into a list for batch processing. */
    private <T> List<T> drain(ConcurrentLinkedQueue<T> queue) {
        List<T> batch = new ArrayList<>();
        T item;
        while ((item = queue.poll()) != null) {
            batch.add(item);
        }
        return batch;
    }

    /** Exposed for health-check / metrics. */
    public long getTotalFlushed() {
        return totalFlushed.get();
    }
}
