-- Seed data for instruments table
-- Curated US-listed tickers. market-data-publisher polls these symbols.
-- Keep this list in sync with the default in market-data-publisher/publisher.py.

TRUNCATE TABLE instruments RESTART IDENTITY CASCADE;

INSERT INTO instruments 
    (symbol, name, asset_class, currency, tradable)
VALUES
    ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', TRUE),
    ('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', TRUE),
    ('GOOGL', 'Alphabet Inc. Class A', 'EQUITY', 'USD', TRUE),
    ('AMZN', 'Amazon.com, Inc.', 'EQUITY', 'USD', TRUE),
    ('NVDA', 'NVIDIA Corporation', 'EQUITY', 'USD', TRUE),
    ('TSLA', 'Tesla, Inc.', 'EQUITY', 'USD', TRUE),
    ('JPM', 'JPMorgan Chase & Co.', 'EQUITY', 'USD', TRUE),
    ('JNJ', 'Johnson & Johnson', 'EQUITY', 'USD', TRUE),
    ('XOM', 'Exxon Mobil Corporation', 'EQUITY', 'USD', TRUE),
    ('WMT', 'Walmart Inc.', 'EQUITY', 'USD', TRUE),
    ('SPY', 'SPDR S&P 500 ETF Trust', 'FUND', 'USD', TRUE),
    ('QQQ', 'Invesco QQQ Trust', 'FUND', 'USD', TRUE);
