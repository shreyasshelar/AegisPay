CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE risk_cases (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID         NOT NULL UNIQUE,
    user_id          UUID         NOT NULL,
    risk_score       INT          NOT NULL,
    decision         VARCHAR(20)  NOT NULL,
    rule_flags       JSONB        NOT NULL DEFAULT '[]',
    rag_explanation  TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_cases_transaction_id ON risk_cases (transaction_id);
CREATE INDEX idx_risk_cases_user_id        ON risk_cases (user_id);
CREATE INDEX idx_risk_cases_decision       ON risk_cases (decision);

CREATE TABLE fraud_blacklist (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type  VARCHAR(20) NOT NULL,
    entity_value VARCHAR(255) NOT NULL,
    reason       TEXT,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    added_by     VARCHAR(255),
    CONSTRAINT uq_blacklist_type_value UNIQUE (entity_type, entity_value)
);

CREATE INDEX idx_blacklist_type_value ON fraud_blacklist (entity_type, entity_value);
