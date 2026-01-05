package com.barry.saga.retail.orderservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO représentant un item d'une commande dans les réponses API.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private UUID orderItemId;
    private String sku;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;

    /** Sous-total calculé : quantity × unitPrice */
    private BigDecimal subtotal;
}
