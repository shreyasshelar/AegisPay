-- ─── Dev / local seed data — admin & back-office users ──────────────────────
-- Adds the two staff users whose Keycloak sub values are defined in
-- infra/local/keycloak/realm-export.json.
--
-- UUIDs must stay in sync with realm-export.json:
--   admin     : aegispay_user_id = 97de22d4-9b21-411d-a52c-014e9710a103
--               external_id (Keycloak sub) = a1b2c3d4-e5f6-4890-ab01-ef1234567890
--   backoffice: aegispay_user_id = c3d4e5f6-a7b8-4012-cd03-123456789012
--               external_id (Keycloak sub) = b2c3d4e5-f6a7-4901-bc02-f12345678901
--
-- Without these rows every staff login triggers the needsRegistration path in
-- auth.ts (1.2 s extra delay + Keycloak attribute write-back) and the first API
-- call resolves via the slower getByExternalId fallback.
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
    -- Admin  (Keycloak username: admin  |  sub: a1b2c3d4-e5f6-4890-ab01-ef1234567890)
    (
        '97de22d4-9b21-411d-a52c-014e9710a103',
        'a1b2c3d4-e5f6-4890-ab01-ef1234567890',
        'admin@aegispay.local',
        NULL,
        'Admin',
        'User',
        'ADMIN',
        'default',
        'APPROVED',
        TRUE,
        0,
        now(),
        now()
    ),
    -- Back-office  (Keycloak username: backoffice  |  sub: b2c3d4e5-f6a7-4901-bc02-f12345678901)
    (
        'c3d4e5f6-a7b8-4012-cd03-123456789012',
        'b2c3d4e5-f6a7-4901-bc02-f12345678901',
        'backoffice@aegispay.local',
        NULL,
        'Back',
        'Office',
        'BACK_OFFICE',
        'default',
        'APPROVED',
        TRUE,
        0,
        now(),
        now()
    )
ON CONFLICT (external_id) DO NOTHING;
