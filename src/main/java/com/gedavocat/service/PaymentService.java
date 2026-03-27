package com.gedavocat.service;

import com.gedavocat.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    @Value("${payplug.secret.key:}")
    private String payplugSecretKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Créer un paiement Stripe (international, accepté en France)
     */
    public String createStripePayment(User user, String plan, double amount) {
        try {
            String url = "https://api.stripe.com/v1/checkout/sessions";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(stripeSecretKey, "");

            String body = "payment_method_types[0]=card" +
                    "&payment_method_types[1]=sepa_debit" + // SEPA pour France
                    "&line_items[0][price_data][currency]=eur" +
                    "&line_items[0][price_data][product_data][name]=Abonnement " + plan +
                    "&line_items[0][price_data][unit_amount=" + (int)(amount * 100) +
                    "&line_items[0][quantity]=1" +
                    "&mode=subscription" +
                    "&customer_email=" + user.getEmail() +
                    "&success_url=https://votresite.fr/subscription/success?session_id={CHECKOUT_SESSION_ID}" +
                    "&cancel_url=https://votresite.fr/subscription/cancel";

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return (String) response.getBody().get("url");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Créer un paiement PayPlug (100% français)
     */
    public String createPayPlugPayment(User user, String plan, double amount) {
        try {
            String url = "https://api.payplug.com/v1/payments";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(payplugSecretKey);

            Map<String, Object> payment = new HashMap<>();
            payment.put("amount", (int)(amount * 100)); // centimes
            payment.put("currency", "EUR");
            
            Map<String, String> customer = new HashMap<>();
            customer.put("email", user.getEmail());
            customer.put("first_name", user.getName().split(" ")[0]);
            customer.put("last_name", user.getName().contains(" ") ? 
                user.getName().split(" ")[1] : "");
            payment.put("customer", customer);

            Map<String, String> notification = new HashMap<>();
            notification.put("url", "https://votresite.fr/api/payplug/webhook");
            payment.put("notification_url", notification.get("url"));

            Map<String, String> urls = new HashMap<>();
            urls.put("return_url", "https://votresite.fr/subscription/success");
            urls.put("cancel_url", "https://votresite.fr/subscription/cancel");
            payment.put("hosted_payment", urls);

            payment.put("metadata", Map.of(
                "user_id", user.getId(),
                "plan", plan
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payment, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> hostedPayment = 
                    (Map<String, Object>) response.getBody().get("hosted_payment");
                return (String) hostedPayment.get("payment_url");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Vérifier le statut d'un paiement Stripe
     */
    public boolean verifyStripePayment(String sessionId) {
        try {
            String url = "https://api.stripe.com/v1/checkout/sessions/" + sessionId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(stripeSecretKey, "");

            HttpEntity<?> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String status = (String) response.getBody().get("payment_status");
                return "paid".equals(status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Vérifier le statut d'un paiement PayPlug
     */
    public boolean verifyPayPlugPayment(String paymentId) {
        try {
            String url = "https://api.payplug.com/v1/payments/" + paymentId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(payplugSecretKey);

            HttpEntity<?> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Boolean isPaid = (Boolean) response.getBody().get("is_paid");
                return Boolean.TRUE.equals(isPaid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
