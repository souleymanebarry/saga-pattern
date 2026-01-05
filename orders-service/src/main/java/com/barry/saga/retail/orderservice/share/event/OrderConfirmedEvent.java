package com.barry.saga.retail.orderservice.share.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Événement émis lorsqu'une commande est confirmée.
 * <p>
 * Cet événement est généralement envoyé après la validation :
 * - du stock
 * - du paiement
 * </p>
 * Il est consommé notamment par :
 * - notification-service
 * - shipping-service
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderConfirmedEvent {

    @Builder.Default
    private String eventType = "OrderConfirmed";

    private UUID orderId;

    private LocalDateTime confirmedAt;
}
