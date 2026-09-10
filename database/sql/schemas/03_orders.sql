-- Orders table: Stores trading orders with status tracking and idempotency
-- Indexes optimize queries by account and symbol
CREATE TABLE orders (
    order_id            SERIAL PRIMARY KEY,
    account_id          INTEGER NOT NULL REFERENCES accounts(account_id),
    symbol              VARCHAR(20) REFERENCES instruments(symbol),
    side                VARCHAR(4) NOT NULL,
    quantity            INTEGER NOT NULL,
    price               NUMERIC(18,2) NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'REJECTED', 'SUCCESFULL')),
    indempotency_key    VARCHAR(100) UNIQUE,
    created_on          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
