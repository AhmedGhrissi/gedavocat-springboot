package com.gedavocat.storage;

import java.io.InputStream;

/**
 * Abstraction du stockage de fichiers — implémenté par MinioStorageService.
 * Permet un remplacement transparent du stockage (disque local → S3/MinIO).
 */
public interface StorageService {

    /**
     * Stocke un fichier dans le bucket donné.
     *
     * @param bucket   nom du bucket MinIO
     * @param key      clé de l'objet (ex : "uuid.pdf")
     * @param data     contenu du fichier
     * @param size     taille en octets (-1 si inconnue)
     * @param mimeType content-type
     */
    void store(String bucket, String key, InputStream data, long size, String mimeType);

    /**
     * Stocke des bytes directement (raccourci pour les fichiers en mémoire).
     */
    void storeBytes(String bucket, String key, byte[] data, String mimeType);

    /**
     * Récupère le contenu d'un objet en bytes.
     */
    byte[] getBytes(String bucket, String key);

    /**
     * Supprime un objet du bucket.
     */
    void delete(String bucket, String key);

    /**
     * Vérifie qu'un objet existe dans le bucket.
     */
    boolean exists(String bucket, String key);
}
