package com.barry.saga.retail.orderservice.share.event;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Événement reçu depuis billing-service suite au traitement d’un paiement.
 * <p>
 * Les valeurs possibles pour status sont :
 * - succeeded
 * - failed
 * </p>
 * Cet événement permet à order-service de finaliser ou d’échouer la commande.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResultEvent {

    @Builder.Default
    private String eventType = "PaymentResult";

    private UUID orderId;

    private String status;
}
