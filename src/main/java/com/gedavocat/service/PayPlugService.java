package com.gedavocat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.gedavocat.model.User;

import java.util.*;

/**
 * Service d'intégration PayPlug - Paiement 100% français
 * Documentation : https://docs.payplug.com/
 */
@Service
public class PayPlugService {

    @Value("${payplug.secret.key:}")
    private String secretKey;

    @Value("${payplug.api.url:https://api.payplug.com/v1}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

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

            // URL de retour
            Map<String, String> notificationUrl = new HashMap<>();
            notificationUrl.put("url", "https://votredomaine.com/api/webhooks/payplug");
            request.put("notification_url", notificationUrl);

            Map<String, String> returnUrl = new HashMap<>();
            returnUrl.put("url", "https://votredomaine.com/payment/success");
            request.put("hosted_payment", Map.of(
                "return_url", "https://votredomaine.com/payment/success",
                "cancel_url", "https://votredomaine.com/payment/cancel"
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return false;
    }
}
