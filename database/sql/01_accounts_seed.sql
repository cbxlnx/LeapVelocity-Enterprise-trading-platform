-- Seed data for accounts table
-- Sample trading accounts for development and testing

TRUNCATE TABLE accounts RESTART IDENTITY CASCADE;

INSERT INTO accounts
    (holder_name, cash_balance, currency, status, account_type, version, last_updated)
VALUES
    ('Alice Johnson', 250000.00, 'GBP', 'ACTIVE', 'ISA', 1, NOW()),
    ('Bob Smith', 150000.00, 'GBP', 'ACTIVE', 'GIA', 1, NOW()),
    ('Charlie Brown', 500000.00, 'GBP', 'ACTIVE', 'SIPP', 2, NOW()),
    ('Diana Prince', 180000.00, 'GBP', 'ACTIVE', 'ISA', 1, NOW()),
    ('Eve Wilson', 320000.00, 'GBP', 'SUSPENDED', 'GIA', 1, NOW()),
    ('Frank Miller', 420000.00, 'GBP', 'ACTIVE', 'SIPP', 1, NOW());
