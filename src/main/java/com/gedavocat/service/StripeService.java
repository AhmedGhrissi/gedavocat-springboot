package com.gedavocat.service;

import com.gedavocat.model.User;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Service de gestion des paiements avec Stripe
 */
@Service
@Slf4j
public class StripeService {

    @Value("${stripe.api.key:}")
    private String stripeSecretKey;

    @Value("${stripe.publishable.key:}")
    private String stripePublishableKey;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @Value("${server.port:8081}")
    private String serverPort;

    /**
     * Initialisation de Stripe avec la clé API
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
        log.info("Stripe initialisé avec succès");
    }

    /**
     * Vérifie si Stripe est correctement configuré
     */
    public boolean isConfigured() {
        return stripeSecretKey != null &&
               !stripeSecretKey.equals("sk_test_dummy_key") &&
               stripePublishableKey != null &&
               !stripePublishableKey.equals("pk_test_dummy_key");
    }

    /**
     * Crée une session Stripe Checkout pour un abonnement
     */
    public String createCheckoutSession(User user, String plan, String period) throws StripeException {
        if (!isConfigured()) {
            throw new RuntimeException("Stripe n'est pas configuré. Ajoutez vos clés API dans application.properties");
        }

        // Calculer le prix selon le plan et la période
        long amount = calculateAmount(plan, period);
        String planName = getPlanDisplayName(plan);

        // URLs de retour
        String successUrl = "http://localhost:" + serverPort + "/payment/success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = "http://localhost:" + serverPort + "/payment/cancel";

        // Créer les métadonnées
        Map<String, String> metadata = new HashMap<>();
        metadata.put("user_id", user.getId());
        metadata.put("user_email", user.getEmail());
        metadata.put("plan", plan);
        metadata.put("period", period);

        // Construire la session Checkout
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setCustomerEmail(user.getEmail())
            .putAllMetadata(metadata)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("eur")
                            .setUnitAmount(amount * 100) // Stripe utilise les centimes
                            .setRecurring(
                                SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                    .setInterval(period.equals("yearly") ?
                                        SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR :
                                        SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                    .build()
                            )
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("GED Avocat - Plan " + planName)
                                    .setDescription("Abonnement " + (period.equals("yearly") ? "annuel" : "mensuel"))
                                    .build()
                            )
                            .build()
                    )
                    .setQuantity(1L)
                    .build()
            )
            .build();

        // Créer la session
        Session session = Session.create(params);

        log.info("Session Stripe créée: {} pour l'utilisateur {}", session.getId(), user.getEmail());

        return session.getUrl();
    }

    /**
     * Récupère une session Checkout
     */
    public Session getSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    /**
     * Vérifie le statut d'un paiement
     */
    public boolean verifyPayment(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);
            return "complete".equals(session.getStatus()) &&
                   "paid".equals(session.getPaymentStatus());
        } catch (StripeException e) {
            log.error("Erreur lors de la vérification du paiement: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Récupère un abonnement
     */
    public Subscription getSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    /**
     * Annule un abonnement
     */
    public Subscription cancelSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        return subscription.cancel();
    }

    /**
     * Crée ou récupère un client Stripe
     */
    public Customer getOrCreateCustomer(User user) throws StripeException {
        // Chercher si le client existe déjà
        Map<String, Object> params = new HashMap<>();
        params.put("email", user.getEmail());
        params.put("limit", 1);

        CustomerCollection customers = Customer.list(params);

        if (!customers.getData().isEmpty()) {
            return customers.getData().get(0);
        }

        // Créer un nouveau client
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", user.getEmail());
        customerParams.put("name", user.getName());
        customerParams.put("metadata", Map.of("user_id", user.getId()));

        return Customer.create(customerParams);
    }

    /**
     * Construit l'événement du webhook avec vérification de signature
     */
    public Event constructWebhookEvent(String payload, String sigHeader) throws StripeException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    /**
     * Calcule le montant selon le plan et la période
     */
    private long calculateAmount(String plan, String period) {
        long baseAmount;

        switch (plan.toUpperCase()) {
            case "SOLO":
                baseAmount = 29;
                break;
            case "CABINET":
                baseAmount = 99;
                break;
            case "ENTERPRISE":
                baseAmount = 299;
                break;
            default:
                return 0;
        }

        // Réduction de 20% pour l'abonnement annuel
        if ("yearly".equals(period)) {
            return (long) (baseAmount * 0.8);
        }

        return baseAmount;
    }

    /**
     * Retourne le nom d'affichage du plan
     */
    private String getPlanDisplayName(String plan) {
        switch (plan.toUpperCase()) {
            case "SOLO":
                return "Solo";
            case "CABINET":
                return "Cabinet";
            case "ENTERPRISE":
                return "Enterprise";
            default:
                return "Inconnu";
        }
    }

    /**
     * Retourne la clé publique Stripe (pour le frontend)
     */
    public String getPublishableKey() {
        return stripePublishableKey;
    }
}
