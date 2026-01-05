package com.barry.saga.retail.orderservice.client.catalog;

import com.barry.saga.retail.orderservice.client.dto.ProductResponseDTO;

import java.util.List;

public interface CatalogClient {

    ProductResponseDTO getProductBySku(String sku);

    List<ProductResponseDTO> getProductBySkus(List<String> skus);
}
