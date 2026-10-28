package com.barry.saga.retail.orderservice.client.catalog;

import com.barry.saga.retail.orderservice.client.dto.ProductResponseDTO;

public interface CatalogClient {

    ProductResponseDTO getProductBySku(String sku);
}
