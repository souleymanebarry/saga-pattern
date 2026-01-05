package com.barry.saga.retail.orderservice.exceptions;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String msg) {
        super(msg);
    }
}

