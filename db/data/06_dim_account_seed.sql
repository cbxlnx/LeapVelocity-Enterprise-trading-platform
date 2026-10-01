-- Seed data for analytics.dim_account
-- Snapshot of current account attributes for reporting examples

TRUNCATE TABLE analytics.dim_account RESTART IDENTITY CASCADE;

INSERT INTO analytics.dim_account
    (account_id, external_account_id, holder_name, status, effective_date)
SELECT
    id,
    account_id,
    holder_name,
    status,
    DATE '2026-09-30'
FROM accounts
ORDER BY id;