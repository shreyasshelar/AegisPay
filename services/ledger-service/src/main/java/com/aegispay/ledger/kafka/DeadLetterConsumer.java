package com.aegispay.ledger.kafka;

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
 * Consumes all DLQ topics owned by ledger-service.
 *
 * Messages arrive here after the primary balance consumer exhausts its
 * 3-attempt exponential-backoff retry window (KafkaConsumerRetryConfig).
 *
 * Each record is logged + persisted to dead_letter_events (Flyway V5)
 * with status=PENDING_REVIEW so ops can replay once the root cause is fixed.
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
            KafkaTopics.BALANCE_RESERVE_REQUESTED_DLQ,
            KafkaTopics.BALANCE_COMMIT_REQUESTED_DLQ,
            KafkaTopics.BALANCE_ROLLBACK_REQUESTED_DLQ,
            KafkaTopics.USER_REGISTERED_DLQ
        },
        groupId = "ledger-service-dlq"
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
