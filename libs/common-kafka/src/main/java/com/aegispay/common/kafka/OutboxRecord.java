package com.aegispay.common.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection used by OutboxSchedulerBase. Services map their @Entity outbox rows
 * to this interface so the base scheduler stays persistence-agnostic.
 */
public interface OutboxRecord {

    UUID getId();

    String getAggregateId();

    String getAggregateType();

    String getEventType();

    String getPayload();

    String getTopic();

    String getMessageKey();

    Instant getCreatedAt();
}
