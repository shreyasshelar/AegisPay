-- ─────────────────────────────────────────────────────────────────────────────
-- V3 — Row-Level Security: tenant isolation for ledger-service
--
-- accounts      — direct tenant_id column, policy applied directly.
-- ledger_entries — linked to accounts; tenant_id denormalized for policy perf.
-- balance_locks  — linked to accounts; tenant_id denormalized.
-- outbox_entries — also receives tenant_id for consistent filtering.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Add tenant_id columns ─────────────────────────────────────────────────
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

-- Denormalize tenant_id into child tables for O(1) policy evaluation
ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

ALTER TABLE balance_locks
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

ALTER TABLE outbox_entries
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

-- Back-fill ledger_entries & balance_locks from parent accounts
UPDATE ledger_entries le
SET    tenant_id = a.tenant_id
FROM   accounts a
WHERE  le.account_id = a.id
  AND  le.tenant_id  = 'system';

UPDATE balance_locks bl
SET    tenant_id = a.tenant_id
FROM   accounts a
WHERE  bl.account_id = a.id
  AND  bl.tenant_id  = 'system';

-- ── 2. Indexes ───────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_accounts_tenant_id       ON accounts       (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_tenant_id ON ledger_entries  (tenant_id);
CREATE INDEX IF NOT EXISTS idx_balance_locks_tenant_id  ON balance_locks   (tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_id         ON outbox_entries  (tenant_id);

-- Composite index for the most common ledger query: tenant + account
CREATE INDEX IF NOT EXISTS idx_accounts_tenant_user
    ON accounts (tenant_id, user_id);

-- ── 3. Application role ──────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aegispay_ledger_svc') THEN
        CREATE ROLE aegispay_ledger_svc LOGIN;
    END IF;
END;
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO aegispay_ledger_svc', current_database());
END;
$$;
GRANT USAGE  ON SCHEMA public              TO aegispay_ledger_svc;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON accounts, ledger_entries, balance_locks, outbox_entries
    TO aegispay_ledger_svc;

-- ── 4. Enable RLS ────────────────────────────────────────────────────────────
ALTER TABLE accounts       ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE balance_locks  ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_entries ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON accounts;
DROP POLICY IF EXISTS tenant_isolation ON ledger_entries;
DROP POLICY IF EXISTS tenant_isolation ON balance_locks;
DROP POLICY IF EXISTS tenant_isolation ON outbox_entries;

-- ── 5. Tenant isolation policies ────────────────────────────────────────────
CREATE POLICY tenant_isolation ON accounts
    AS PERMISSIVE
    FOR ALL
    TO aegispay_ledger_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

CREATE POLICY tenant_isolation ON ledger_entries
    AS PERMISSIVE
    FOR ALL
    TO aegispay_ledger_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

CREATE POLICY tenant_isolation ON balance_locks
    AS PERMISSIVE
    FOR ALL
    TO aegispay_ledger_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

CREATE POLICY tenant_isolation ON outbox_entries
    AS PERMISSIVE
    FOR ALL
    TO aegispay_ledger_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

-- ── 6. Force RLS for all roles ───────────────────────────────────────────────
ALTER TABLE accounts       FORCE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries FORCE ROW LEVEL SECURITY;
ALTER TABLE balance_locks  FORCE ROW LEVEL SECURITY;
ALTER TABLE outbox_entries FORCE ROW LEVEL SECURITY;
