CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    schema_version INTEGER     NOT NULL,
    payload        TEXT        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INTEGER     NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
