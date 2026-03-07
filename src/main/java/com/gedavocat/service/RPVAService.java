package com.gedavocat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service d'intégration avec le RPVA (Réseau Privé Virtuel des Avocats)
 * Anciennement e-Barreau - Communication électronique avec les juridictions
 * https://www.e-barreau.fr/
 */
@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings({"unchecked", "rawtypes", "null"})
public class RPVAService {
    
    @Value("${rpva.api.url:https://api.e-barreau.fr}")
    private String apiUrl;
    
    @Value("${rpva.api.key:}")
    private String apiKey;
    
    @Value("${rpva.certificate.path:}")
    private String certificatePath;
    
    @Value("${rpva.certificate.password:}")
    private String certificatePassword;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * Envoyer une communication vers une juridiction
     */
    public Map<String, Object> sendCommunication(
            String jurisdictionCode,
            String caseReference,
            String subject,
            String content,
            String[] attachments
    ) {
        try {
            String url = apiUrl + "/v1/communications/send";
            
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jurisdiction_code", jurisdictionCode);
            requestBody.put("case_reference", caseReference);
            requestBody.put("subject", subject);
            requestBody.put("content", content);
            requestBody.put("attachments", attachments);
            requestBody.put("send_date", LocalDateTime.now().toString());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Communication RPVA envoyée avec succès");
                return response.getBody();
            }
            
            throw new RuntimeException("Erreur lors de l'envoi de la communication RPVA");
            
        } catch (Exception e) {
            log.error("Erreur RPVA: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la communication avec le RPVA", e);
        }
    }
    
    /**
     * Récupérer les communications reçues
     */
    public Map<String, Object> getReceivedCommunications(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String status
    ) {
        try {
            // SEC FIX : utiliser UriComponentsBuilder pour encoder les paramètres URL
            String url = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(apiUrl + "/v1/communications/received")
                .queryParam("start", startDate)
                .queryParam("end", endDate)
                .queryParam("status", status)
                .build().toUriString();
            
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
            log.error("Erreur lors de la récupération des communications: {}", e.getMessage());
            throw new RuntimeException("Erreur RPVA", e);
        }
    }
    
    /**
     * Consulter le statut d'une communication
     */
    public Map<String, Object> getCommunicationStatus(String communicationId) {
        try {
            // SEC FIX : utiliser UriComponentsBuilder pour encoder le communicationId
            String url = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(apiUrl + "/v1/communications/{id}/status")
                .buildAndExpand(communicationId).toUriString();
            
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
            log.error("Erreur lors de la consultation du statut: {}", e.getMessage());
            throw new RuntimeException("Erreur RPVA", e);
        }
    }
    
    /**
     * Télécharger un accusé de réception
     */
    public byte[] downloadReceipt(String communicationId) {
        try {
            String url = String.format("%s/v1/communications/%s/receipt", apiUrl, communicationId);
            
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
            log.error("Erreur lors du téléchargement de l'AR: {}", e.getMessage());
            throw new RuntimeException("Erreur RPVA", e);
        }
    }
    
    /**
     * Rechercher une juridiction par code postal
     */
    public Map<String, Object> findJurisdiction(String postalCode, String jurisdictionType) {
        try {
            String url = String.format("%s/v1/jurisdictions/search?postal_code=%s&type=%s",
                apiUrl, postalCode, jurisdictionType);
            
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
            log.error("Erreur lors de la recherche de juridiction: {}", e.getMessage());
            throw new RuntimeException("Erreur RPVA", e);
        }
    }
    
    /**
     * Enregistrer un nouveau dossier au RPVA
     */
    public Map<String, Object> registerCase(
            String caseNumber,
            String jurisdictionCode,
            String caseType,
            Map<String, Object> parties
    ) {
        try {
            String url = apiUrl + "/v1/cases/register";
            
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("case_number", caseNumber);
            requestBody.put("jurisdiction_code", jurisdictionCode);
            requestBody.put("case_type", caseType);
            requestBody.put("parties", parties);
            requestBody.put("registration_date", LocalDateTime.now().toString());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement du dossier: {}", e.getMessage());
            throw new RuntimeException("Erreur RPVA", e);
        }
    }
    
    /**
     * Créer les headers HTTP pour les requêtes RPVA
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        // Note: En production, ajouter le certificat électronique
        return headers;
    }
    
    /**
     * Vérifier la configuration RPVA
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
