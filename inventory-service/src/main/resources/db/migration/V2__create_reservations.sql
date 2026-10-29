-- One row per processed order. The primary key is what makes OrderPlaced handling idempotent.
CREATE TABLE reservations
(
    order_id   VARCHAR(64) PRIMARY KEY,
    item_id    VARCHAR(64) NOT NULL,
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(16) NOT NULL,
    reason     VARCHAR(255),
    message_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL
);
