-- ─── Dev / local seed data ────────────────────────────────────────────────────
-- Inserts the two test users whose Keycloak sub values are defined in
-- infra/local/keycloak/realm-export.json.  Both rows use the exact Keycloak
-- user `id` as `external_id` so GET /api/v1/users/me always resolves correctly
-- after a DB wipe — no manual SQL patches required.
--
-- external_id values MUST match the `id` field in realm-export.json.
-- If you recreate the Keycloak realm and users get new UUIDs, update this file
-- and the corresponding ledger/transaction seed scripts to match.
--
-- Uses ON CONFLICT DO NOTHING so re-running (e.g. flyway repair) is safe.
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
    -- Test Customer  (Keycloak username: customer  |  sub: 56903685-bde3-45a4-80b9-d0d40432d548)
    (
        '3f951f39-c51e-4713-9b40-4209b0c4942b',
        '56903685-bde3-45a4-80b9-d0d40432d548',
        'customer@aegispay.local',
        '+918432204040',
        'Test',
        'Customer',
        'CUSTOMER',
        'default',
        'APPROVED',
        TRUE,
        0,
        now(),
        now()
    ),
    -- Test Payee     (Keycloak username: payee     |  sub: 4dfa538c-f408-4e2a-91b4-eba86846f39e)
    (
        'be0f53e9-ac88-4a0c-9f09-913df3c8f0eb',
        '4dfa538c-f408-4e2a-91b4-eba86846f39e',
        'payee@aegispay.local',
        '+918432204040',
        'Test',
        'Payee',
        'CUSTOMER',
        'default',
        'APPROVED',
        TRUE,
        0,
        now(),
        now()
    )
ON CONFLICT (external_id) DO NOTHING;
