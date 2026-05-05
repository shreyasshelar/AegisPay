-- ─── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    external_id     VARCHAR(255) NOT NULL,          -- IdP subject (sub claim)
    email           VARCHAR(320) NOT NULL,
    phone           VARCHAR(30),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(50)  NOT NULL DEFAULT 'CUSTOMER',
    tenant_id       VARCHAR(100),
    kyc_status      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,  -- optimistic lock
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_external_id ON users (external_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email       ON users (email);
CREATE INDEX        IF NOT EXISTS idx_users_kyc_status  ON users (kyc_status);
CREATE INDEX        IF NOT EXISTS idx_users_tenant_id   ON users (tenant_id);

-- ─── KYC Documents ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS kyc_documents (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_type   VARCHAR(50) NOT NULL,   -- AADHAAR, PAN, PASSPORT, DRIVING_LICENSE
    document_ref    VARCHAR(500) NOT NULL,  -- S3/GCS object key
    ocr_status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    extracted_data  JSONB,                  -- AI-extracted structured data (masked fields only)
    tampered_flag   BOOLEAN     NOT NULL DEFAULT FALSE,
    quality_score   NUMERIC(5,2),           -- 0.00 – 100.00
    rejection_reason VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_user_id    ON kyc_documents (user_id);
CREATE INDEX IF NOT EXISTS idx_kyc_documents_ocr_status ON kyc_documents (ocr_status);
