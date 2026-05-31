CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE sagas (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID         NOT NULL UNIQUE,
    user_id          UUID         NOT NULL,
    payer_id         UUID         NOT NULL,
    payee_id         UUID         NOT NULL,
    account_id       UUID,
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    current_step     VARCHAR(50)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    timeout_at       TIMESTAMPTZ  NOT NULL,
    failure_reason   TEXT,
    external_reference VARCHAR(255)
);

CREATE INDEX idx_sagas_transaction_id ON sagas (transaction_id);
CREATE INDEX idx_sagas_status         ON sagas (status) WHERE status IN ('RUNNING', 'COMPENSATING');
CREATE INDEX idx_sagas_timeout_at     ON sagas (timeout_at) WHERE status IN ('RUNNING', 'COMPENSATING');

CREATE TABLE saga_steps (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id          UUID         NOT NULL REFERENCES sagas(id),
    step_name        VARCHAR(50)  NOT NULL,
    step_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count    INT          NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMPTZ,
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_saga_steps_saga_id ON saga_steps (saga_id);
CREATE UNIQUE INDEX idx_saga_steps_saga_name ON saga_steps (saga_id, step_name);
