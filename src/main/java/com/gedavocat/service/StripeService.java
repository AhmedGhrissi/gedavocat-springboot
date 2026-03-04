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
 * 
 * Utilise des Price IDs pré-créés sur Stripe (mode test/prod)
 * avec essai gratuit de 14 jours intégré.
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

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    // ── Price IDs Stripe (configurables par env/properties) ──
    @Value("${stripe.price.essentiel.monthly:price_1T7LmhCN6rHCwBaCGnryc3Aq}")
    private String priceEssentielMonthly;

    @Value("${stripe.price.essentiel.yearly:price_1T7Ln9CN6rHCwBaCjiiAh2Bg}")
    private String priceEssentielYearly;

    @Value("${stripe.price.professionnel.monthly:price_1T7LmoCN6rHCwBaC3PXpsddB}")
    private String priceProMonthly;

    @Value("${stripe.price.professionnel.yearly:price_1T7LnGCN6rHCwBaCeyQDFZ4I}")
    private String priceProYearly;

    @Value("${stripe.price.cabinet-plus.monthly:price_1T7LmwCN6rHCwBaCe1LmUDL0}")
    private String priceCabinetMonthly;

    @Value("${stripe.price.cabinet-plus.yearly:price_1T7LnNCN6rHCwBaC7wheamZ7}")
    private String priceCabinetYearly;

    /** Map interne plan+period → priceId, initialisée au démarrage */
    private Map<String, String> priceMap;

    /**
     * Initialisation de Stripe avec la clé API et le mapping des prix
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;

        // Construire le mapping plan+period → priceId
        priceMap = new HashMap<>();
        priceMap.put("ESSENTIEL_monthly", priceEssentielMonthly);
        priceMap.put("ESSENTIEL_yearly", priceEssentielYearly);
        priceMap.put("PROFESSIONNEL_monthly", priceProMonthly);
        priceMap.put("PROFESSIONNEL_yearly", priceProYearly);
        priceMap.put("CABINET_PLUS_monthly", priceCabinetMonthly);
        priceMap.put("CABINET_PLUS_yearly", priceCabinetYearly);

        log.info("Stripe initialisé — {} prix configurés", priceMap.size());
    }

    /**
     * Vérifie si Stripe est correctement configuré
     */
    public boolean isConfigured() {
        return stripeSecretKey != null &&
               !stripeSecretKey.isBlank() &&
               !stripeSecretKey.startsWith("sk_test_dummy") &&
               stripePublishableKey != null &&
               !stripePublishableKey.isBlank() &&
               !stripePublishableKey.startsWith("pk_test_dummy");
    }

    /**
     * Crée une session Stripe Checkout pour un abonnement
     * Utilise les Price IDs pré-créés (essai 14j inclus dans le prix Stripe)
     */
    public String createCheckoutSession(User user, String plan, String period) throws StripeException {
        if (!isConfigured()) {
            throw new RuntimeException("Stripe n'est pas configuré. Ajoutez vos clés API dans application.properties");
        }

        // Récupérer le Price ID correspondant
        String priceId = getPriceId(plan, period);
        if (priceId == null) {
            throw new IllegalArgumentException("Plan ou période invalide: " + plan + " / " + period);
        }

        // SEC-06 FIX : Utiliser app.base-url au lieu de localhost
        String successUrl = baseUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = baseUrl + "/payment/cancel";

        // Créer les métadonnées
        Map<String, String> metadata = new HashMap<>();
        metadata.put("user_id", user.getId());
        metadata.put("user_email", user.getEmail());
        metadata.put("plan", plan);
        metadata.put("period", period);

        // Construire la session Checkout avec le Price ID pré-créé
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setCustomerEmail(user.getEmail())
            .putAllMetadata(metadata)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .build();

        // Créer la session
        Session session = Session.create(params);

        log.info("Session Stripe créée: {} pour l'utilisateur {} (plan={}, période={})",
                session.getId(), user.getEmail(), plan, period);

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
     * Résout le Price ID Stripe pour un plan et une période donnés
     */
    private String getPriceId(String plan, String period) {
        String key = plan.toUpperCase() + "_" + period.toLowerCase();
        return priceMap.get(key);
    }

    /**
     * Retourne le nom d'affichage du plan
     */
    private String getPlanDisplayName(String plan) {
        return switch (plan.toUpperCase()) {
            case "ESSENTIEL" -> "Essentiel";
            case "PROFESSIONNEL" -> "Professionnel";
            case "CABINET_PLUS" -> "Cabinet+";
            default -> "Inconnu";
        };
    }

    /**
     * Retourne la clé publique Stripe (pour le frontend)
     */
    public String getPublishableKey() {
        return stripePublishableKey;
    }
}
