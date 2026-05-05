-- Enable extensions in the default aegispay database
-- (each service database also gets these via Flyway migration)

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
