package com.barry.saga.retail.orderservice.share.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockRejectedEvent {

    @Builder.Default
    private String eventType = "StockRejected";

    private UUID orderId;

    private String idempotencyKey;

    private String reason;

    private LocalDateTime rejectedAt;
}
