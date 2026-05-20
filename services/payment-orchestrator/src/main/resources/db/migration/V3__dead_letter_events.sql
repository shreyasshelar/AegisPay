-- Dead-letter events: messages that exhausted all retry attempts in this service.
-- Status lifecycle: PENDING_REVIEW → REPLAYED | DISCARDED
CREATE TABLE IF NOT EXISTS dead_letter_events (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    original_topic     VARCHAR(255) NOT NULL,
    message_key        VARCHAR(255),
    payload            TEXT         NOT NULL,
    failure_reason     TEXT         NOT NULL,
    failure_timestamp  TIMESTAMPTZ  NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_at        TIMESTAMPTZ,
    reviewed_by        VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dle_pending
    ON dead_letter_events (status, created_at)
    WHERE status = 'PENDING_REVIEW';

CREATE INDEX IF NOT EXISTS idx_dle_topic
    ON dead_letter_events (original_topic, created_at DESC);
