package com.gedavocat.exception;

/**
 * Exception levée lors d'une erreur de validation métier.
 * Produit un HTTP 400 via le GlobalExceptionHandler.
 */
public class BusinessValidationException extends RuntimeException {

    public BusinessValidationException(String message) {
        super(message);
    }
}
