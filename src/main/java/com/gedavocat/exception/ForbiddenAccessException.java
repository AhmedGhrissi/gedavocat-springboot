package com.gedavocat.exception;

/**
 * Exception levée lors d'un accès non autorisé à une ressource.
 * Produit un HTTP 403 via le GlobalExceptionHandler.
 */
public class ForbiddenAccessException extends RuntimeException {

    public ForbiddenAccessException(String message) {
        super(message);
    }

    public ForbiddenAccessException() {
        super("Vous n'avez pas accès à cette ressource");
    }
}
