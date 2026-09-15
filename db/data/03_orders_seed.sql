-- Seed data for orders table
-- Sample trading orders for testing
-- NOTE: Execute this file AFTER 01_accounts_seed.sql and 02_instruments_seed.sql

TRUNCATE TABLE orders RESTART IDENTITY CASCADE;

INSERT INTO orders
    (account_id, symbol, side, quantity, price, status, indempotency_key, created_on)
VALUES
    (1, 'GLBEQ1', 'BUY', 100, 12.50, 'SUCCESSFUL', 'IDEM-001', NOW() - INTERVAL '45 days'),
    (1, 'CORPB1', 'BUY', 50, 101.25, 'SUCCESSFUL', 'IDEM-002', NOW() - INTERVAL '40 days'),
    (1, 'GILT10', 'BUY', 30, 105.00, 'SUCCESSFUL', 'IDEM-003', NOW() - INTERVAL '30 days'),
    (2, 'USEQUITY', 'BUY', 75, 18.75, 'SUCCESSFUL', 'IDEM-004', NOW() - INTERVAL '60 days'),
    (2, 'EMMARKET', 'BUY', 120, 9.20, 'SUCCESSFUL', 'IDEM-005', NOW() - INTERVAL '35 days'),
    (2, 'GLBEQ1', 'BUY', 50, 13.00, 'SUCCESSFUL', 'IDEM-006', NOW() - INTERVAL '20 days'),
    (3, 'CORPB1', 'BUY', 200, 101.00, 'SUCCESSFUL', 'IDEM-007', NOW() - INTERVAL '50 days'),
    (3, 'EUROFIX', 'BUY', 100, 98.50, 'SUCCESSFUL', 'IDEM-008', NOW() - INTERVAL '45 days'),
    (3, 'MULTIFUND', 'BUY', 60, 22.00, 'PENDING', 'IDEM-009', NOW() - INTERVAL '5 days'),
    (4, 'GLBEQ1', 'BUY', 80, 12.75, 'SUCCESSFUL', 'IDEM-010', NOW() - INTERVAL '55 days'),
    (4, 'GILT10', 'BUY', 40, 104.75, 'SUCCESSFUL', 'IDEM-011', NOW() - INTERVAL '25 days'),
    (4, 'USEQUITY', 'BUY', 50, 19.00, 'REJECTED', 'IDEM-012', NOW() - INTERVAL '15 days'),
    (5, 'EMMARKET', 'BUY', 90, 9.30, 'SUCCESSFUL', 'IDEM-013', NOW() - INTERVAL '70 days'),
    (5, 'CORPB1', 'BUY', 30, 102.00, 'SUCCESSFUL', 'IDEM-014', NOW() - INTERVAL '40 days'),
    (6, 'MULTIFUND', 'BUY', 150, 21.50, 'SUCCESSFUL', 'IDEM-015', NOW() - INTERVAL '65 days'),
    (6, 'EUROFIX', 'BUY', 120, 99.00, 'SUCCESSFUL', 'IDEM-016', NOW() - INTERVAL '35 days'),
    (6, 'GLBEQ1', 'BUY', 200, 12.60, 'SUCCESSFUL', 'IDEM-017', NOW() - INTERVAL '10 days'),
    (6, 'GILT10', 'BUY', 25, 105.25, 'PENDING', 'IDEM-018', NOW() - INTERVAL '2 days');
