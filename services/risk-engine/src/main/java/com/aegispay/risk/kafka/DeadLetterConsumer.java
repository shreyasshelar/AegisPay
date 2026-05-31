package com.aegispay.risk.kafka;

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
 * Consumes all DLQ topics owned by risk-engine.
 *
 * A risk.assessment.requested.DLQ message means a transaction's risk score
 * could not be computed — the saga is waiting for an APPROVED/REJECTED reply
 * that will never come, and will eventually time out via the saga watchdog.
 *
 * Each record is persisted to dead_letter_events (Flyway V4) for manual review.
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
            KafkaTopics.RISK_ASSESSMENT_REQUESTED_DLQ,
            KafkaTopics.KYC_STATUS_CHANGED_DLQ
        },
        groupId = "risk-engine-dlq"
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
