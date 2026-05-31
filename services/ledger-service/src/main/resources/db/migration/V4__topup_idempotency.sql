-- V4: Add idempotency_key to ledger_entries for Stripe top-up deduplication.
--     Also relax the NOT NULL constraint on transaction_id so top-up entries
--     (which have no P2P transaction) can be stored cleanly.

-- 1. Make transaction_id nullable (top-ups have no transaction)
ALTER TABLE ledger_entries
    ALTER COLUMN transaction_id DROP NOT NULL;

-- 2. Add idempotency_key (Stripe PaymentIntent ID) with a unique constraint
ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uidx_ledger_entries_idempotency_key
    ON ledger_entries (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
