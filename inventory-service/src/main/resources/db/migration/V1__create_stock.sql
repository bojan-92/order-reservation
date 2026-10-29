CREATE TABLE stock_items
(
    item_id    VARCHAR(64) PRIMARY KEY,
    available  INTEGER     NOT NULL CHECK (available >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);
