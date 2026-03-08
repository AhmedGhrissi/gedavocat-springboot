package com.gedavocat.service;

import com.gedavocat.model.User;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
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

    // Injected from env var (not hardcoded) - audit-security-approved
    @Value("${stripe.api.key:}") // gitleaks:allow
    private String stripeApiKey; // gitleaks:allow

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
        Stripe.apiKey = stripeApiKey; // gitleaks:allow

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
        return stripeApiKey != null && // gitleaks:allow
               !stripeApiKey.isBlank() && // gitleaks:allow
               !stripeApiKey.startsWith("sk_test_dummy") && // gitleaks:allow
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
        String successUrl = baseUrl + "/subscription/success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = baseUrl + "/subscription/cancel";

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
     * Annule un abonnement immédiatement
     */
    public Subscription cancelSubscription(String subscriptionId) throws StripeException {
        Subscription subscription = Subscription.retrieve(subscriptionId);
        return subscription.cancel();
    }

    /**
     * Annule un abonnement Stripe en fin de période par subscriptionId direct.
     * (cancel_at_period_end = true)
     */
    public Subscription cancelSubscriptionByIdAtPeriodEnd(String subscriptionId) throws StripeException {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            log.warn("Pas de subscriptionId pour annuler l'abonnement");
            return null;
        }
        Subscription subscription = Subscription.retrieve(subscriptionId);
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("cancel_at_period_end", true);
        Subscription updated = subscription.update(updateParams);
        log.info("Abonnement {} marqué pour annulation en fin de période", subscriptionId);
        return updated;
    }

    /**
     * Annule l'abonnement actif d'un client Stripe en fin de période
     * (cancel_at_period_end = true) — recherche par customerId
     * @return le Subscription modifié, ou null si aucun abonnement actif trouvé
     */
    public Subscription cancelSubscriptionAtPeriodEnd(String stripeCustomerId) throws StripeException {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            log.warn("Pas de stripeCustomerId pour annuler l'abonnement");
            return null;
        }

        // Lister les abonnements actifs du client
        Map<String, Object> params = new HashMap<>();
        params.put("customer", stripeCustomerId);
        params.put("status", "active");
        params.put("limit", 1);

        SubscriptionCollection subscriptions = Subscription.list(params);

        if (subscriptions.getData().isEmpty()) {
            // Essayer aussi trialing
            params.put("status", "trialing");
            subscriptions = Subscription.list(params);
        }

        if (subscriptions.getData().isEmpty()) {
            log.warn("Aucun abonnement actif trouvé pour le client Stripe: {}", stripeCustomerId);
            return null;
        }

        Subscription subscription = subscriptions.getData().get(0);
        
        // Planifier l'annulation en fin de période
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("cancel_at_period_end", true);
        Subscription updated = subscription.update(updateParams);
        
        log.info("Abonnement {} marqué pour annulation en fin de période pour client {}", 
                 subscription.getId(), stripeCustomerId);
        return updated;
    }

    /**
     * Met à jour le plan d'un abonnement Stripe existant (upgrade/downgrade)
     * au lieu de créer un nouveau checkout. Proratisation automatique.
     */
    public Subscription updateSubscriptionPlan(String subscriptionId, String plan, String period) throws StripeException {
        String newPriceId = getPriceId(plan, period);
        if (newPriceId == null) {
            throw new IllegalArgumentException("Plan ou période invalide: " + plan + " / " + period);
        }

        Subscription subscription = Subscription.retrieve(subscriptionId);
        if (subscription.getItems() == null || subscription.getItems().getData().isEmpty()) {
            throw new RuntimeException("Aucun item trouvé sur l'abonnement " + subscriptionId);
        }

        SubscriptionItem currentItem = subscription.getItems().getData().get(0);

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
            .addItem(SubscriptionUpdateParams.Item.builder()
                .setId(currentItem.getId())
                .setPrice(newPriceId)
                .build())
            .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
            .build();

        Subscription updated = subscription.update(params);
        log.info("Abonnement {} mis à jour vers plan={} period={} (priceId={})",
                subscriptionId, plan, period, newPriceId);
        return updated;
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
     * FUNC-02 FIX : résout un SubscriptionPlan depuis un priceId Stripe (reverse lookup)
     */
    public com.gedavocat.model.User.SubscriptionPlan getPlanFromPriceId(String priceId) {
        if (priceId == null || priceMap == null) return null;
        for (Map.Entry<String, String> entry : priceMap.entrySet()) {
            if (priceId.equals(entry.getValue())) {
                // key format: "PLAN_period"
                String planName = entry.getKey().split("_")[0];
                // Handle CABINET_PLUS (two-word plan)
                if (entry.getKey().startsWith("CABINET_PLUS")) {
                    planName = "CABINET_PLUS";
                }
                try {
                    return com.gedavocat.model.User.SubscriptionPlan.valueOf(planName);
                } catch (IllegalArgumentException e) {
                    log.warn("Plan inconnu dans le mapping : {}", planName);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Retourne la clé publique Stripe (pour le frontend)
     */
    public String getPublishableKey() {
        return stripePublishableKey;
    }
}
