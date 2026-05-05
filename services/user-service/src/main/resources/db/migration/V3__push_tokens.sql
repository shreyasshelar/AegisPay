-- ─── Push notification device tokens ─────────────────────────────────────────
-- Stores the FCM / APNs token per user so notification-service can deliver
-- targeted push notifications outside of WebSocket sessions.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS push_token          VARCHAR(512),
    ADD COLUMN IF NOT EXISTS push_token_platform VARCHAR(10)
        CONSTRAINT chk_push_token_platform CHECK (push_token_platform IN ('ios', 'android'));

CREATE INDEX IF NOT EXISTS idx_users_push_token_platform
    ON users (push_token_platform)
    WHERE push_token IS NOT NULL;
