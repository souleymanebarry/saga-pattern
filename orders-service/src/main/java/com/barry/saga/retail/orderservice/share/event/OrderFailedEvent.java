package com.barry.saga.retail.orderservice.share.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Événement émis lorsqu'une commande échoue au cours du processus Saga.
 * <p>
 * Cela peut arriver pour plusieurs raisons :
 * - Stock insuffisant
 * - Paiement refusé
 * - Erreur interne dans un micro-service aval
 * </p>
 * Cet événement permet à d'autres services de réagir :
 * - en annulant des réservations
 * - en déclenchant des alertes
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderFailedEvent {

    @Builder.Default
    private String eventType = "OrderFailed";

    private UUID orderId;

    private String reason;

    private LocalDateTime failedAt;
}
