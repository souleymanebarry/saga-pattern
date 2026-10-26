--liquibase formatted sql
--changeset sbarry:002-create-order-items-table

CREATE TABLE order_items (
    order_item_id UUID           NOT NULL,
    sku           VARCHAR(255),
    product_name  VARCHAR(255),
    quantity      INTEGER,
    unit_price    NUMERIC(38, 2),
    order_id      UUID,
    CONSTRAINT pk_order_items PRIMARY KEY (order_item_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);
--rollback DROP TABLE order_items;
