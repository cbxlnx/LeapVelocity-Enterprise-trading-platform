-- Positions table: Stores current holdings for each account
-- Tracks quantity and average cost for portfolio valuation
CREATE TABLE positions (
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    symbol          VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    quantity        NUMERIC (14,4),
    average_cost    NUMERIC(18,2),
    PRIMARY KEY (account_id, symbol)
);

CREATE INDEX idx_positions_symbol ON positions(symbol);
