-- ─── Dev / local seed data ────────────────────────────────────────────────────
-- Creates default INR accounts for the two test users seeded in user-service
-- V5__seed_dev_users.sql.  Those users were inserted directly via Flyway, so the
-- user.registered Kafka event never fired → ledger's UserRegisteredConsumer never
-- created their accounts → every payment attempt fails with ACCOUNT_NOT_FOUND.
--
-- user_id values MUST match the `id` column in user-service V5__seed_dev_users.sql
-- (the AegisPay domain UUID, NOT the Keycloak sub / external_id).
--
-- ₹100,000 INR starting balance is enough for any realistic dev/demo payment.
-- tenant_id = 'default' matches the seed users' tenant.
--
-- Uses ON CONFLICT DO NOTHING so re-running (e.g. flyway repair) is safe.
-- Runs as the postgres superuser (POSTGRES_USER=aegispay), which bypasses RLS —
-- the aegispay_ledger_svc role policy is enforced at application runtime only.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO accounts (
    id,
    user_id,
    tenant_id,
    currency,
    available_balance,
    reserved_balance,
    version,
    created_at,
    updated_at
) VALUES
    -- Test Customer  (user-service id: 3f951f39-c51e-4713-9b40-4209b0c4942b)
    (
        gen_random_uuid(),
        '3f951f39-c51e-4713-9b40-4209b0c4942b',
        'default',
        'INR',
        100000.0000,
        0.0000,
        0,
        now(),
        now()
    ),
    -- Test Payee     (user-service id: be0f53e9-ac88-4a0c-9f09-913df3c8f0eb)
    (
        gen_random_uuid(),
        'be0f53e9-ac88-4a0c-9f09-913df3c8f0eb',
        'default',
        'INR',
        100000.0000,
        0.0000,
        0,
        now(),
        now()
    )
ON CONFLICT (user_id, currency) DO NOTHING;
