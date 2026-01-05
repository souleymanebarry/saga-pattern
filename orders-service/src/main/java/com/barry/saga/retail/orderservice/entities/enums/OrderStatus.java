package com.barry.saga.retail.orderservice.entities.enums;

public enum OrderStatus {

    PENDING,
    STOCK_CHECKING,
    PAYMENT_PROCESSING,
    FAILED,
    CANCELLED,
    STOCK_RESERVED,
    CONFIRMED,
    REJECTED
}
