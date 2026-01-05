package com.barry.saga.retail.orderservice.client.catalog;

import com.barry.saga.retail.orderservice.client.dto.ProductResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class CatalogClientImpl implements CatalogClient {

    private final RestClient restClient;

    @Value("${catalog.service.url}")
    private String catalogBaseUrl;

    @Override
    public ProductResponseDTO getProductBySku(String sku) {
        log.info("🔎 Calling catalog-service for SKU {}", sku);
        return restClient.get()
                .uri(catalogBaseUrl+"/api/v1/products/{sku}", sku)
                .retrieve()
                .body(ProductResponseDTO.class);
    }

    @Override
    public List<ProductResponseDTO> getProductBySkus(List<String> skus) {
        log.info("🔎 Calling catalog-service for SKU list {}", skus);
        return restClient.post()
                .uri(catalogBaseUrl + "/api/products/batch")
                .body(skus)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
