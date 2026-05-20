package com.aegispay.notification.kafka;

import com.aegispay.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumes all DLQ topics owned by notification-service.
 *
 * notification-service is MongoDB-only (no PostgreSQL datasource), so
 * failed records are written to the 'dead_letter_events' MongoDB collection
 * instead of a PostgreSQL table.  The same PENDING_REVIEW / REPLAYED /
 * DISCARDED lifecycle applies.
 *
 * A message in these DLQs means a notification was never dispatched.
 * Ops should check the original payload, verify the notification channel
 * (email/SMS/push) is healthy, and replay if appropriate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final MongoTemplate mongoTemplate;

    private static final String COLLECTION = "dead_letter_events";

    @KafkaListener(
        topics = {
            KafkaTopics.TRANSACTION_COMPLETED_DLQ,
            KafkaTopics.TRANSACTION_FAILED_DLQ,
            KafkaTopics.TRANSACTION_ROLLED_BACK_DLQ,
            KafkaTopics.USER_REGISTERED_DLQ,
            KafkaTopics.KYC_STATUS_CHANGED_DLQ,
            KafkaTopics.NOTIFICATION_SEND_REQUESTED_DLQ
        },
        groupId = "notification-service-dlq"
    )
    public void handle(ConsumerRecord<String, String> record) {
        String failureReason    = headerValue(record, "dlq-failure-reason");
        String failureTimestamp = headerValue(record, "dlq-failure-timestamp");
        Instant failedAt = failureTimestamp != null ? Instant.parse(failureTimestamp) : Instant.now();

        log.warn("DLQ message received: topic={} key={} reason={}",
                 record.topic(), record.key(), failureReason);

        Map<String, Object> doc = new HashMap<>();
        doc.put("originalTopic",      record.topic());
        doc.put("messageKey",         record.key());
        doc.put("payload",            record.value());
        doc.put("failureReason",      failureReason);
        doc.put("failureTimestamp",   failedAt.toString());
        doc.put("status",             "PENDING_REVIEW");
        doc.put("createdAt",          Instant.now().toString());

        try {
            mongoTemplate.insert(doc, COLLECTION);
        } catch (Exception e) {
            // Log-only fallback — never rethrow from a DLQ consumer
            log.error("Failed to persist DLQ event to MongoDB: topic={} key={} error={}",
                      record.topic(), record.key(), e.getMessage(), e);
        }
    }

    private String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h != null ? new String(h.value(), StandardCharsets.UTF_8) : "unknown";
    }
}
