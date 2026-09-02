INSERT INTO accounts
    (holder_name, cash_balance, currency, status, account_type, version, last_updated)
VALUES
    ('Alice Johnson', 250000.00, 'GBP', 'ACTIVE', 'ISA', 1, NOW()),
    ('Bob Smith', 150000.00, 'GBP', 'ACTIVE', 'GIA', 1, NOW()),
    ('Charlie Brown', 500000.00, 'GBP', 'ACTIVE', 'SIPP', 2, NOW()),
    ('Diana Prince', 180000.00, 'GBP', 'ACTIVE', 'ISA', 1, NOW()),
    ('Eve Wilson', 320000.00, 'GBP', 'SUSPENDED', 'GIA', 1, NOW()),
    ('Frank Miller', 420000.00, 'GBP', 'ACTIVE', 'SIPP', 1, NOW());

INSERT INTO instruments 
    (symbol, name, asset_class, currency, tradable)
VALUES
('GLBEQ1', 'Global Equity Index Fund', 'EQUILTY', 'GBP', TRUE),
('CORPB1', 'Sterling Corporate Bond Fund', 'BOND', 'GBP', TRUE),
('CASHGBP', 'Cash GBP', 'CASH', 'GBP', FALSE),
('GILT10', 'UK 10-Year Gilt', 'BOND', 'GBP', TRUE),
('USEQUITY', 'US Equity Fund', 'EQUILTY', 'GBP', TRUE),
('EMMARKET', 'Emerging Markets Fund', 'EQUILTY', 'GBP', TRUE),
('EUROFIX', 'Euro Fixed Income Fund', 'BOND', 'GBP', TRUE),
('MULTIFUND', 'Multi-Asset Fund', 'FUND', 'GBP', TRUE);

INSERT INTO orders
    (account_id, symbol, side, quantity, price, status, indempotency_key, created_on)
VALUES
    (1, 'GLBEQ1', 'BUY', 100, 12.50, 'SUCCESFULL', 'IDEM-001', NOW() - INTERVAL '45 days'),
    (1, 'CORPB1', 'BUY', 50, 101.25, 'SUCCESFULL', 'IDEM-002', NOW() - INTERVAL '40 days'),
    (1, 'GILT10', 'BUY', 30, 105.00, 'SUCCESFULL', 'IDEM-003', NOW() - INTERVAL '30 days'),
    (2, 'USEQUITY', 'BUY', 75, 18.75, 'SUCCESFULL', 'IDEM-004', NOW() - INTERVAL '60 days'),
    (2, 'EMMARKET', 'BUY', 120, 9.20, 'SUCCESFULL', 'IDEM-005', NOW() - INTERVAL '35 days'),
    (2, 'GLBEQ1', 'BUY', 50, 13.00, 'SUCCESFULL', 'IDEM-006', NOW() - INTERVAL '20 days'),
    (3, 'CORPB1', 'BUY', 200, 101.00, 'SUCCESFULL', 'IDEM-007', NOW() - INTERVAL '50 days'),
    (3, 'EUROFIX', 'BUY', 100, 98.50, 'SUCCESFULL', 'IDEM-008', NOW() - INTERVAL '45 days'),
    (3, 'MULTIFUND', 'BUY', 60, 22.00, 'PENDING', 'IDEM-009', NOW() - INTERVAL '5 days'),
    (4, 'GLBEQ1', 'BUY', 80, 12.75, 'SUCCESFULL', 'IDEM-010', NOW() - INTERVAL '55 days'),
    (4, 'GILT10', 'BUY', 40, 104.75, 'SUCCESFULL', 'IDEM-011', NOW() - INTERVAL '25 days'),
    (4, 'USEQUITY', 'BUY', 50, 19.00, 'REJECTED', 'IDEM-012', NOW() - INTERVAL '15 days'),
    (5, 'EMMARKET', 'BUY', 90, 9.30, 'SUCCESFULL', 'IDEM-013', NOW() - INTERVAL '70 days'),
    (5, 'CORPB1', 'BUY', 30, 102.00, 'SUCCESFULL', 'IDEM-014', NOW() - INTERVAL '40 days'),
    (6, 'MULTIFUND', 'BUY', 150, 21.50, 'SUCCESFULL', 'IDEM-015', NOW() - INTERVAL '65 days'),
    (6, 'EUROFIX', 'BUY', 120, 99.00, 'SUCCESFULL', 'IDEM-016', NOW() - INTERVAL '35 days'),
    (6, 'GLBEQ1', 'BUY', 200, 12.60, 'SUCCESFULL', 'IDEM-017', NOW() - INTERVAL '10 days'),
    (6, 'GILT10', 'BUY', 25, 105.25, 'PENDING', 'IDEM-018', NOW() - INTERVAL '2 days');

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



SELECT 'accounts' AS table_name, COUNT(*) AS row_count FROM accounts
UNION ALL
SELECT 'instruments', COUNT(*) FROM instruments
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'positions', COUNT(*) FROM positions;