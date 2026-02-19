package com.gedavocat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service d'intégration avec Yousign
 * Plateforme française de signature électronique gratuite jusqu'à 5 signatures/mois
 * https://yousign.com
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class YousignService {
    
    @Value("${yousign.api.key:}")
    private String apiKey;
    
    @Value("${yousign.api.url:https://api.yousign.com}")
    private String apiUrl;
    
    @Value("${yousign.api.version:v3}")
    private String apiVersion;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * Créer une demande de signature
     */
    public Map<String, Object> createSignatureRequest(
            String documentPath,
            String signerName,
            String signerEmail,
            String signatureLevel
    ) {
        try {
            String url = String.format("%s/%s/signature_requests", apiUrl, apiVersion);
            
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "Signature document juridique");
            requestBody.put("delivery_mode", "email");
            
            // Documents
            Map<String, Object> document = new HashMap<>();
            document.put("name", "document.pdf");
            document.put("file", documentPath); // Base64 ou URL du fichier
            requestBody.put("documents", new Object[]{document});
            
            // Signataires
            Map<String, Object> signer = new HashMap<>();
            signer.put("info", Map.of(
                "first_name", signerName.split(" ")[0],
                "last_name", signerName.split(" ").length > 1 ? signerName.split(" ")[1] : "",
                "email", signerEmail
            ));
            signer.put("signature_level", signatureLevel); // simple, advanced, qualified
            signer.put("signature_authentication_mode", "otp_sms"); // OTP par SMS
            
            requestBody.put("signers", new Object[]{signer});
            
            // Options
            Map<String, Object> options = new HashMap<>();
            options.put("brand_id", ""); // ID de votre marque (optionnel)
            options.put("custom_experience_id", ""); // Expérience personnalisée
            requestBody.put("options", options);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Demande de signature créée avec succès");
                return response.getBody();
            }
            
            throw new RuntimeException("Erreur lors de la création de la demande de signature");
            
        } catch (Exception e) {
            log.error("Erreur Yousign: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la communication avec Yousign", e);
        }
    }
    
    /**
     * Obtenir le statut d'une signature
     */
    public Map<String, Object> getSignatureStatus(String signatureRequestId) {
        try {
            String url = String.format("%s/%s/signature_requests/%s", 
                apiUrl, apiVersion, signatureRequestId);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                Map.class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du statut: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la communication avec Yousign", e);
        }
    }
    
    /**
     * Télécharger le document signé
     */
    public byte[] downloadSignedDocument(String signatureRequestId) {
        try {
            String url = String.format("%s/%s/signature_requests/%s/documents/download", 
                apiUrl, apiVersion, signatureRequestId);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                byte[].class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Erreur lors du téléchargement: {}", e.getMessage());
            throw new RuntimeException("Erreur lors du téléchargement du document", e);
        }
    }
    
    /**
     * Annuler une demande de signature
     */
    public void cancelSignatureRequest(String signatureRequestId) {
        try {
            String url = String.format("%s/%s/signature_requests/%s/cancel", 
                apiUrl, apiVersion, signatureRequestId);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            
            log.info("Demande de signature annulée");
            
        } catch (Exception e) {
            log.error("Erreur lors de l'annulation: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'annulation de la signature", e);
        }
    }
    
    /**
     * Relancer un signataire
     */
    public void remindSigner(String signatureRequestId, String signerId) {
        try {
            String url = String.format("%s/%s/signature_requests/%s/signers/%s/send_reminder", 
                apiUrl, apiVersion, signatureRequestId, signerId);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            
            log.info("Relance envoyée au signataire");
            
        } catch (Exception e) {
            log.error("Erreur lors de la relance: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la relance du signataire", e);
        }
    }
    
    /**
     * Créer les headers HTTP pour les requêtes Yousign
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }
    
    /**
     * Vérifier la configuration de l'API
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
