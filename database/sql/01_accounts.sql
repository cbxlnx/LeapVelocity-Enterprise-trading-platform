-- Accounts table: Stores customer trading account information
-- Supports multiple account types (ISA, GIA, SIPP)
CREATE TABLE accounts (
    account_id          SERIAL PRIMARY KEY,
    holder_name         VARCHAR(255) NOT NULL,
    cash_balance        NUMERIC(18,2) NOT NULL,
    currency            TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    version             INTEGER DEFAULT 0,
    last_updated        TIMESTAMP DEFAULT NOW(),
    account_type        TEXT NOT NULL CHECK (account_type IN ('ISA', 'GIA', 'SIPP'))
);
