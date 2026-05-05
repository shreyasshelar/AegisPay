package com.aegispay.transaction.kafka;

import com.aegispay.common.domain.event.TransactionInitiatedEvent;
import com.aegispay.common.kafka.KafkaTopics;
import com.aegispay.transaction.domain.entity.OutboxEntry;
import com.aegispay.transaction.domain.entity.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Builds outbox entries for transaction events — never publishes to Kafka directly. */
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {

    private final ObjectMapper objectMapper;

    public OutboxEntry buildTransactionInitiatedEntry(Transaction txn) {
        TransactionInitiatedEvent event = TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .schemaVersion(1)
                .transactionId(txn.getId())
                .userId(txn.getUserId())
                .payerId(txn.getPayerId())
                .payeeId(txn.getPayeeId())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .idempotencyKey(txn.getIdempotencyKey())
                .metadata(txn.getMetadata() != null ? txn.getMetadata().toString() : null)
                .build();

        return OutboxEntry.builder()
                .aggregateId(txn.getId().toString())
                .aggregateType("Transaction")
                .eventType("TransactionInitiatedEvent")
                .topic(KafkaTopics.TRANSACTION_INITIATED)
                .messageKey(txn.getId().toString())
                .payload(serialize(event))
                .build();
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
