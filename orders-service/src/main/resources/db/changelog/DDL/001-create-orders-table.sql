--liquibase formatted sql
--changeset sbarry:001-create-orders-table

CREATE TABLE orders (
    order_id        UUID           NOT NULL,
    customer_id     VARCHAR(255),
    status          VARCHAR(255),
    total_amount    NUMERIC(38, 2),
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key)
);
--rollback DROP TABLE orders;
