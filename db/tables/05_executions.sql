-- Executions table: Stores one fill row for each FILLED order
-- Kept intentionally simple so persistence stays easy to reason about
CREATE TABLE executions (
    id                  UUID PRIMARY KEY,
    order_id            UUID NOT NULL UNIQUE REFERENCES orders(id),
    account_id          BIGINT NOT NULL REFERENCES accounts(id),
    symbol              VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    side                VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity            NUMERIC(18,4) NOT NULL CHECK (quantity > 0),
    price               NUMERIC(18,2) NOT NULL CHECK (price > 0),
    executed_on         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_account_id ON executions(account_id);
CREATE INDEX idx_executions_symbol ON executions(symbol);
CREATE INDEX idx_executions_executed_on ON executions(executed_on);