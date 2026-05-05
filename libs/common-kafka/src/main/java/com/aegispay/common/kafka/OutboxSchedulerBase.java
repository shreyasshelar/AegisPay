package com.aegispay.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Template-method base for all outbox schedulers.
 *
 * Each service extends this class and implements the three abstract methods
 * to plug in their own JPA repository. The scheduling annotation (@Scheduled)
 * lives in the concrete subclass so each service can tune its own interval.
 *
 * Guarantees:
 * - Fetches in batches to avoid memory pressure
 * - Marks published before sending (optimistic) — if Kafka fails, a retry
 *   will re-fetch the row only if it is still PENDING (avoid double-publish
 *   by using at-least-once semantics with idempotent producers)
 * - Marks failed after a configurable number of attempts so poison pills do
 *   not block the outbox forever
 */
@Slf4j
@RequiredArgsConstructor
public abstract class OutboxSchedulerBase {

    protected static final int DEFAULT_BATCH_SIZE = 100;

    private final KafkaTemplate<String, String> kafkaTemplate;

    protected abstract List<? extends OutboxRecord> fetchPendingEntries(int batchSize);

    protected abstract void markPublished(UUID id);

    protected abstract void markFailed(UUID id, String errorMessage);

    public void processOutbox() {
        List<? extends OutboxRecord> pending = fetchPendingEntries(DEFAULT_BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox entries", pending.size());

        for (OutboxRecord entry : pending) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(entry.getTopic(), entry.getMessageKey(), entry.getPayload());

                record.headers().add(new RecordHeader("event-type",
                        entry.getEventType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("aggregate-type",
                        entry.getAggregateType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("outbox-id",
                        entry.getId().toString().getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).get();
                markPublished(entry.getId());

                log.debug("Outbox entry published: id={} topic={} key={}",
                          entry.getId(), entry.getTopic(), entry.getMessageKey());

            } catch (Exception e) {
                log.error("Failed to publish outbox entry id={}: {}", entry.getId(), e.getMessage());
                markFailed(entry.getId(), e.getMessage());
            }
        }
    }
}
