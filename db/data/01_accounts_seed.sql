-- Seed data for accounts table
-- Sample trading accounts for development and testing

TRUNCATE TABLE accounts RESTART IDENTITY CASCADE;

INSERT INTO accounts
    (id, account_id, holder_name, cash_balance, status, version, last_updated)
VALUES
    (1, 'ACC-001', 'Alice Johnson', 250000.00, 'ACTIVE', 1, NOW()),
    (2, 'ACC-002', 'Bob Smith', 150000.00, 'ACTIVE', 1, NOW()),
    (3, 'ACC-003', 'Charlie Brown', 500000.00, 'ACTIVE', 2, NOW()),
    (4, 'ACC-004', 'Diana Prince', 180000.00, 'ACTIVE', 1, NOW()),
    (5, 'ACC-005', 'Eve Wilson', 320000.00, 'SUSPENDED', 1, NOW()),
    (6, 'ACC-006', 'Frank Miller', 420000.00, 'ACTIVE', 1, NOW());

SELECT setval(pg_get_serial_sequence('accounts', 'id'), COALESCE(MAX(id), 1), true) FROM accounts;
