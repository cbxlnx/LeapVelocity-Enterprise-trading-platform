-- Orders table: Stores trading orders with status tracking and idempotency
-- Indexes optimize queries by account and symbol
CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    account_id          BIGINT NOT NULL REFERENCES accounts(id),
    symbol              VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    side                VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity            NUMERIC(18,4) NOT NULL,
    price               NUMERIC(18,2) NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    idempotency_key     VARCHAR(100) UNIQUE,
    created_on          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
