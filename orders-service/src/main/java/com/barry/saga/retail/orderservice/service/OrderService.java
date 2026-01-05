package com.barry.saga.retail.orderservice.service;

import com.barry.saga.retail.orderservice.entities.OrderEntity;

import java.util.UUID;

public interface OrderService {

    OrderEntity createOrder(OrderEntity orderEntity);

    OrderEntity getOrderByOrderId(UUID orderId);
}
