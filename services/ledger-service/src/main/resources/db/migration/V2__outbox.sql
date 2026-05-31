CREATE TABLE IF NOT EXISTS outbox_entries (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id     VARCHAR(255) NOT NULL,
    aggregate_type   VARCHAR(100) NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    topic            VARCHAR(255) NOT NULL,
    message_key      VARCHAR(255),
    payload          TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_entries_pending ON outbox_entries (created_at ASC) WHERE status = 'PENDING';
