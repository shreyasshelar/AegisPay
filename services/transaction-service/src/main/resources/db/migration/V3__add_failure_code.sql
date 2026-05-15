-- Add machine-readable failure_code column to transactions table.
-- Separate from failure_reason (human-readable message) so the UI and AI RAG
-- pipeline can use the raw code (e.g. "amount_too_small") without string-splitting.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100);
