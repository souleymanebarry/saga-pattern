package com.barry.saga.retail.catalog.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductResponseDTO {

    private UUID productId;

    private String sku;

    private String name;

    private BigDecimal price;

    private String description;

    private String brand;

    private LocalDateTime createdAt;
}
