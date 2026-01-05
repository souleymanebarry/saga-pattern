package com.barry.saga.retail.catalog.controller;

import com.barry.saga.retail.catalog.dtos.ProductResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("/api/v1/products")
public interface ProductController {

    @GetMapping("/{sku}")
    ResponseEntity<ProductResponseDTO> getBySku(@PathVariable("sku") String sku);

    @GetMapping
    ResponseEntity<List<ProductResponseDTO>> getAll();
}
