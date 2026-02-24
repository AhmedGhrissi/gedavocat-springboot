package com.gedavocat.exception;

/**
 * Exception levée lorsqu'une ressource n'est pas trouvée.
 * Produit un HTTP 404 via le GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String entityName, String id) {
        super(entityName + " non trouvé(e) avec l'identifiant: " + id);
    }
}
