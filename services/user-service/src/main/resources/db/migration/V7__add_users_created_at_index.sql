-- Supports the back-office paginated user list which orders by created_at DESC.
-- Without this index the query does a full sequential scan of the users table.
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at DESC);
