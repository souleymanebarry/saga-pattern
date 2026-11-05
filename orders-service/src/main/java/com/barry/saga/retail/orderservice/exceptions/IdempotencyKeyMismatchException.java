package com.barry.saga.retail.orderservice.exceptions;

/** Idempotency-Key déjà utilisée pour une requête au contenu différent (client qui réutilise une clé). */
public class IdempotencyKeyMismatchException extends RuntimeException {

    public IdempotencyKeyMismatchException(String idempotencyKey) {
        super("Idempotency-Key " + idempotencyKey + " was already used with a different request body");
    }
}
