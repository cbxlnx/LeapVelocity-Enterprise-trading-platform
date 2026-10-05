-- Seed data for instruments table
-- Available tradable financial instruments

TRUNCATE TABLE instruments RESTART IDENTITY CASCADE;

INSERT INTO instruments 
    (symbol, name, asset_class, currency, tradable)
VALUES
    ('GLBEQ1', 'Global Equity Index Fund', 'EQUITY', 'GBP', TRUE),
    ('CORPB1', 'Sterling Corporate Bond Fund', 'BOND', 'GBP', TRUE),
    ('CASHGBP', 'Cash GBP', 'CASH', 'GBP', FALSE),
    ('GILT10', 'UK 10-Year Gilt', 'BOND', 'GBP', TRUE),
    ('USEQUITY', 'US Equity Fund', 'EQUITY', 'GBP', TRUE),
    ('EMMARKET', 'Emerging Markets Fund', 'EQUITY', 'GBP', TRUE),
    ('EUROFIX', 'Euro Fixed Income Fund', 'BOND', 'GBP', TRUE),
    ('MULTIFUND', 'Multi-Asset Fund', 'FUND', 'GBP', TRUE);
