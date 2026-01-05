package com.barry.saga.retail.orderservice.controller.impl;

import com.barry.saga.retail.orderservice.controller.OrderController;
import com.barry.saga.retail.orderservice.dtos.CreateOrderRequest;
import com.barry.saga.retail.orderservice.dtos.OrderResponse;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.mappers.OrderWebMapper;
import com.barry.saga.retail.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Log4j2
@RequiredArgsConstructor
public class OrderControllerImpl implements OrderController {

    private final OrderService orderService;
    private final OrderWebMapper orderWebMapper;

    @Override
    public ResponseEntity<OrderResponse> createOrder(CreateOrderRequest createOrderRequest) {
        String idempotencyKey = UUID.randomUUID().toString();
        createOrderRequest.setIdempotencyKey(idempotencyKey);
        log.info("POST /api/v1/orders -> {}", createOrderRequest);

        OrderEntity orderEntity = orderWebMapper.toOrderEntity(createOrderRequest);
        OrderEntity savedOrder = orderService.createOrder(orderEntity);
        final OrderResponse orderResponse = orderWebMapper.toResponse(savedOrder);
        return ResponseEntity.ok()
                .header("Idempotency-key", idempotencyKey)
                .body(orderResponse);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId) {
        log.info("GET api/v1/orders/{}: ", orderId);
        OrderEntity fetchedOrder = orderService.getOrderByOrderId(orderId);
        return orderWebMapper.toResponse(fetchedOrder);
    }
}
