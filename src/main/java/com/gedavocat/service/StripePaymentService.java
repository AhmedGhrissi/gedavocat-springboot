package com.gedavocat.service;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service de paiement avec Stripe
 * Stripe France - Solution de paiement en ligne française
 * https://stripe.com/fr
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StripePaymentService {
    
    @Value("${stripe.api.key:}")
    private String stripeApiKey;
    
    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;
    
    @Value("${app.subscription.solo.price:29.99}")
    private double soloPlanPrice;
    
    @Value("${app.subscription.cabinet.price:99.99}")
    private double cabinetPlanPrice;
    
    @Value("${app.subscription.enterprise.price:299.99}")
    private double enterprisePlanPrice;
    
    private final UserRepository userRepository;
    
    /**
     * Créer une session de paiement Checkout Stripe
     */
    public Map<String, Object> createCheckoutSession(
            String userId,
            String planType,
            String successUrl,
            String cancelUrl
    ) {
        try {
            // Configuration de Stripe (à faire avec la librairie Stripe)
            // Stripe.apiKey = stripeApiKey;
            
            double price = getPriceForPlan(planType);
            
            Map<String, Object> session = new HashMap<>();
            session.put("payment_method_types", new String[]{"card", "sepa_debit"});
            
            // Informations de ligne de commande
            Map<String, Object> lineItem = new HashMap<>();
            lineItem.put("price_data", Map.of(
                "currency", "eur",
                "product_data", Map.of(
                    "name", "GED Avocat - Plan " + planType,
                    "description", getDescriptionForPlan(planType)
                ),
                "unit_amount", (int)(price * 100), // Centimes
                "recurring", Map.of("interval", "month")
            ));
            lineItem.put("quantity", 1);
            
            session.put("line_items", new Object[]{lineItem});
            session.put("mode", "subscription");
            session.put("success_url", successUrl);
            session.put("cancel_url", cancelUrl);
            
            // Métadonnées
            session.put("metadata", Map.of(
                "user_id", userId,
                "plan_type", planType
            ));
            
            // En production, créer la session avec Stripe
            // Session stripeSession = Session.create(session);
            // return Map.of("id", stripeSession.getId(), "url", stripeSession.getUrl());
            
            log.info("Session de paiement créée pour l'utilisateur {} - Plan {}", userId, planType);
            
            // Simulation pour développement
            return Map.of(
                "id", "cs_test_" + System.currentTimeMillis(),
                "url", "https://checkout.stripe.com/pay/cs_test_simulation"
            );
            
        } catch (Exception e) {
            log.error("Erreur lors de la création de la session Stripe: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la création de la session de paiement", e);
        }
    }
    
    /**
     * Créer un portail client pour gérer l'abonnement
     */
    public Map<String, Object> createCustomerPortal(String userId, String returnUrl) {
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            // En production: créer le portail avec Stripe
            // BillingPortalSession portal = BillingPortalSession.create(params);
            
            log.info("Portail client créé pour l'utilisateur {}", userId);
            
            return Map.of(
                "url", "https://billing.stripe.com/session/test_simulation"
            );
            
        } catch (Exception e) {
            log.error("Erreur lors de la création du portail: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la création du portail client", e);
        }
    }
    
    /**
     * Gérer les webhooks Stripe
     */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        try {
            // En production: vérifier la signature du webhook
            // Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            
            // Simuler le parsing de l'événement
            Map<String, Object> event = parseWebhookPayload(payload);
            String eventType = (String) event.get("type");
            
            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutCompleted(event);
                    break;
                    
                case "customer.subscription.created":
                    handleSubscriptionCreated(event);
                    break;
                    
                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event);
                    break;
                    
                case "customer.subscription.deleted":
                    handleSubscriptionCancelled(event);
                    break;
                    
                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(event);
                    break;
                    
                case "invoice.payment_failed":
                    handlePaymentFailed(event);
                    break;
                    
                default:
                    log.info("Événement Stripe non géré: {}", eventType);
            }
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement du webhook: {}", e.getMessage());
            throw new RuntimeException("Erreur webhook", e);
        }
    }
    
    /**
     * Activer l'abonnement après paiement réussi
     */
    @Transactional
    protected void handleCheckoutCompleted(Map<String, Object> event) {
        try {
            Map<String, Object> metadata = (Map<String, Object>) event.get("metadata");
            String userId = (String) metadata.get("user_id");
            String planType = (String) metadata.get("plan_type");
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            // Activer l'abonnement
            user.setSubscriptionPlan(User.SubscriptionPlan.valueOf(planType.toUpperCase()));
            user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
            user.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));
            
            // Définir les limites selon le plan
            switch (planType.toLowerCase()) {
                case "solo":
                    user.setMaxClients(10);
                    break;
                case "cabinet":
                    user.setMaxClients(100);
                    break;
                case "enterprise":
                    user.setMaxClients(Integer.MAX_VALUE);
                    break;
            }
            
            userRepository.save(user);
            
            log.info("Abonnement activé pour l'utilisateur {} - Plan {}", userId, planType);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'activation de l'abonnement: {}", e.getMessage());
        }
    }
    
    /**
     * Gérer la création d'abonnement
     */
    protected void handleSubscriptionCreated(Map<String, Object> event) {
        log.info("Nouvel abonnement créé");
    }
    
    /**
     * Gérer la mise à jour d'abonnement
     */
    protected void handleSubscriptionUpdated(Map<String, Object> event) {
        log.info("Abonnement mis à jour");
    }
    
    /**
     * Gérer l'annulation d'abonnement
     */
    @Transactional
    protected void handleSubscriptionCancelled(Map<String, Object> event) {
        try {
            // Récupérer l'ID utilisateur depuis les métadonnées
            Map<String, Object> metadata = (Map<String, Object>) event.get("metadata");
            String userId = (String) metadata.get("user_id");
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            user.setSubscriptionStatus(User.SubscriptionStatus.CANCELLED);
            userRepository.save(user);
            
            log.info("Abonnement annulé pour l'utilisateur {}", userId);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'annulation: {}", e.getMessage());
        }
    }
    
    /**
     * Gérer le paiement réussi
     */
    protected void handlePaymentSucceeded(Map<String, Object> event) {
        log.info("Paiement réussi");
    }
    
    /**
     * Gérer l'échec de paiement
     */
    protected void handlePaymentFailed(Map<String, Object> event) {
        log.error("Échec de paiement");
    }
    
    /**
     * Parser le payload du webhook (simulation)
     */
    private Map<String, Object> parseWebhookPayload(String payload) {
        // En production: parser le JSON réel
        return new HashMap<>();
    }
    
    /**
     * Obtenir le prix d'un plan
     */
    private double getPriceForPlan(String planType) {
        switch (planType.toLowerCase()) {
            case "solo": return soloPlanPrice;
            case "cabinet": return cabinetPlanPrice;
            case "enterprise": return enterprisePlanPrice;
            default: throw new IllegalArgumentException("Plan inconnu: " + planType);
        }
    }
    
    /**
     * Obtenir la description d'un plan
     */
    private String getDescriptionForPlan(String planType) {
        switch (planType.toLowerCase()) {
            case "solo":
                return "Plan Solo - Jusqu'à 10 clients - 10 GB de stockage";
            case "cabinet":
                return "Plan Cabinet - Jusqu'à 100 clients - 100 GB de stockage";
            case "enterprise":
                return "Plan Enterprise - Clients illimités - 1 TB de stockage";
            default:
                return "Abonnement GED Avocat";
        }
    }
    
    /**
     * Vérifier si Stripe est configuré
     */
    public boolean isConfigured() {
        return stripeApiKey != null && !stripeApiKey.isEmpty();
    }
}
