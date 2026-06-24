package com.barry.saga.retail.catalog.service.impl;

import com.barry.saga.retail.catalog.entities.ProductEntity;
import com.barry.saga.retail.catalog.repositories.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    @DisplayName("getProductBySku renvoie le produit quand le SKU existe")
    void getProductBySku_returnsProduct_whenFound() {
        ProductEntity product = ProductEntity.builder()
                .sku("SKU-1")
                .name("Keyboard")
                .price(new BigDecimal("19.99"))
                .build();
        when(productRepository.findBySku("SKU-1")).thenReturn(Optional.of(product));

        assertThat(productService.getProductBySku("SKU-1")).isSameAs(product);
    }

    @Test
    @DisplayName("getProductBySku lève IllegalArgumentException quand le SKU est inconnu")
    void getProductBySku_throws_whenNotFound() {
        when(productRepository.findBySku("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductBySku("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("getAll délègue au repository et renvoie tous les produits")
    void getAll_returnsAllProducts() {
        List<ProductEntity> products = List.of(
                ProductEntity.builder().sku("SKU-1").build(),
                ProductEntity.builder().sku("SKU-2").build());
        when(productRepository.findAll()).thenReturn(products);

        assertThat(productService.getAll()).isEqualTo(products);
    }
}