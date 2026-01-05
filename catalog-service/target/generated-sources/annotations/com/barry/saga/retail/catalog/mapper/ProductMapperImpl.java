package com.barry.saga.retail.catalog.mapper;

import com.barry.saga.retail.catalog.dtos.ProductRequestDTO;
import com.barry.saga.retail.catalog.dtos.ProductResponseDTO;
import com.barry.saga.retail.catalog.entities.ProductEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-12-16T19:39:22+0100",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.4.1 (Oracle Corporation)"
)
@Component
public class ProductMapperImpl implements ProductMapper {

    @Override
    public ProductEntity toEntity(ProductRequestDTO productRequest) {
        if ( productRequest == null ) {
            return null;
        }

        ProductEntity.ProductEntityBuilder productEntity = ProductEntity.builder();

        productEntity.sku( productRequest.getSku() );
        productEntity.name( productRequest.getName() );
        productEntity.price( productRequest.getPrice() );
        productEntity.description( productRequest.getDescription() );
        productEntity.brand( productRequest.getBrand() );

        return productEntity.build();
    }

    @Override
    public ProductResponseDTO toResponse(ProductEntity productEntity) {
        if ( productEntity == null ) {
            return null;
        }

        ProductResponseDTO.ProductResponseDTOBuilder productResponseDTO = ProductResponseDTO.builder();

        productResponseDTO.productId( productEntity.getProductId() );
        productResponseDTO.sku( productEntity.getSku() );
        productResponseDTO.name( productEntity.getName() );
        productResponseDTO.price( productEntity.getPrice() );
        productResponseDTO.description( productEntity.getDescription() );
        productResponseDTO.brand( productEntity.getBrand() );
        productResponseDTO.createdAt( productEntity.getCreatedAt() );

        return productResponseDTO.build();
    }
}
