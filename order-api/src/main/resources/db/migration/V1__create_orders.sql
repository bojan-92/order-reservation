CREATE TABLE orders
(
    order_id      VARCHAR(64) PRIMARY KEY,
    item_id       VARCHAR(64) NOT NULL,
    quantity      INTEGER     NOT NULL CHECK (quantity > 0),
    status        VARCHAR(16) NOT NULL,
    status_reason VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL
);
