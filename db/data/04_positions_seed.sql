-- Seed data for positions table
-- Sample positions representing current account holdings
-- NOTE: Execute this file AFTER 01_accounts_seed.sql and 02_instruments_seed.sql

TRUNCATE TABLE positions RESTART IDENTITY CASCADE;

INSERT INTO positions
    (account_id, symbol, quantity, average_cost)
VALUES
    (1, 'GLBEQ1', 100.0000, 12.50),
    (1, 'CORPB1', 50.0000, 101.25),
    (1, 'GILT10', 30.0000, 105.00),
    (2, 'USEQUITY', 75.0000, 18.75),
    (2, 'EMMARKET', 120.0000, 9.20),
    (2, 'GLBEQ1', 50.0000, 13.00),
    (3, 'CORPB1', 200.0000, 101.00),
    (3, 'EUROFIX', 100.0000, 98.50),
    (4, 'GLBEQ1', 80.0000, 12.75),
    (4, 'GILT10', 40.0000, 104.75),
    (5, 'EMMARKET', 90.0000, 9.30),
    (5, 'CORPB1', 30.0000, 102.00),
    (6, 'MULTIFUND', 150.0000, 21.50),
    (6, 'EUROFIX', 120.0000, 99.00),
    (6, 'GLBEQ1', 200.0000, 12.60);
