-- AegisPay — Create per-service databases for local development
-- Each microservice owns its own database to enforce bounded-context isolation.
-- Run automatically by the PostgreSQL Docker container on first startup.

-- Note: CREATE DATABASE cannot run inside a transaction block, so each
-- statement is separated. The \connect metacommands switch database context.

SELECT 'CREATE DATABASE aegispay_users'    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aegispay_users')    \gexec
SELECT 'CREATE DATABASE aegispay_transactions' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aegispay_transactions') \gexec
SELECT 'CREATE DATABASE aegispay_ledger'   WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aegispay_ledger')   \gexec
SELECT 'CREATE DATABASE aegispay_sagas'    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aegispay_sagas')    \gexec
SELECT 'CREATE DATABASE aegispay_risk'     WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aegispay_risk')     \gexec
SELECT 'CREATE DATABASE aegispay_ai'       WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aegispay_ai')       \gexec

-- Grant the aegispay user access to all databases
GRANT ALL PRIVILEGES ON DATABASE aegispay_users        TO aegispay;
GRANT ALL PRIVILEGES ON DATABASE aegispay_transactions  TO aegispay;
GRANT ALL PRIVILEGES ON DATABASE aegispay_ledger        TO aegispay;
GRANT ALL PRIVILEGES ON DATABASE aegispay_sagas         TO aegispay;
GRANT ALL PRIVILEGES ON DATABASE aegispay_risk          TO aegispay;
GRANT ALL PRIVILEGES ON DATABASE aegispay_ai            TO aegispay;
