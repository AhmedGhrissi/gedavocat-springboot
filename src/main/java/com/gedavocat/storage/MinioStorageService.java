package com.gedavocat.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Implémentation MinIO du StorageService (S3-compatible).
 *
 * Utilisé pour stocker les documents, signatures et factures
 * à la place du disque local.
 *
 * Configuration requise dans application.properties :
 *   minio.endpoint, minio.access-key, minio.secret-key
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;

    @Override
    public void store(String bucket, String key, InputStream data, long size, String mimeType) {
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(data, size, -1)
                            .contentType(mimeType)
                            .build()
            );
            log.debug("[Storage] Objet stocké : {}/{}", bucket, key);
        } catch (Exception e) {
            throw new RuntimeException("Erreur stockage MinIO [" + bucket + "/" + key + "] : " + e.getMessage(), e);
        }
    }

    @Override
    public void storeBytes(String bucket, String key, byte[] data, String mimeType) {
        store(bucket, key, new ByteArrayInputStream(data), data.length, mimeType);
    }

    @Override
    public byte[] getBytes(String bucket, String key) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lecture MinIO [" + bucket + "/" + key + "] : " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );
            log.debug("[Storage] Objet supprimé : {}/{}", bucket, key);
        } catch (Exception e) {
            log.warn("[Storage] Suppression MinIO échouée [{}/{}] : {}", bucket, key, e.getMessage());
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("Erreur vérification MinIO [" + bucket + "/" + key + "] : " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Erreur vérification MinIO [" + bucket + "/" + key + "] : " + e.getMessage(), e);
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("[Storage] Bucket créé : {}", bucket);
        }
    }
}
