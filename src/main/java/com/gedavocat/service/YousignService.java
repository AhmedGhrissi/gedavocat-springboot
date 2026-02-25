package com.gedavocat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'intégration avec Yousign v3
 * Plateforme française de signature électronique
 * https://yousign.com
 * 
 * Flux API v3 :
 * 1. Créer une signature request
 * 2. Uploader le document (multipart)
 * 3. Ajouter le(s) signataire(s)
 * 4. Activer la demande
 */
@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class YousignService {
    
    @Value("${yousign.api.key:}")
    private String apiKey;
    
    @Value("${yousign.api.url:https://api-sandbox.yousign.app}")
    private String apiUrl;
    
    @Value("${yousign.api.version:v3}")
    private String apiVersion;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * Créer une demande de signature complète (multi-étapes Yousign v3)
     */
    public Map<String, Object> createSignatureRequest(
            String documentPath,
            String signerFirstName,
            String signerLastName,
            String signerEmail,
            String signatureLevel
    ) {
        if (!isConfigured()) {
            throw new RuntimeException("Yousign n'est pas configuré. Veuillez ajouter la variable YOUSIGN_API_KEY dans votre configuration.");
        }
        
        try {
            String baseUrl = String.format("%s/%s", apiUrl, apiVersion);
            
            // ── Étape 1 : Créer la signature request ──
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", "Signature document juridique");
            requestBody.put("delivery_mode", "email");
            requestBody.put("timezone", "Europe/Paris");
            // ordered_signers obligatoire pour qualified_electronic_signature
            requestBody.put("ordered_signers", true);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, createJsonHeaders());
            
            ResponseEntity<Map> srResponse = restTemplate.exchange(
                baseUrl + "/signature_requests",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            Map<String, Object> srResult = srResponse.getBody();
            String signatureRequestId = (String) srResult.get("id");
            log.info("Signature request créée: {}", signatureRequestId);
            
            // ── Étape 2 : Upload du document (multipart) ──
            byte[] fileBytes = readDocumentFile(documentPath);
            String fileName = Paths.get(documentPath).getFileName().toString();
            
            HttpHeaders uploadHeaders = new HttpHeaders();
            uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            uploadHeaders.setBearerAuth(apiKey);
            
            MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            uploadBody.add("file", fileResource);
            uploadBody.add("nature", "signable_document");
            
            HttpEntity<MultiValueMap<String, Object>> uploadEntity = new HttpEntity<>(uploadBody, uploadHeaders);
            
            ResponseEntity<Map> docResponse = restTemplate.exchange(
                baseUrl + "/signature_requests/" + signatureRequestId + "/documents",
                HttpMethod.POST,
                uploadEntity,
                Map.class
            );
            
            String uploadedDocumentId = (String) docResponse.getBody().get("id");
            log.info("Document uploadé: {}", uploadedDocumentId);
            
            // ── Étape 3 : Ajouter le signataire ──
            Map<String, Object> signerBody = new HashMap<>();
            signerBody.put("info", Map.of(
                "first_name", signerFirstName,
                "last_name", signerLastName,
                "email", signerEmail,
                "locale", "fr"
            ));
            String mappedLevel = mapSignatureLevel(signatureLevel);
            signerBody.put("signature_level", mappedLevel);
            // Pour qualified_electronic_signature, pas d'authentication_mode
            if (!"qualified_electronic_signature".equals(mappedLevel)) {
                signerBody.put("signature_authentication_mode", "no_otp");
            }
            signerBody.put("fields", List.of(Map.of(
                "document_id", uploadedDocumentId,
                "type", "signature",
                "page", 1,
                "x", 77,
                "y", 581,
                "width", 220,
                "height", 54
            )));
            
            HttpEntity<Map<String, Object>> signerEntity = new HttpEntity<>(signerBody, createJsonHeaders());
            
            restTemplate.exchange(
                baseUrl + "/signature_requests/" + signatureRequestId + "/signers",
                HttpMethod.POST,
                signerEntity,
                Map.class
            );
            log.info("Signataire ajouté: {}", signerEmail);
            
            // ── Étape 4 : Activer la demande ──
            HttpEntity<?> activateEntity = new HttpEntity<>(createJsonHeaders());
            
            ResponseEntity<Map> activateResponse = restTemplate.exchange(
                baseUrl + "/signature_requests/" + signatureRequestId + "/activate",
                HttpMethod.POST,
                activateEntity,
                Map.class
            );
            log.info("Demande de signature activée: {}", signatureRequestId);
            
            Map<String, Object> result = activateResponse.getBody() != null 
                ? new HashMap<>(activateResponse.getBody()) 
                : new HashMap<>();
            result.put("id", signatureRequestId);
            return result;
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur Yousign: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la communication avec Yousign: " + e.getMessage(), e);
        }
    }
    
    /**
     * Obtenir le statut d'une signature
     */
    public Map<String, Object> getSignatureStatus(String signatureRequestId) {
        try {
            String url = String.format("%s/%s/signature_requests/%s", 
                apiUrl, apiVersion, signatureRequestId);
            
            HttpHeaders headers = createJsonHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class
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
            
            HttpHeaders headers = createJsonHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class
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
            
            HttpHeaders headers = createJsonHeaders();
            Map<String, String> body = Map.of("reason", "contractualization_aborted");
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            
            log.info("Demande de signature annulée: {}", signatureRequestId);
            
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
            
            HttpHeaders headers = createJsonHeaders();
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            
            log.info("Relance envoyée au signataire");
            
        } catch (Exception e) {
            log.error("Erreur lors de la relance: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la relance du signataire", e);
        }
    }
    
    /**
     * Lire les bytes d'un fichier document
     */
    private byte[] readDocumentFile(String documentPath) {
        try {
            Path path = Paths.get(documentPath);
            if (!Files.exists(path)) {
                throw new RuntimeException("Le fichier n'existe pas: " + documentPath);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire le fichier: " + documentPath, e);
        }
    }
    
    /**
     * Convertir le niveau de signature UI vers l'API Yousign v3
     */
    private String mapSignatureLevel(String level) {
        return switch (level != null ? level.toLowerCase() : "simple") {
            case "advanced" -> "advanced_electronic_signature";
            case "qualified" -> "qualified_electronic_signature";
            default -> "electronic_signature";
        };
    }
    
    /**
     * Créer les headers HTTP JSON pour les requêtes Yousign
     */
    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }
    
    /**
     * Vérifier la configuration de l'API
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"dummy_key".equals(apiKey);
    }
}
