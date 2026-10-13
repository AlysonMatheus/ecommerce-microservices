CREATE TABLE stock (
    product_id UUID PRIMARY KEY,
    available  INT NOT NULL CHECK (available >= 0),
    reserved   INT NOT NULL DEFAULT 0 CHECK (reserved >= 0)
);

-- Garante idempotência: um pedido só é processado uma vez
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);
