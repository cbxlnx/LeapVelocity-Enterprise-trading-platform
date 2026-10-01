-- Seed data for analytics.dim_instrument
-- Reporting copy of current instrument reference data

TRUNCATE TABLE analytics.dim_instrument RESTART IDENTITY CASCADE;

INSERT INTO analytics.dim_instrument
    (symbol, name, asset_class, currency)
SELECT
    symbol,
    name,
    asset_class,
    currency
FROM instruments
ORDER BY symbol;