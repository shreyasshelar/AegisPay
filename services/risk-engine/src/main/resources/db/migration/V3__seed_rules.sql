-- ── V3: Rules Engine Configuration Seed ──────────────────────────────────────
-- Creates a configurable rule_config table so risk thresholds can be updated
-- at runtime (via admin API) without code deployments.
-- Defaults mirror the hardcoded values in RiskProperties.java.

CREATE TABLE IF NOT EXISTS rule_config (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name     VARCHAR(100) NOT NULL UNIQUE,
    description   TEXT,
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    parameters    JSONB        NOT NULL DEFAULT '{}',
    score_weight  INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rule_config_name    ON rule_config (rule_name);
CREATE INDEX IF NOT EXISTS idx_rule_config_enabled ON rule_config (enabled) WHERE enabled = true;

-- Trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION update_rule_config_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rule_config_updated_at
    BEFORE UPDATE ON rule_config
    FOR EACH ROW EXECUTE FUNCTION update_rule_config_timestamp();

-- ── Seed default rules ────────────────────────────────────────────────────────

INSERT INTO rule_config (rule_name, description, enabled, parameters, score_weight) VALUES

-- Amount threshold rule: large amounts from unverified users score higher
('AMOUNT_THRESHOLD',
 'Flags transactions exceeding the configurable amount limit for unverified KYC users.',
 true,
 '{
   "unverifiedKycLimitINR": 10000,
   "unverifiedKycLimitUSD": 120,
   "unverifiedKycLimitGBP": 95,
   "unverifiedKycLimitEUR": 110,
   "verifiedHighValueThresholdINR": 500000,
   "verifiedHighValueThresholdUSD": 6000
 }',
 40),

-- Velocity rule: too many transactions in a short window
('VELOCITY_CHECK',
 'Flags users who send more than maxTransactions in windowMinutes.',
 true,
 '{
   "windowMinutes": 5,
   "maxTransactions": 10,
   "softLimitTransactions": 6,
   "softLimitScoreBoost": 15
 }',
 35),

-- Blacklist rule: sender or receiver on fraud blacklist
('BLACKLIST_CHECK',
 'Instantly flags any transaction where the sender or receiver is on the fraud blacklist.',
 true,
 '{
   "redisKeyPrefix": "aegispay:blacklist:",
   "fallbackToDb": true
 }',
 100),

-- Geo-location rule: unusual country or VPN detected
('GEO_LOCATION',
 'Scores transactions originating from high-risk countries or via VPN/proxy.',
 true,
 '{
   "highRiskCountries": ["NG", "KP", "IR", "CU", "SY", "MM", "VE"],
   "mediumRiskCountries": ["PK", "BD", "RU", "BY"],
   "vpnDetectionEnabled": true,
   "highRiskScore": 50,
   "mediumRiskScore": 20,
   "vpnScore": 25
 }',
 50),

-- Time-of-day rule: unusual transaction hours
('TIME_OF_DAY',
 'Scores transactions placed during unusual hours (01:00–05:00 local time).',
 true,
 '{
   "suspiciousHoursStart": 1,
   "suspiciousHoursEnd": 5,
   "timezone": "UTC"
 }',
 15),

-- New device rule: first-time device fingerprint
('NEW_DEVICE',
 'Scores transactions from a device fingerprint never seen before for this user.',
 true,
 '{}',
 20),

-- Stripe Radar EFW rule: early fraud warning received from Stripe
('STRIPE_RADAR_EFW',
 'Marks a transaction HIGH RISK when Stripe Radar issues an Early Fraud Warning.',
 true,
 '{
   "autoRejectOnEfw": false,
   "requireManualReview": true
 }',
 80)

ON CONFLICT (rule_name) DO UPDATE
    SET description  = EXCLUDED.description,
        parameters   = EXCLUDED.parameters,
        score_weight = EXCLUDED.score_weight,
        updated_at   = now();

-- ── Seed initial fraud blacklist entries ──────────────────────────────────────
-- These are synthetic test entries for development; replace with real data in prod.

INSERT INTO fraud_blacklist (entity_type, entity_value, reason, added_by) VALUES
('IP',        '185.220.101.0/24', 'Known Tor exit node range used for fraud',          'system-seed'),
('IP',        '192.42.116.0/24',  'Known anonymisation proxy network',                  'system-seed'),
('USER_ID',   'FRAUD_TEST_001',   'Synthetic fraud test account — do not remove',       'system-seed'),
('EMAIL',     'fraud@tempmail.io','Disposable email domain associated with fraud rings', 'system-seed'),
('DEVICE_ID', 'EMULATOR_001',     'Known Android emulator fingerprint used in fraud',   'system-seed')
ON CONFLICT (entity_type, entity_value) DO NOTHING;

-- ── Score threshold documentation ─────────────────────────────────────────────
COMMENT ON TABLE rule_config IS
'Configurable risk rule parameters. The RulesEngine Spring beans read from RiskProperties '
'at startup; this table provides the authoritative store for operator-adjustable thresholds. '
'A future RuleConfigService will sync these values to the running application via a scheduled '
'refresh or Spring Cloud Config integration.';
