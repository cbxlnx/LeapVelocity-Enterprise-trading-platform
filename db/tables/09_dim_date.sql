-- Date dimension: Simple calendar dimension for batch-loaded trade facts
CREATE TABLE analytics.dim_date (
    date_key            INTEGER PRIMARY KEY,
    full_date           DATE NOT NULL UNIQUE,
    day                 SMALLINT NOT NULL CHECK (day BETWEEN 1 AND 31),
    month               SMALLINT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year                INTEGER NOT NULL,
    quarter             SMALLINT NOT NULL CHECK (quarter BETWEEN 1 AND 4)
);