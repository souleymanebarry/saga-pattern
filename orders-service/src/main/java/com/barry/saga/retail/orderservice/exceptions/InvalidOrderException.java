package com.barry.saga.retail.orderservice.exceptions;

/** Commande qui ne respecte pas les règles métier (aucun article, quantité invalide…). */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
