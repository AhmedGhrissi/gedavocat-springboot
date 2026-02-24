package com.gedavocat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.gedavocat.model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Service d'intégration PayPlug - Paiement 100% français
 * Documentation : https://docs.payplug.com/
 */
@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class PayPlugService {

    @Value("${payplug.secret.key:}")
    private String secretKey;

    @Value("${payplug.api.url:https://api.payplug.com/v1}")
    private String apiUrl;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Vérifie la signature HMAC-SHA256 du webhook PayPlug.
     * Si aucune clé secrète n'est configurée, la vérification est ignorée (développement).
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        // SEC-03 FIX : Rejeter TOUS les webhooks si la clé n'est pas configurée
        if (secretKey == null || secretKey.isEmpty() || secretKey.startsWith("sk_test_YOUR")) {
            log.error("PayPlug non configuré — webhook rejeté (clé secrète manquante ou invalide)");
            return false;
        }
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hmacBytes);
            return computed.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    /**
     * Créer un paiement PayPlug
     * @return URL de redirection vers la page de paiement
     */
    public String createPayment(User user, String plan, double amount) {
        try {
            String url = apiUrl + "/payments";

            // Construire la requête
            Map<String, Object> request = new HashMap<>();
            request.put("amount", (int) (amount * 100)); // Centimes
            request.put("currency", "EUR");

            // SEC-06 FIX : Utiliser app.base-url au lieu d'URLs hardcodées
            Map<String, String> notificationUrl = new HashMap<>();
            notificationUrl.put("url", baseUrl + "/api/webhooks/payplug");
            request.put("notification_url", notificationUrl);

            Map<String, String> returnUrl = new HashMap<>();
            returnUrl.put("url", baseUrl + "/payment/success");
            request.put("hosted_payment", Map.of(
                "return_url", baseUrl + "/payment/success",
                "cancel_url", baseUrl + "/payment/cancel"
            ));

            // Client
            Map<String, String> customer = new HashMap<>();
            customer.put("email", user.getEmail());
            customer.put("first_name", user.getName().split(" ")[0]);
            customer.put("last_name", user.getName().contains(" ") ?
                user.getName().substring(user.getName().indexOf(" ") + 1) : "");
            request.put("customer", customer);

            // Métadonnées (pour identifier le paiement)
            Map<String, String> metadata = new HashMap<>();
            metadata.put("user_id", user.getId());
            metadata.put("plan", plan);
            request.put("metadata", metadata);

            // Headers avec authentification Basic
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = secretKey + ":";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> body = response.getBody();
                Map<String, String> hostedPayment = (Map<String, String>) body.get("hosted_payment");
                return hostedPayment.get("payment_url");
            }

        } catch (Exception e) {
            log.error("Erreur lors de la création du paiement PayPlug", e);
        }
        return null;
    }

    /**
     * Vérifier un paiement
     */
    public boolean verifyPayment(String paymentId) {
        try {
            String url = apiUrl + "/payments/" + paymentId;

            HttpHeaders headers = new HttpHeaders();
            String auth = secretKey + ":";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Boolean isPaid = (Boolean) response.getBody().get("is_paid");
                return Boolean.TRUE.equals(isPaid);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du paiement PayPlug: {}", paymentId, e);
        }
        return false;
    }

    /**
     * Rembourser un paiement
     */
    public boolean refundPayment(String paymentId, int amount) {
        try {
            String url = apiUrl + "/payments/" + paymentId + "/refunds";

            Map<String, Object> request = new HashMap<>();
            if (amount > 0) {
                request.put("amount", amount); // Remboursement partiel
            }
            // Si amount = 0, remboursement total

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = secretKey + ":";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            return response.getStatusCode() == HttpStatus.CREATED;

        } catch (Exception e) {
            log.error("Erreur lors du remboursement PayPlug: {}", paymentId, e);
        }
        return false;
    }
}
