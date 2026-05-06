-- ─────────────────────────────────────────────────────────────────────────────
-- AegisPay ClickHouse Analytics Schema
--
-- ClickHouse is a columnar OLAP database optimised for real-time analytics.
-- Four tables covering: payments, fraud, SLA, and reconciliation.
--
-- Run via: clickhouse-client --multiline < infra/clickhouse/init.sql
-- Or mounted as /docker-entrypoint-initdb.d/init.sql in the container.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS aegispay_analytics;

USE aegispay_analytics;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. transaction_facts
--    One row per transaction event (completed / failed / rolled-back).
--    Primary data source for Payment Operations Dashboard.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transaction_facts
(
    -- Identity
    transaction_id          UUID,
    user_id                 UUID,

    -- Payment dimensions
    amount                  Decimal(18, 4),
    currency                LowCardinality(String),  -- INR, USD, EUR …
    status                  LowCardinality(String),  -- COMPLETED | FAILED | ROLLED_BACK
    failure_code            Nullable(String),         -- INSUFFICIENT_FUNDS, RISK_REJECTED …
    external_reference      Nullable(String),         -- Stripe PaymentIntent ID

    -- Timing
    event_time              DateTime64(3, 'UTC'),     -- when event hit Kafka
    processing_latency_ms   Int64,                    -- saga start → completion latency

    -- Partitioning helpers (derived from event_time)
    event_date              Date MATERIALIZED toDate(event_time),
    event_hour              UInt8 MATERIALIZED toHour(event_time)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, currency, status, transaction_id)
TTL event_date + INTERVAL 2 YEAR
SETTINGS index_granularity = 8192;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. risk_assessments
--    One row per risk evaluation from the risk-engine.
--    Primary data source for Fraud Intelligence Dashboard.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS risk_assessments
(
    transaction_id  UUID,
    user_id         UUID,

    -- Risk output
    risk_score      UInt8,                             -- 0-100
    decision        LowCardinality(String),            -- APPROVED | REJECTED | REVIEW
    rule_flags      Array(String),                     -- ['velocity_high', 'new_device', …]
    rule_count      UInt8 MATERIALIZED length(rule_flags),

    -- Timing
    event_time      DateTime64(3, 'UTC'),
    event_date      Date MATERIALIZED toDate(event_time)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, decision, risk_score, transaction_id)
TTL event_date + INTERVAL 1 YEAR
SETTINGS index_granularity = 8192;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. saga_latencies
--    End-to-end payment saga timing.
--    Primary data source for SLA Dashboard.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS saga_latencies
(
    transaction_id  UUID,
    saga_id         UUID,

    -- Timing
    started_at      DateTime64(3, 'UTC'),
    completed_at    DateTime64(3, 'UTC'),
    latency_ms      Int64,                  -- completed_at - started_at in ms

    -- Outcome
    final_status    LowCardinality(String), -- COMPLETED | FAILED | COMPENSATED | TIMEOUT

    -- Derived
    event_date      Date MATERIALIZED toDate(completed_at),
    event_hour      UInt8 MATERIALIZED toHour(completed_at)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, final_status, latency_ms)
TTL event_date + INTERVAL 1 YEAR
SETTINGS index_granularity = 8192;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. reconciliation_breaks
--    Discrepancies between AegisPay ledger and Stripe settlements.
--    Primary data source for Settlement Reconciliation Dashboard.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reconciliation_breaks
(
    -- Identity
    break_id            UUID DEFAULT generateUUIDv4(),
    report_date         Date,                          -- which day's reconciliation run
    transaction_id      Nullable(UUID),                -- AegisPay txn ID (null if Stripe-only)
    stripe_pi_id        Nullable(String),              -- Stripe PaymentIntent ID (null if ledger-only)

    -- Amounts
    ledger_amount       Nullable(Decimal(18, 4)),      -- what ledger says
    stripe_amount       Nullable(Decimal(18, 4)),      -- what Stripe settled
    break_amount        Decimal(18, 4),                -- |ledger - stripe| (abs diff)
    currency            LowCardinality(String),

    -- Break classification
    break_type          LowCardinality(String),        -- MISSING_IN_STRIPE | MISSING_IN_LEDGER | AMOUNT_MISMATCH | STATUS_MISMATCH
    break_status        LowCardinality(String),        -- OPEN | RESOLVED | ESCALATED
    ledger_status       Nullable(String),              -- COMPLETED | FAILED etc
    stripe_status       Nullable(String),              -- succeeded | requires_payment_method etc

    -- Resolution
    resolved_at         Nullable(DateTime64(3, 'UTC')),
    resolution_note     Nullable(String),

    -- Metadata
    created_at          DateTime64(3, 'UTC') DEFAULT now64()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(report_date)
ORDER BY (report_date, break_type, break_status, break_amount)
TTL report_date + INTERVAL 3 YEAR
SETTINGS index_granularity = 8192;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Materialised views for fast dashboard queries
-- ─────────────────────────────────────────────────────────────────────────────

-- Hourly transaction summary (feeds real-time ops dashboard)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_hourly_transaction_summary
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, event_hour, currency, status)
POPULATE
AS
SELECT
    event_date,
    event_hour,
    currency,
    status,
    count()                         AS tx_count,
    sum(amount)                     AS total_amount,
    avg(processing_latency_ms)      AS avg_latency_ms,
    quantile(0.95)(processing_latency_ms) AS p95_latency_ms,
    quantile(0.99)(processing_latency_ms) AS p99_latency_ms
FROM transaction_facts
GROUP BY event_date, event_hour, currency, status;

-- Hourly fraud summary (feeds fraud intelligence dashboard)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_hourly_risk_summary
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, decision)
POPULATE
AS
SELECT
    event_date,
    toHour(event_time)          AS event_hour,
    decision,
    count()                     AS assessment_count,
    avg(risk_score)             AS avg_risk_score,
    quantile(0.95)(risk_score)  AS p95_risk_score
FROM risk_assessments
GROUP BY event_date, event_hour, decision;

-- Daily reconciliation summary
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_reconciliation_summary
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(report_date)
ORDER BY (report_date, break_type, break_status)
POPULATE
AS
SELECT
    report_date,
    break_type,
    break_status,
    currency,
    count()             AS break_count,
    sum(break_amount)   AS total_break_amount
FROM reconciliation_breaks
GROUP BY report_date, break_type, break_status, currency;
