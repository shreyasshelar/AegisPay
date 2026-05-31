package com.aegispay.orchestrator.kafka;

import com.aegispay.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Consumes all DLQ topics owned by payment-orchestrator.
 *
 * A message in these DLQ topics means a saga step could not be processed
 * even after retries — the saga is effectively stuck.  The record is persisted
 * to dead_letter_events (Flyway V3) and the DlqDepthNonZero alert fires.
 *
 * Ops steps:
 *  1. Identify the stuck sagaId from the payload
 *  2. Determine the correct compensating action (manual balance rollback, etc.)
 *  3. Mark the DLQ row as REPLAYED or DISCARDED after handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final JdbcTemplate jdbc;

    private static final String INSERT_SQL = """
            INSERT INTO dead_letter_events
                (original_topic, message_key, payload, failure_reason, failure_timestamp, status)
            VALUES (?, ?, ?, ?, ?, 'PENDING_REVIEW')
            """;

    @KafkaListener(
        topics = {
            // Inbound saga triggers
            KafkaTopics.TRANSACTION_INITIATED_DLQ,
            KafkaTopics.PAYMENT_PROCESS_REQUESTED_DLQ,
            // Saga reply topics
            KafkaTopics.BALANCE_RESERVED_DLQ,
            KafkaTopics.BALANCE_RESERVE_FAILED_DLQ,
            KafkaTopics.RISK_ASSESSED_DLQ,
            KafkaTopics.PAYMENT_PROCESSED_DLQ,
            KafkaTopics.BALANCE_COMMITTED_DLQ,
            KafkaTopics.BALANCE_ROLLED_BACK_DLQ
        },
        groupId = "payment-orchestrator-dlq"
    )
    public void handle(ConsumerRecord<String, String> record) {
        String failureReason    = headerValue(record, "dlq-failure-reason");
        String failureTimestamp = headerValue(record, "dlq-failure-timestamp");
        Instant failedAt = failureTimestamp != null ? Instant.parse(failureTimestamp) : Instant.now();

        log.warn("DLQ message received: topic={} key={} reason={}",
                 record.topic(), record.key(), failureReason);

        try {
            jdbc.update(INSERT_SQL,
                    record.topic(),
                    record.key(),
                    record.value(),
                    failureReason,
                    java.sql.Timestamp.from(failedAt));
        } catch (Exception e) {
            log.error("Failed to persist DLQ event to DB: topic={} key={} error={}",
                      record.topic(), record.key(), e.getMessage(), e);
        }
    }

    private String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h != null ? new String(h.value(), StandardCharsets.UTF_8) : "unknown";
    }
}
