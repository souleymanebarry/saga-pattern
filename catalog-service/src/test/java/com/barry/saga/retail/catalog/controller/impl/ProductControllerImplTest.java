package com.barry.saga.retail.catalog.controller.impl;

import com.barry.saga.retail.catalog.dtos.ProductResponseDTO;
import com.barry.saga.retail.catalog.entities.ProductEntity;
import com.barry.saga.retail.catalog.exceptions.GlobalExceptionHandler;
import com.barry.saga.retail.catalog.exceptions.ProductNotFoundException;
import com.barry.saga.retail.catalog.mapper.ProductMapper;
import com.barry.saga.retail.catalog.service.ProductService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerImplTest {

    private ProductService productService;
    private ProductMapper productMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        productMapper = mock(ProductMapper.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductControllerImpl(productService, productMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @SneakyThrows
    @DisplayName("GET /api/v1/products/{sku} renvoie 200 avec le produit")
    void getBySku_returnsProduct() {
        ProductEntity product = ProductEntity.builder().sku("SKU-1").build();
        when(productService.getProductBySku("SKU-1")).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(
                ProductResponseDTO.builder().sku("SKU-1").price(new BigDecimal("19.99")).build());

        mockMvc.perform(get("/api/v1/products/{sku}", "SKU-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.price").value(19.99));
    }

    @Test
    @SneakyThrows
    @DisplayName("GET /api/v1/products/{sku} renvoie 404 si le SKU est inconnu")
    void getBySku_returns404WhenNotFound() {
        when(productService.getProductBySku("UNKNOWN")).thenThrow(new ProductNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/products/{sku}", "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail").value("Product not found: UNKNOWN"));
    }
}
