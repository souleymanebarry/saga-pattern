package com.barry.saga.retail.orderservice.controller;

import com.barry.saga.retail.orderservice.dtos.CreateOrderRequest;
import com.barry.saga.retail.orderservice.dtos.OrderResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/v1/orders")
public interface OrderController {

  /** Clé générée par le client et réutilisée à l'identique en cas de nouvel essai. */
  String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  /**
   * Crée une commande. Le header {@value #IDEMPOTENCY_KEY_HEADER} est obligatoire :
   * une requête rejouée avec la même clé renvoie la commande déjà créée.
   */
  @PostMapping
  ResponseEntity<OrderResponse> createOrder(@RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
                                            @RequestBody CreateOrderRequest request);

  @GetMapping("/{orderId}")
  OrderResponse getOrderById(@PathVariable("orderId") UUID orderId);

}
