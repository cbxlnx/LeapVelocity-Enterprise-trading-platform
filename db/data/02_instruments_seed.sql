-- Seed data for instruments table
-- Curated US-listed tickers. market-data-publisher polls these symbols.
-- Keep this list in sync with the default in market-data-publisher/publisher.py.

TRUNCATE TABLE instruments RESTART IDENTITY CASCADE;

INSERT INTO instruments 
    (symbol, name, asset_class, currency, tradable, sector)
VALUES
    ('AAPL', 'Apple Inc.', 'EQUITY', 'USD', TRUE, 'Technology'),
    ('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', TRUE, 'Technology'),
    ('GOOGL', 'Alphabet Inc. Class A', 'EQUITY', 'USD', TRUE, 'Technology'),
    ('AMZN', 'Amazon.com, Inc.', 'EQUITY', 'USD', TRUE, 'Other'),
    ('NVDA', 'NVIDIA Corporation', 'EQUITY', 'USD', TRUE, 'Technology'),
    ('TSLA', 'Tesla, Inc.', 'EQUITY', 'USD', TRUE, 'Other'),
    ('JPM', 'JPMorgan Chase & Co.', 'EQUITY', 'USD', TRUE, 'Financials'),
    ('JNJ', 'Johnson & Johnson', 'EQUITY', 'USD', TRUE, 'Healthcare'),
    ('XOM', 'Exxon Mobil Corporation', 'EQUITY', 'USD', TRUE, 'Energy'),
    ('WMT', 'Walmart Inc.', 'EQUITY', 'USD', TRUE, 'Other'),
    ('SPY', 'SPDR S&P 500 ETF Trust', 'FUND', 'USD', TRUE, 'Other'),
    ('QQQ', 'Invesco QQQ Trust', 'FUND', 'USD', TRUE, 'Technology');
