CREATE TABLE outbox_events (
                               id           UUID PRIMARY KEY,
                               aggregate_id UUID         NOT NULL,
                               routing_key  VARCHAR(100) NOT NULL,
                               payload      TEXT         NOT NULL,
                               created_at   TIMESTAMP    NOT NULL DEFAULT now(),
                               published_at TIMESTAMP
);

CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;