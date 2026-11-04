package com.barry.saga.retail.orderservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO représentant la requête de création d'une commande.
 * Utilisé uniquement par le contrôleur REST.
 * La clé d'idempotence n'est pas dans le body : elle arrive par le header {@code Idempotency-Key}.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    private String customerId;

    private List<OrderItemRequest> items;
}
