package com.barry.saga.retail.orderservice.share.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedEvent {

    @Builder.Default
    private String eventType = "StockReserved";

    private UUID orderId;

    private List<Item> items;

    private LocalDateTime reservedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String sku;
        private Integer quantity;
    }
}

