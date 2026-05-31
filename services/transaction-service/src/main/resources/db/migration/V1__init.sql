-- ─── Transactions (write side) ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id             UUID          NOT NULL,
    payer_id            UUID          NOT NULL,
    payee_id            UUID          NOT NULL,
    amount              NUMERIC(19,4) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'INITIATED',
    idempotency_key     VARCHAR(255)  NOT NULL,
    saga_id             UUID,                              -- set by payment-orchestrator
    initiated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    failure_reason      TEXT,
    external_reference  VARCHAR(255),                      -- payment-gateway reference
    metadata            JSONB,
    version             BIGINT        NOT NULL DEFAULT 0   -- optimistic lock
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_txn_idempotency_key ON transactions (idempotency_key);
CREATE        INDEX IF NOT EXISTS idx_txn_user_id         ON transactions (user_id);
CREATE        INDEX IF NOT EXISTS idx_txn_status          ON transactions (status);
CREATE        INDEX IF NOT EXISTS idx_txn_saga_id         ON transactions (saga_id) WHERE saga_id IS NOT NULL;
