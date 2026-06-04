-- ─── Demo seed data — realistic named users ────────────────────────────────
-- Adds two additional customer personas for demos and portfolio walkthroughs.
-- These complement the existing customer/payee users from V5 so that every
-- "Send Money" demo uses real names instead of "Test Customer → Test Payee".
--
-- Keycloak sub UUIDs must match realm-export.json entries.
--   alice : external_id = c1a2b3c4-d5e6-4f78-9012-abcdef123456
--   bob   : external_id = d2b3c4d5-e6f7-4089-0123-bcdef1234567
--
-- These UUIDs are also referenced in ledger-service V7__seed_demo_accounts.sql.
-- Uses ON CONFLICT DO NOTHING — safe to re-run.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO users (
    id,
    external_id,
    email,
    phone,
    first_name,
    last_name,
    role,
    tenant_id,
    kyc_status,
    is_active,
    version,
    created_at,
    updated_at
) VALUES
    -- Alice Sharma — high-balance sender
    (
        'a1c2e3f4-1234-4abc-8def-000000000001',
        'c1a2b3c4-d5e6-4f78-9012-abcdef123456',
        'alice@aegispay.local',
        '+918432204040',
        'Alice',
        'Sharma',
        'CUSTOMER',
        'default',
        'APPROVED',
        TRUE,
        0,
        now(),
        now()
    ),
    -- Bob Mehta — receiver / secondary customer
    (
        'b2d3e4f5-2345-4bcd-9ef0-000000000002',
        'd2b3c4d5-e6f7-4089-0123-bcdef1234567',
        'bob@aegispay.local',
        '+918432204040',
        'Bob',
        'Mehta',
        'CUSTOMER',
        'default',
        'APPROVED',
        TRUE,
        0,
        now(),
        now()
    )
ON CONFLICT (external_id) DO NOTHING;
