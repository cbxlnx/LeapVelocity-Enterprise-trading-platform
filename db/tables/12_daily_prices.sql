-- Daily market history, separate from execution prices and live Kafka quotes.
-- price_date is the exchange trading date; loaded_at records ingestion time.
-- Keep close and adjusted_close separate for valuation and return calculations.
CREATE TABLE IF NOT EXISTS analytics.daily_prices (
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    price_date      DATE NOT NULL,
    open            NUMERIC(20,6) NOT NULL,
    high            NUMERIC(20,6) NOT NULL,
    low             NUMERIC(20,6) NOT NULL,
    close           NUMERIC(20,6) NOT NULL,
    adjusted_close  NUMERIC(20,6) NOT NULL,
    volume          BIGINT NOT NULL,
    source          TEXT NOT NULL,
    loaded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (symbol, price_date),
    -- The upper bound also rejects PostgreSQL's special numeric NaN value.
    CONSTRAINT ck_daily_prices_positive_finite CHECK (
        open > 0 AND open < 'Infinity'::numeric
        AND high > 0 AND high < 'Infinity'::numeric
        AND low > 0 AND low < 'Infinity'::numeric
        AND close > 0 AND close < 'Infinity'::numeric
        AND adjusted_close > 0 AND adjusted_close < 'Infinity'::numeric
    ),
    CONSTRAINT ck_daily_prices_ohlc CHECK (
        high >= low
        AND open BETWEEN low AND high
        AND close BETWEEN low AND high
    ),
    CONSTRAINT ck_daily_prices_volume CHECK (volume >= 0),
    CONSTRAINT ck_daily_prices_source CHECK (BTRIM(source) <> '')
);

-- The primary key indexes symbol/date history queries and supports
-- ON CONFLICT (symbol, price_date) in the historical price importer.
