package com.gedavocat.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter pour chiffrer/déchiffrer les secrets MFA
 * en base de données de manière transparente.
 * 
 * Algorithme : AES-256-GCM (AEAD - Authenticated Encryption with Associated Data)
 * Conforme NIST SP 800-38D, ANSSI RGS, FIPS 140-2
 * 
 * Format stocké en BDD : Base64(IV || ciphertext || GCM-tag)
 * - IV  : 12 octets (96 bits) aléatoire unique par opération
 * - Tag : 128 bits (16 octets) pour authentification
 * 
 * La clé AES-256 est dérivée depuis la propriété security.mfa.encryption-key
 * qui DOIT être un secret de 32 octets encodé en Base64 (44 caractères).
 */
@Slf4j
@Component
@Converter
public class MfaSecretAttributeConverter implements AttributeConverter<String, String> {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;    // 96 bits (NIST recommended)
    private static final int GCM_TAG_BITS = 128;    // Maximum tag length
    
    /**
     * Clé statique initialisée par Spring via le setter @Value.
     * Pattern nécessaire car JPA instancie le converter en dehors du contexte Spring.
     */
    private static byte[] encryptionKeyBytes;
    private static final SecureRandom SECURE_RANDOM;
    
    static {
        try {
            SECURE_RANDOM = SecureRandom.getInstanceStrong();
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Impossible d'initialiser SecureRandom: " + e.getMessage());
        }
    }

    /**
     * Injection de la clé depuis application.properties.
     * Appelé une seule fois au démarrage par Spring (le bean @Component).
     */
    @Value("${security.mfa.encryption-key:}")
    public void setEncryptionKey(String key) {
        if (key == null || key.isBlank()) {
            log.warn("SEC-MFA: security.mfa.encryption-key non configurée. "
                + "Le chiffrement MFA est DÉSACTIVÉ. "
                + "Générez une clé : openssl rand -base64 32");
            encryptionKeyBytes = null;
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(key);
            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                    "La clé MFA doit faire exactement 32 octets (256 bits). "
                    + "Reçu: " + decoded.length + " octets. "
                    + "Générez: openssl rand -base64 32");
            }
            encryptionKeyBytes = decoded;
            log.info("SEC-MFA: Clé de chiffrement MFA initialisée (AES-256-GCM)");
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("32 octets")) throw e;
            throw new IllegalArgumentException(
                "security.mfa.encryption-key n'est pas un Base64 valide: " + e.getMessage());
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        if (encryptionKeyBytes == null) {
            // Clé non configurée : stocker en clair (mode dégradé avec warning)
            return attribute;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKeyBytes, "AES");
            
            // IV unique par opération (JAMAIS réutiliser un IV avec la même clé)
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // Format : IV || ciphertext (GCM tag est inclus dans le ciphertext par Java)
            ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("SEC-MFA: Erreur chiffrement secret MFA", e);
            throw new RuntimeException("Impossible de chiffrer le secret MFA", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        if (encryptionKeyBytes == null) {
            return dbData;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);
            
            // Vérifier longueur minimale : IV (12) + au moins 1 octet de données + tag (16)
            if (decoded.length < GCM_IV_LENGTH + 17) {
                // Donnée probablement en clair (migration depuis version non chiffrée)
                log.warn("SEC-MFA: Secret MFA en clair détecté en BDD (longueur {}). "
                    + "Il sera re-chiffré lors de la prochaine sauvegarde.", decoded.length);
                return dbData;
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Base64 decoding failed → data is plaintext (pre-encryption migration)
            log.warn("SEC-MFA: Secret MFA en clair détecté (non-Base64). "
                + "Il sera re-chiffré lors de la prochaine sauvegarde.");
            return dbData;
        } catch (Exception e) {
            log.error("SEC-MFA: Erreur déchiffrement secret MFA. "
                + "Vérifiez que security.mfa.encryption-key n'a pas changé.", e);
            throw new RuntimeException("Impossible de déchiffrer le secret MFA", e);
        }
    }
}
