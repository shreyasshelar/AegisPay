package com.aegispay.pipeline.stream;

import com.aegispay.pipeline.sink.ClickHouseSink;
import com.aegispay.pipeline.sink.ClickHouseSink.TransactionFactRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Streams topology for transaction lifecycle events.
 *
 * <p>Consumes:
 * <ul>
 *   <li>{@code transaction.completed} — {@code TransactionCompletedEvent} as JSON</li>
 *   <li>{@code transaction.failed}    — {@code TransactionFailedEvent} as JSON</li>
 *   <li>{@code transaction.rolled-back} — {@code TransactionRolledBackEvent} as JSON</li>
 * </ul>
 *
 * <p>For each event the topology:
 * <ol>
 *   <li>Deserialises the JSON payload into a {@link Map} (avoids classpath coupling
 *       to the {@code common-domain} event classes which may not yet be on the
 *       build path).</li>
 *   <li>Builds a {@link TransactionFactRecord} and enqueues it in
 *       {@link ClickHouseSink#writeTransactionFact}.</li>
 *   <li>Demonstrates a 1-minute tumbling-window count of COMPLETED vs FAILED
 *       events (logged; not written to ClickHouse).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionMetricsStream {

    private static final String TOPIC_COMPLETED = "transaction.completed";
    private static final String TOPIC_FAILED    = "transaction.failed";

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED    = "FAILED";

    private final StreamsBuilder  streamsBuilder;
    private final ClickHouseSink  clickHouseSink;

    @Value("${aegispay.pipeline.window-size-minutes:1}")
    private int windowSizeMinutes;

    private final ObjectMapper objectMapper = buildObjectMapper();

    @PostConstruct
    public void buildTopology() {
        Consumed<String, String> stringConsumed =
                Consumed.with(Serdes.String(), Serdes.String());

        // ── 1. transaction.completed ──────────────────────────────────────────
        KStream<String, String> completedStream =
                streamsBuilder.stream(TOPIC_COMPLETED, stringConsumed);

        completedStream.foreach((key, value) -> {
            try {
                Map<String, Object> payload = parseJson(value);
                if (payload == null) return;

                UUID transactionId = uuidFrom(payload, "transactionId");
                UUID userId        = uuidFrom(payload, "userId");
                BigDecimal amount  = decimalFrom(payload, "amount");
                String currency    = stringFrom(payload, "currency", "UNKNOWN");
                Instant completedAt = instantFrom(payload, "completedAt");

                clickHouseSink.writeTransactionFact(new TransactionFactRecord(
                        transactionId, userId, amount, currency,
                        STATUS_COMPLETED, null, completedAt, 0L));

                // Saga latency: processingLatencyMs not in event yet → store 0 until
                // orchestrator adds saga timing to TransactionCompletedEvent
                clickHouseSink.writeSagaLatency(new ClickHouseSink.SagaLatencyRecord(
                        transactionId,
                        null,         // sagaId not in event → null → nil UUID in flush
                        null,         // startedAt not in event → null → completedAt used
                        completedAt,
                        0L,
                        STATUS_COMPLETED));

            } catch (Exception ex) {
                log.error("Error processing transaction.completed message key={}: {}", key, ex.getMessage(), ex);
            }
        });

        // ── 2. transaction.failed ─────────────────────────────────────────────
        KStream<String, String> failedStream =
                streamsBuilder.stream(TOPIC_FAILED, stringConsumed);

        failedStream.foreach((key, value) -> {
            try {
                Map<String, Object> payload = parseJson(value);
                if (payload == null) return;

                UUID transactionId = uuidFrom(payload, "transactionId");
                UUID userId        = uuidFrom(payload, "userId");
                String failureCode = stringFrom(payload, "failureCode", "UNKNOWN");

                TransactionFactRecord record = new TransactionFactRecord(
                        transactionId,
                        userId,
                        BigDecimal.ZERO,    // amount not available in failed event
                        "UNKNOWN",
                        STATUS_FAILED,
                        failureCode,
                        Instant.now(),
                        0L
                );
                clickHouseSink.writeTransactionFact(record);

            } catch (Exception ex) {
                log.error("Error processing transaction.failed message key={}: {}", key, ex.getMessage(), ex);
            }
        });

        // ── 3. Tumbling-window count: COMPLETED vs FAILED ─────────────────────
        // Note: transaction.rolled-back stream removed — orchestrator now maps all
        // terminal failures to transaction.failed (single terminal failure event).
        // Merge completed and failed streams, re-key by status, then count
        // within 1-minute tumbling windows. The result is logged for
        // observability; in a future iteration this can be written to ClickHouse
        // as a pre-aggregated summary table.
        Duration windowDuration = Duration.ofMinutes(windowSizeMinutes);

        KStream<String, String> statusKeyedStream = completedStream
                .merge(failedStream)
                .selectKey((key, value) -> {
                    try {
                        Map<String, Object> payload = parseJson(value);
                        if (payload == null) return "UNKNOWN";
                        // Heuristic: presence of "failureCode" → FAILED, else COMPLETED
                        return payload.containsKey("failureCode") ? STATUS_FAILED : STATUS_COMPLETED;
                    } catch (Exception ex) {
                        return "UNKNOWN";
                    }
                });

        statusKeyedStream
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(windowDuration))
                .count(Materialized.as("transaction-status-counts"))
                .toStream()
                .foreach((windowedKey, count) ->
                        log.info("[Window {}–{}] status={} count={}",
                                windowedKey.window().startTime(),
                                windowedKey.window().endTime(),
                                windowedKey.key(),
                                count));

        log.info("TransactionMetricsStream topology built: topics=[{}, {}], windowSize={}m",
                TOPIC_COMPLETED, TOPIC_FAILED, windowSizeMinutes);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Received null/blank Kafka message — skipping");
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            log.error("Failed to parse Kafka message as JSON: {} — value={}", ex.getMessage(), json);
            return null;
        }
    }

    private UUID uuidFrom(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return UUID.randomUUID(); // fallback; should not happen in prod
        return val instanceof UUID u ? u : UUID.fromString(val.toString());
    }

    private BigDecimal decimalFrom(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return BigDecimal.ZERO;
        return val instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue())
                : new BigDecimal(val.toString());
    }

    private String stringFrom(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private Instant instantFrom(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return Instant.now();
        try {
            // Jackson with JavaTimeModule deserialises Instant as a string ISO-8601
            return val instanceof String s ? Instant.parse(s) : Instant.now();
        } catch (Exception ex) {
            log.warn("Could not parse Instant for key={}, value={}", key, val);
            return Instant.now();
        }
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
