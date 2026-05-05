-- Enable pgvector extension (used by ai-platform for RAG embeddings)
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable uuid-ossp for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create per-service schemas to namespace tables cleanly
CREATE SCHEMA IF NOT EXISTS user_svc;
CREATE SCHEMA IF NOT EXISTS transaction_svc;
CREATE SCHEMA IF NOT EXISTS ledger_svc;
CREATE SCHEMA IF NOT EXISTS orchestrator_svc;
CREATE SCHEMA IF NOT EXISTS risk_svc;
CREATE SCHEMA IF NOT EXISTS ai_svc;
