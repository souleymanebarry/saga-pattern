package com.barry.saga.retail.orderservice.client.catalog;

import com.barry.saga.retail.orderservice.client.dto.ProductResponseDTO;
import com.barry.saga.retail.orderservice.exceptions.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
                // SKU inconnu : erreur métier, pas une panne du catalog-service
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                    throw new ProductNotFoundException(sku);
                })
                .body(ProductResponseDTO.class);
    }
}
