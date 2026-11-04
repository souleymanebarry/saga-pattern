package com.barry.saga.retail.orderservice.exceptions;

/** SKU commandé absent du catalog-service. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String sku) {
        super("Product not found in catalog: " + sku);
    }
}
