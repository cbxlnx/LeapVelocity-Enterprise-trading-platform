-- Instruments table: Stores tradable financial instruments (stocks, bonds, funds, cash)
CREATE TABLE instruments (
    symbol          VARCHAR(20) NOT NULL PRIMARY KEY,
    name            TEXT NOT NULL,
    asset_class     TEXT NOT NULL CHECK (asset_class IN ('EQUILTY', 'BOND', 'FUND', 'CASH')),
    currency        TEXT NOT NULL,
    tradable        BOOLEAN
);
