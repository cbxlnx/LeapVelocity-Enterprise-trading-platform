CREATE TABLE accounts (
    account_id          SERIAL PRIMARY KEY,
    holder_name         VARCHAR(255) NOT NULL,
    cash_balance        NUMERIC(18,2) NOT NULL,
    currency            TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    version             INTEGER DEFAULT 0,
    last_updated        TIMESTAMP DEFAULT NOW(),
    account_type        TEXT NOT NULL CHECK (account_type IN ('ISA', 'GIA', 'SIPP'))
);

CREATE TABLE instruments (
    symbol          VARCHAR(20) NOT NULL PRIMARY KEY,
    name            TEXT NOT NULL,
    asset_class     TEXT NOT NULL CHECK (asset_class IN ('EQUILTY', 'BOND', 'FUND', 'CASH')),
    currency        TEXT NOT NULL,
    tradable        BOOLEAN
);

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

CREATE TABLE positions (
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity        NUMERIC (14,4),
    average_cost    NUMERIC(18,2),
    PRIMARY KEY (account_id, symbol)
);
CREATE INDEX idx_positions_symbol ON positions(symbol);
