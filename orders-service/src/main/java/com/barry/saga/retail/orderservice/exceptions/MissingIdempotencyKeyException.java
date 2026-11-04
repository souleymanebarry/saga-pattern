package com.barry.saga.retail.orderservice.exceptions;

/** Commande reçue sans clé d'idempotence : elle doit être générée par le client. */
public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("Idempotency-Key header is required and must not be blank");
    }
}
