-- ─── Demo seed data — ledger accounts for all 4 demo users ─────────────────
-- Creates INR accounts for alice + bob (from user-service V8__seed_demo_users).
-- The existing customer + payee accounts from V6 remain unchanged.
--
-- user_id values MUST match id column in user-service V8__seed_demo_users.sql.
--
-- Balances are intentionally different so "Send Money" demos show realistic
-- fund movements:
--   alice ₹5,00,000 — high-value sender (good for large payment demos)
--   bob   ₹2,50,000 — moderate balance receiver
--
-- All four demo accounts combined:
--   customer@aegispay.local  ₹1,00,000  (from V6)
--   payee@aegispay.local     ₹1,00,000  (from V6)
--   alice@aegispay.local     ₹5,00,000  (this migration)
--   bob@aegispay.local       ₹2,50,000  (this migration)
--
-- Uses ON CONFLICT DO NOTHING — safe to re-run.
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
    -- Alice Sharma  (user-service id: a1c2e3f4-1234-4abc-8def-000000000001)
    (
        gen_random_uuid(),
        'a1c2e3f4-1234-4abc-8def-000000000001',
        'default',
        'INR',
        500000.0000,
        0.0000,
        0,
        now(),
        now()
    ),
    -- Bob Mehta  (user-service id: b2d3e4f5-2345-4bcd-9ef0-000000000002)
    (
        gen_random_uuid(),
        'b2d3e4f5-2345-4bcd-9ef0-000000000002',
        'default',
        'INR',
        250000.0000,
        0.0000,
        0,
        now(),
        now()
    )
ON CONFLICT (user_id, currency) DO NOTHING;
