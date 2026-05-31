CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE knowledge_documents (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source      VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}',
    embedding   vector(1536),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_docs_source ON knowledge_documents (source);
CREATE INDEX idx_knowledge_docs_embedding
    ON knowledge_documents USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE TABLE ai_audit_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    request_type VARCHAR(50)  NOT NULL,
    input_masked TEXT,
    output       TEXT,
    model        VARCHAR(100),
    latency_ms   BIGINT,
    error        TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_request_type ON ai_audit_log (request_type);
CREATE INDEX idx_audit_log_created_at   ON ai_audit_log (created_at DESC);
