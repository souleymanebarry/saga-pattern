--liquibase formatted sql
--changeset sbarry:001-create-products-table

CREATE TABLE products (
    product_id  UUID           NOT NULL,
    sku         VARCHAR(255)   NOT NULL,
    name        VARCHAR(255),
    price       NUMERIC(38, 2),
    description VARCHAR(255),
    brand       VARCHAR(255),
    created_at  TIMESTAMP,
    CONSTRAINT pk_products PRIMARY KEY (product_id),
    CONSTRAINT uk_products_sku UNIQUE (sku)
);
--rollback DROP TABLE products;
