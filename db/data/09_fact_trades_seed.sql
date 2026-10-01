-- Seed data for analytics.fact_trades
-- Small example fact set aligned with the executions sample data

TRUNCATE TABLE analytics.fact_trades RESTART IDENTITY CASCADE;

INSERT INTO analytics.fact_trades
    (account_key, instrument_key, date_key, side, quantity, price, status)
VALUES
    (
        (SELECT account_key FROM analytics.dim_account WHERE account_id = 1),
        (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = 'GLBEQ1'),
        20260816,
        'BUY',
        100.0000,
        12.50,
        'FILLED'
    ),
    (
        (SELECT account_key FROM analytics.dim_account WHERE account_id = 2),
        (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = 'USEQUITY'),
        20260801,
        'BUY',
        75.0000,
        18.75,
        'FILLED'
    ),
    (
        (SELECT account_key FROM analytics.dim_account WHERE account_id = 3),
        (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = 'CORPB1'),
        20260811,
        'BUY',
        200.0000,
        101.00,
        'FILLED'
    ),
    (
        (SELECT account_key FROM analytics.dim_account WHERE account_id = 4),
        (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = 'GILT10'),
        20260905,
        'BUY',
        40.0000,
        104.75,
        'FILLED'
    ),
    (
        (SELECT account_key FROM analytics.dim_account WHERE account_id = 5),
        (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = 'EMMARKET'),
        20260722,
        'BUY',
        90.0000,
        9.30,
        'FILLED'
    ),
    (
        (SELECT account_key FROM analytics.dim_account WHERE account_id = 6),
        (SELECT instrument_key FROM analytics.dim_instrument WHERE symbol = 'GLBEQ1'),
        20260920,
        'BUY',
        200.0000,
        12.60,
        'FILLED'
    );