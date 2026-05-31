CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS accounts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    reserved_balance  NUMERIC(19,4) NOT NULL DEFAULT 0,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_available_balance_non_negative CHECK (available_balance >= 0),
    CONSTRAINT chk_reserved_balance_non_negative  CHECK (reserved_balance  >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_user_currency ON accounts (user_id, currency);
CREATE INDEX        IF NOT EXISTS idx_accounts_user_id        ON accounts (user_id);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       UUID         NOT NULL REFERENCES accounts(id),
    transaction_id   UUID         NOT NULL,
    entry_type       VARCHAR(20)  NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    balance_before   NUMERIC(19,4) NOT NULL,
    balance_after    NUMERIC(19,4) NOT NULL,
    description      VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_id     ON ledger_entries (account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_created_at     ON ledger_entries (created_at DESC);

CREATE TABLE IF NOT EXISTS balance_locks (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID         NOT NULL UNIQUE,
    account_id       UUID         NOT NULL REFERENCES accounts(id),
    reserved_amount  NUMERIC(19,4) NOT NULL,
    locked_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_balance_locks_account_id  ON balance_locks (account_id);
CREATE INDEX IF NOT EXISTS idx_balance_locks_expires_at  ON balance_locks (expires_at) WHERE expires_at IS NOT NULL;
