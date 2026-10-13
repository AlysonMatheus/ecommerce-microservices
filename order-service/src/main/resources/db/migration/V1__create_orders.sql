CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    customer_id UUID          NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    total       NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES orders(id),
    product_id UUID          NOT NULL,
    quantity   INT           NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL
);
