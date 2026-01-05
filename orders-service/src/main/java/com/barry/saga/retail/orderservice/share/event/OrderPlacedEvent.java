package com.barry.saga.retail.orderservice.share.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Événement émis lorsqu'une commande est créée.
 * <p>
 * Cet événement est publié sur Kafka pour informer les autres micro-services
 * (comme stock-service ) qu'une nouvelle commande vient d'être passée.
 * Il contient toutes les informations nécessaires pour démarrer le Saga :
 * - les articles
 * - le montant total
 * - les métadonnées
 * </p>
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPlacedEvent {

    @Builder.Default
    private String eventType = "OrderPlaced";

    private UUID orderId;

    private String customerId;

    private List<OrderItem> items;

    private BigDecimal totalAmount;

    private LocalDateTime createdAt;

    private String idempotencyKey;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItem {

        private String sku;

        private Integer quantity;

        private BigDecimal unitPrice;
    }
}



