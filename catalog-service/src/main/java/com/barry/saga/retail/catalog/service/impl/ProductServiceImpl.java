package com.barry.saga.retail.catalog.service.impl;

import com.barry.saga.retail.catalog.entities.ProductEntity;
import com.barry.saga.retail.catalog.repositories.ProductRepository;
import com.barry.saga.retail.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
@Log4j2
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public ProductEntity getProductBySku(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + sku));
    }

    @Override
    public List<ProductEntity> getAll() {
        return productRepository.findAll();
    }

}
