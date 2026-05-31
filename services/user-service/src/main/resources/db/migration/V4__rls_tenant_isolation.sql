-- ─────────────────────────────────────────────────────────────────────────────
-- V4 — Row-Level Security: tenant isolation for user-service
--
-- Pattern:
--   1. Ensure tenant_id is NOT NULL going forward (backfill existing rows to
--      a sentinel "system" tenant so the constraint never fails on upgrade).
--   2. Create a least-privilege application role used by the service at runtime.
--   3. Enable RLS on every user-facing table.
--   4. USING policy: current_setting('app.tenant_id') — set by the service
--      before any query via SET LOCAL app.tenant_id = '<value>'.
--   5. BYPASSRLS is NOT granted to the app role; only the migration owner
--      (superuser / flyway role) bypasses RLS.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Backfill & constrain tenant_id on users ──────────────────────────────
-- The column already exists from V1 but is nullable.
-- Set any NULL rows to the sentinel before adding NOT NULL.
UPDATE users
SET tenant_id = 'system'
WHERE tenant_id IS NULL;

ALTER TABLE users
    ALTER COLUMN tenant_id SET NOT NULL,
    ALTER COLUMN tenant_id SET DEFAULT 'system';

-- Add tenant_id to kyc_documents (inherits tenant through users, but having
-- it as a direct column avoids expensive correlated sub-queries in policies).
ALTER TABLE kyc_documents
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'system';

-- Populate from parent user
UPDATE kyc_documents kd
SET    tenant_id = u.tenant_id
FROM   users u
WHERE  kd.user_id = u.id
  AND  kd.tenant_id = 'system';

CREATE INDEX IF NOT EXISTS idx_kyc_documents_tenant_id ON kyc_documents (tenant_id);

-- ── 2. Application role ──────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aegispay_user_svc') THEN
        CREATE ROLE aegispay_user_svc LOGIN;
    END IF;
END;
$$;

-- Grant only what the service needs
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO aegispay_user_svc', current_database());
END;
$$;
GRANT USAGE  ON SCHEMA public              TO aegispay_user_svc;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON users, kyc_documents               TO aegispay_user_svc;

-- ── 3. Enable RLS ────────────────────────────────────────────────────────────
ALTER TABLE users           ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyc_documents   ENABLE ROW LEVEL SECURITY;

-- Existing policies (idempotent re-run safety)
DROP POLICY IF EXISTS tenant_isolation ON users;
DROP POLICY IF EXISTS tenant_isolation ON kyc_documents;

-- ── 4. Tenant isolation policies ────────────────────────────────────────────
-- Reads & writes are scoped to the current tenant.
-- The 'system' superuser / migration role is never set via SET LOCAL, so it
-- falls back to BYPASSRLS and sees all rows.

CREATE POLICY tenant_isolation ON users
    AS PERMISSIVE
    FOR ALL
    TO aegispay_user_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

CREATE POLICY tenant_isolation ON kyc_documents
    AS PERMISSIVE
    FOR ALL
    TO aegispay_user_svc
    USING  (tenant_id = current_setting('app.tenant_id', TRUE))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', TRUE));

-- ── 5. Enforce for all roles (including table owner) ────────────────────────
ALTER TABLE users         FORCE ROW LEVEL SECURITY;
ALTER TABLE kyc_documents FORCE ROW LEVEL SECURITY;

-- ── 6. Index to help policy predicate ───────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_tenant_id_rls ON users (tenant_id, id);
