package com.barry.saga.retail.catalog.controller.impl;

import com.barry.saga.retail.catalog.controller.ProductController;
import com.barry.saga.retail.catalog.dtos.ProductResponseDTO;
import com.barry.saga.retail.catalog.entities.ProductEntity;
import com.barry.saga.retail.catalog.mapper.ProductMapper;
import com.barry.saga.retail.catalog.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Log4j2
public class ProductControllerImpl implements ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;


    @Override
    public ResponseEntity<ProductResponseDTO> getBySku(String sku) {
        log.info("GET api/v1/orders/{}: ", sku);
        ProductEntity product = productService.getProductBySku(sku);
        ProductResponseDTO response = productMapper.toResponse(product);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<ProductResponseDTO>> getAll() {
        log.info("GET api/v1/orders");
        List<ProductResponseDTO> response =
                productService.getAll().stream().map(productMapper::toResponse).toList();
        return ResponseEntity.ok(response);
    }

}
