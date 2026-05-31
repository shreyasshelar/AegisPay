-- ─── Transactional Outbox ─────────────────────────────────────────────────────
-- Rows in this table are written atomically with the business entity in a single
-- transaction, then a background scheduler publishes them to Kafka.
CREATE TABLE IF NOT EXISTS outbox_entries (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_id    VARCHAR(255) NOT NULL,   -- e.g. user UUID
    aggregate_type  VARCHAR(100) NOT NULL,   -- e.g. "User"
    event_type      VARCHAR(100) NOT NULL,   -- e.g. "UserRegisteredEvent"
    topic           VARCHAR(255) NOT NULL,   -- Kafka topic
    message_key     VARCHAR(255) NOT NULL,   -- Kafka partition key (usually aggregateId)
    payload         JSONB        NOT NULL,   -- Serialised event JSON
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',   -- PENDING | PUBLISHED | FAILED
    attempt_count   INT          NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_entries (status, created_at)
    WHERE status = 'PENDING';
