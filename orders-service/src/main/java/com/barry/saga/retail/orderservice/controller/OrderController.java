package com.barry.saga.retail.orderservice.controller;

import com.barry.saga.retail.orderservice.dtos.CreateOrderRequest;
import com.barry.saga.retail.orderservice.dtos.OrderResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping("/api/v1/orders")
public interface OrderController {

  @PostMapping
  ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request);

  @GetMapping("/{orderId}")
  OrderResponse getOrderById(@PathVariable UUID orderId);

}
