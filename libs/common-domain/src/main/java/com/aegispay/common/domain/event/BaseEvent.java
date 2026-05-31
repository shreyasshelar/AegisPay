package com.aegispay.common.domain.event;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Root of all Kafka event hierarchy. Carries trace + correlation context so every
 * downstream consumer can propagate observability headers without parsing the payload.
 */
@Getter
@SuperBuilder
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class BaseEvent {

    private UUID eventId;
    private String correlationId;
    private String traceParent;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;

    private int schemaVersion;

    protected BaseEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.schemaVersion = 1;
        this.correlationId = null;
        this.traceParent = null;
    }

    protected BaseEvent(UUID eventId, String correlationId, String traceParent,
                        Instant occurredAt, int schemaVersion) {
        this.eventId = eventId;
        this.correlationId = correlationId;
        this.traceParent = traceParent;
        this.occurredAt = occurredAt;
        this.schemaVersion = schemaVersion;
    }
}
