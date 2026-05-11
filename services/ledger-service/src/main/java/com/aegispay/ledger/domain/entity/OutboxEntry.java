package com.aegispay.ledger.domain.entity;

import com.aegispay.common.kafka.OutboxRecord;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_entries")
public class OutboxEntry implements OutboxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String topic;

    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String status;

    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    // ----- JPA requirement: no-arg constructor -----
    public OutboxEntry() {}

    // ----- Builder-style all-args constructor -----
    public OutboxEntry(UUID id, String aggregateId, String aggregateType,
                       String eventType, String topic, String messageKey,
                       String payload, String status, String errorMessage,
                       Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (status == null) status = "PENDING";
    }

    // ----- Getters (OutboxRecord interface) -----
    @Override public UUID getId()            { return id; }
    @Override public String getAggregateId()   { return aggregateId; }
    @Override public String getAggregateType() { return aggregateType; }
    @Override public String getEventType()     { return eventType; }
    @Override public String getPayload()       { return payload; }
    @Override public String getTopic()         { return topic; }
    @Override public String getMessageKey()    { return messageKey; }
    @Override public Instant getCreatedAt()    { return createdAt; }

    // ----- Additional getters -----
    public String getStatus()       { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getPublishedAt() { return publishedAt; }

    // ----- Setters -----
    public void setId(UUID id)                        { this.id = id; }
    public void setAggregateId(String aggregateId)    { this.aggregateId = aggregateId; }
    public void setAggregateType(String aggregateType){ this.aggregateType = aggregateType; }
    public void setEventType(String eventType)        { this.eventType = eventType; }
    public void setTopic(String topic)                { this.topic = topic; }
    public void setMessageKey(String messageKey)      { this.messageKey = messageKey; }
    public void setPayload(String payload)            { this.payload = payload; }
    public void setStatus(String status)              { this.status = status; }
    public void setErrorMessage(String errorMessage)  { this.errorMessage = errorMessage; }
    public void setCreatedAt(Instant createdAt)       { this.createdAt = createdAt; }
    public void setPublishedAt(Instant publishedAt)   { this.publishedAt = publishedAt; }

    // ----- Static builder -----
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id;
        private String aggregateId;
        private String aggregateType;
        private String eventType;
        private String topic;
        private String messageKey;
        private String payload;
        private String status;
        private String errorMessage;
        private Instant createdAt;
        private Instant publishedAt;

        public Builder id(UUID id)                        { this.id = id; return this; }
        public Builder aggregateId(String v)              { this.aggregateId = v; return this; }
        public Builder aggregateType(String v)            { this.aggregateType = v; return this; }
        public Builder eventType(String v)                { this.eventType = v; return this; }
        public Builder topic(String v)                    { this.topic = v; return this; }
        public Builder messageKey(String v)               { this.messageKey = v; return this; }
        public Builder payload(String v)                  { this.payload = v; return this; }
        public Builder status(String v)                   { this.status = v; return this; }
        public Builder errorMessage(String v)             { this.errorMessage = v; return this; }
        public Builder createdAt(Instant v)               { this.createdAt = v; return this; }
        public Builder publishedAt(Instant v)             { this.publishedAt = v; return this; }

        public OutboxEntry build() {
            return new OutboxEntry(id, aggregateId, aggregateType, eventType, topic,
                    messageKey, payload, status, errorMessage, createdAt, publishedAt);
        }
    }
}
