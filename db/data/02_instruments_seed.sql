-- Seed data for instruments table
-- Curated US-listed tickers. market-data-publisher polls these symbols.
-- Keep this list in sync with the default in market-data-publisher/publisher.py.

TRUNCATE TABLE instruments RESTART IDENTITY CASCADE;

INSERT INTO instruments 
    (symbol, name, asset_class, currency, tradable, sector)
VALUES
    ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', TRUE, 'Information Technology'),
    ('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', TRUE, 'Information Technology'),
    ('GOOGL', 'Alphabet Inc. Class A', 'EQUITY', 'USD', TRUE, 'Communication Services'),
    ('AMZN', 'Amazon.com, Inc.', 'EQUITY', 'USD', TRUE, 'Consumer Discretionary'),
    ('NVDA', 'NVIDIA Corporation', 'EQUITY', 'USD', TRUE, 'Information Technology'),
    ('TSLA', 'Tesla, Inc.', 'EQUITY', 'USD', TRUE, 'Consumer Discretionary'),
    ('JPM', 'JPMorgan Chase & Co.', 'EQUITY', 'USD', TRUE, 'Financials'),
    ('JNJ', 'Johnson & Johnson', 'EQUITY', 'USD', TRUE, 'Healthcare'),
    ('XOM', 'Exxon Mobil Corporation', 'EQUITY', 'USD', TRUE, 'Energy'),
    ('WMT', 'Walmart Inc.', 'EQUITY', 'USD', TRUE, 'Consumer Staples'),
    ('SPY', 'SPDR S&P 500 ETF Trust', 'FUND', 'USD', TRUE, 'Multi-Sector Equity'),
    ('QQQ', 'Invesco QQQ Trust', 'FUND', 'USD', TRUE, 'Multi-Sector Equity'),
    -- Additional equities: ten distinct sectors, including previously missing sectors.
    ('V', 'Visa Inc. Class A', 'EQUITY', 'USD', TRUE, 'Financials'),
    ('UNH', 'UnitedHealth Group Incorporated', 'EQUITY', 'USD', TRUE, 'Healthcare'),
    ('CVX', 'Chevron Corporation', 'EQUITY', 'USD', TRUE, 'Energy'),
    ('PG', 'The Procter & Gamble Company', 'EQUITY', 'USD', TRUE, 'Consumer Staples'),
    ('CAT', 'Caterpillar Inc.', 'EQUITY', 'USD', TRUE, 'Industrials'),
    ('NEE', 'NextEra Energy, Inc.', 'EQUITY', 'USD', TRUE, 'Utilities'),
    ('AMT', 'American Tower Corporation', 'EQUITY', 'USD', TRUE, 'Real Estate'),
    ('LIN', 'Linde plc', 'EQUITY', 'USD', TRUE, 'Materials'),
    ('HD', 'The Home Depot, Inc.', 'EQUITY', 'USD', TRUE, 'Consumer Discretionary'),
    ('VZ', 'Verizon Communications Inc.', 'EQUITY', 'USD', TRUE, 'Communication Services'),
    -- Bond ETFs remain FUND instruments; sectors describe their underlying bond markets.
    ('IEF', 'iShares 7-10 Year Treasury Bond ETF', 'FUND', 'USD', TRUE, 'US Treasury Bonds'),
    ('LQD', 'iShares iBoxx $ Investment Grade Corporate Bond ETF', 'FUND', 'USD', TRUE, 'Investment Grade Corporate Bonds'),
    ('HYG', 'iShares iBoxx $ High Yield Corporate Bond ETF', 'FUND', 'USD', TRUE, 'High Yield Corporate Bonds'),
    ('MUB', 'iShares National Muni Bond ETF', 'FUND', 'USD', TRUE, 'Municipal Bonds'),
    ('EMB', 'iShares J.P. Morgan USD Emerging Markets Bond ETF', 'FUND', 'USD', TRUE, 'Emerging Markets Sovereign Bonds');
