-- ─────────────────────────────────────────────────────────────────────────────
-- V4 — Row-Level Security: tenant isolation for transaction-service
--
-- Adds tenant_id to transactions (and the outbox), then wires up RLS so each
-- tenant can only read/write its own rows.  The service sets:
--   SET LOCAL app.tenant_id = '<tenant>';
-- at the start of every transaction before any DML/query.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Add tenant_id column ──────────────────────────────────────────────────
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

-- Outbox inherits same tenant context
ALTER TABLE outbox_entries
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

CREATE INDEX IF NOT EXISTS idx_txn_tenant_id    ON transactions   (tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_id ON outbox_entries (tenant_id);

-- Composite for common query pattern: tenant + status
CREATE INDEX IF NOT EXISTS idx_txn_tenant_status
    ON transactions (tenant_id, status);

-- ── 2. Application role ──────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aegispay_txn_svc') THEN
        CREATE ROLE aegispay_txn_svc LOGIN;
    END IF;
END;
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO aegispay_txn_svc', current_database());
END;
$$;
GRANT USAGE  ON SCHEMA public              TO aegispay_txn_svc;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON transactions, outbox_entries        TO aegispay_txn_svc;

-- ── 3. Enable RLS ────────────────────────────────────────────────────────────
ALTER TABLE transactions  ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_entries ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON transactions;
DROP POLICY IF EXISTS tenant_isolation ON outbox_entries;

-- ── 4. Tenant isolation policies ────────────────────────────────────────────
-- TRUE as second arg to current_setting means "return NULL if not set"
-- rather than throwing — prevents accidental superuser lockout during migration.
CREATE POLICY tenant_isolation ON transactions
    AS PERMISSIVE
    FOR ALL
    TO aegispay_txn_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

CREATE POLICY tenant_isolation ON outbox_entries
    AS PERMISSIVE
    FOR ALL
    TO aegispay_txn_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

-- ── 5. Force RLS even for table owner ───────────────────────────────────────
ALTER TABLE transactions   FORCE ROW LEVEL SECURITY;
ALTER TABLE outbox_entries FORCE ROW LEVEL SECURITY;
