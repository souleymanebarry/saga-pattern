package com.barry.saga.retail.catalog.service;

import com.barry.saga.retail.catalog.entities.ProductEntity;

import java.util.List;

public interface ProductService {

    ProductEntity getProductBySku(String sku);

    List<ProductEntity> getAll();
}

