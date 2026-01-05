package com.barry.saga.retail.catalog.mapper;

import com.barry.saga.retail.catalog.dtos.ProductRequestDTO;
import com.barry.saga.retail.catalog.dtos.ProductResponseDTO;
import com.barry.saga.retail.catalog.entities.ProductEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductEntity toEntity(ProductRequestDTO productRequest);

    ProductResponseDTO toResponse(ProductEntity productEntity);
}
