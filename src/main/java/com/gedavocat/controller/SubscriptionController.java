package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * Contrôleur pour la gestion des abonnements avec Stripe
 */
@Controller
@RequestMapping("/subscription")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final StripeService stripeService;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    /**
     * Page de choix d'abonnement (pricing)
     */
    @GetMapping("/pricing")
    public String pricingPage(Authentication authentication, Model model) {
        if (authentication != null) {
            User user = getCurrentUser(authentication);
            model.addAttribute("user", user);
            model.addAttribute("currentPlan", user.getSubscriptionPlan());
        }
        model.addAttribute("stripePublishableKey", stripeService.getPublishableKey());
        return "subscription/pricing";
    }

    /**
     * Créer une session de paiement Stripe Checkout
     */
    @GetMapping("/checkout")
    public String createCheckout(
            @RequestParam String plan,
            @RequestParam(required = false, defaultValue = "monthly") String period,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (!stripeService.isConfigured()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Stripe n'est pas configuré. Veuillez configurer vos clés API.");
                return "redirect:/subscription/pricing";
            }

            User user = getCurrentUser(authentication);

            // Créer la session Stripe Checkout
            String checkoutUrl = stripeService.createCheckoutSession(user, plan, period);

            if (checkoutUrl != null) {
                log.info("Redirection vers Stripe Checkout pour l'utilisateur: {}", user.getEmail());
                return "redirect:" + checkoutUrl;
            } else {
                redirectAttributes.addFlashAttribute("error", "Erreur lors de la création du paiement");
                return "redirect:/subscription/pricing";
            }

        } catch (StripeException e) {
            log.error("Erreur Stripe lors du checkout: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", 
                "Erreur de paiement: " + e.getUserMessage());
            return "redirect:/subscription/pricing";
        } catch (Exception e) {
            log.error("Erreur inattendue lors du checkout: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue");
            return "redirect:/subscription/pricing";
        }
    }

    /**
     * Page de succès après paiement
     */
    @GetMapping("/success")
    public String paymentSuccess(
            @RequestParam(required = false) String session_id,
            Model model,
            Authentication authentication
    ) {
        if (session_id != null && authentication != null) {
            try {
                Session session = stripeService.getSession(session_id);
                
                if ("complete".equals(session.getStatus())) {
                    User user = getCurrentUser(authentication);
                    
                    // Récupérer les métadonnées
                    String plan = session.getMetadata().get("plan");
                    String period = session.getMetadata().get("period");
                    
                    // Activer l'abonnement
                    activateSubscription(user, plan, period, session.getSubscription());
                    
                    // Sauvegarder l'ID client Stripe
                    if (session.getCustomer() != null && (user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty())) {
                        user.setStripeCustomerId(session.getCustomer());
                        userRepository.save(user);
                    }
                    
                    model.addAttribute("user", user);
                    model.addAttribute("plan", user.getSubscriptionPlan());
                    model.addAttribute("success", true);
                    model.addAttribute("message", "Votre abonnement a été activé avec succès !");
                } else {
                    model.addAttribute("error", "Le paiement est en cours de traitement");
                }
            } catch (StripeException e) {
                log.error("Erreur lors de la récupération de la session: {}", e.getMessage());
                model.addAttribute("error", "Erreur lors de la vérification du paiement");
            }
        }
        return "payment/success";
    }

    /**
     * Page d'annulation
     */
    @GetMapping("/cancel")
    public String cancelPayment(Model model) {
        model.addAttribute("message", "Vous avez annulé le processus de paiement");
        return "payment/cancel";
    }

    /**
     * Gérer mon abonnement
     */
    @GetMapping("/manage")
    public String manageSubscription(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        
        model.addAttribute("user", user);
        model.addAttribute("plan", user.getSubscriptionPlan());
        model.addAttribute("status", user.getSubscriptionStatus());
        model.addAttribute("endDate", user.getSubscriptionEndsAt());
        
        return "payment/manage";
    }

    /**
     * Page de changement de plan (pour abonnés connectés)
     */
    @GetMapping("/change-plan")
    public String changePlanPage(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);

        int currentClients = (int) clientRepository.countByLawyerId(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("currentPlan", user.getSubscriptionPlan());
        model.addAttribute("currentStatus", user.getSubscriptionStatus());
        model.addAttribute("endDate", user.getSubscriptionEndsAt());
        model.addAttribute("currentClients", currentClients);
        model.addAttribute("plans", User.SubscriptionPlan.values());
        model.addAttribute("stripePublishableKey", stripeService.getPublishableKey());

        return "subscription/change-plan";
    }

    /**
     * Valider un changement de plan (POST)
     * - Upgrade : redirige vers Stripe Checkout immédiatement
     * - Downgrade : vérifie les limites puis planifie à la fin de la période
     */
    @PostMapping("/change-plan")
    public String changePlan(
            @RequestParam String plan,
            @RequestParam(required = false, defaultValue = "monthly") String period,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            User.SubscriptionPlan newPlan = User.SubscriptionPlan.valueOf(plan.toUpperCase());
            User.SubscriptionPlan currentPlan = user.getSubscriptionPlan();

            if (newPlan == currentPlan) {
                redirectAttributes.addFlashAttribute("error", "Vous êtes déjà sur ce plan.");
                return "redirect:/subscription/change-plan";
            }

            boolean isUpgrade = newPlan.getPrice() > currentPlan.getPrice();
            int currentClients = (int) clientRepository.countByLawyerId(user.getId());

            if (!isUpgrade) {
                // Downgrade : vérifier les limites
                if (currentClients > newPlan.getMaxClients()) {
                    redirectAttributes.addFlashAttribute("error",
                        "Impossible de passer au plan " + newPlan.getDisplayName()
                        + " : vous avez " + currentClients + " clients actifs, "
                        + "mais ce plan est limité à " + newPlan.getMaxClients()
                        + " clients. Veuillez d'abord archiver ou supprimer des clients.");
                    return "redirect:/subscription/change-plan";
                }
            }

            if (!stripeService.isConfigured()) {
                redirectAttributes.addFlashAttribute("error",
                    "Le système de paiement n'est pas configuré.");
                return "redirect:/subscription/change-plan";
            }

            // Si l'utilisateur a déjà un abonnement Stripe, modifier le plan directement
            String existingSubId = user.getStripeSubscriptionId();
            if (existingSubId != null && !existingSubId.isBlank()) {
                try {
                    com.stripe.model.Subscription updatedSub = stripeService.updateSubscriptionPlan(
                            existingSubId, plan, period);
                    if (updatedSub != null) {
                        // Mettre à jour le plan en base
                        user.setSubscriptionPlan(newPlan);
                        user.setMaxClients(newPlan.getMaxClients());
                        userRepository.save(user);
                        log.info("Plan modifié via Stripe {} → {} pour {}", currentPlan, newPlan, user.getEmail());
                        redirectAttributes.addFlashAttribute("message",
                            "Votre plan a été changé avec succès vers " + newPlan.getDisplayName() + " !");
                        return "redirect:/subscription/change-plan";
                    }
                } catch (StripeException se) {
                    log.warn("Impossible de mettre à jour l'abonnement Stripe {} : {}", existingSubId, se.getMessage());
                    // L'abonnement est peut-être annulé/expiré — on crée un nouveau checkout
                }
            }

            // Pas d'abonnement existant ou mise à jour échouée → nouveau checkout
            String checkoutUrl = stripeService.createCheckoutSession(user, plan, period);
            if (checkoutUrl != null) {
                log.info("Changement de plan {} → {} pour {} (nouveau checkout)", currentPlan, newPlan, user.getEmail());
                return "redirect:" + checkoutUrl;
            } else {
                redirectAttributes.addFlashAttribute("error", "Erreur lors de la création du paiement");
                return "redirect:/subscription/change-plan";
            }

        } catch (Exception e) {
            log.error("Erreur lors du changement de plan: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue : " + e.getMessage());
            return "redirect:/subscription/change-plan";
        }
    }

    /**
     * Annuler l'abonnement (utilise l'API Stripe pour annuler en fin de période)
     */
    @PostMapping("/cancel-subscription")
    public String cancelSubscription(
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            
            // 1. Annuler côté Stripe (cancel_at_period_end = true)
            if (stripeService.isConfigured() && user.getStripeCustomerId() != null) {
                try {
                    var cancelled = stripeService.cancelSubscriptionAtPeriodEnd(user.getStripeCustomerId());
                    if (cancelled != null) {
                        log.info("Abonnement Stripe annulé en fin de période pour: {}", user.getEmail());
                    } else {
                        log.warn("Aucun abonnement Stripe actif trouvé pour: {}", user.getEmail());
                    }
                } catch (Exception stripeEx) {
                    log.error("Erreur Stripe lors de l'annulation pour {}: {}", 
                              user.getEmail(), stripeEx.getMessage());
                    // On continue quand même pour mettre à jour le statut local
                }
            }
            
            // 2. Mettre à jour le statut local
            user.setSubscriptionStatus(User.SubscriptionStatus.CANCELLED);
            userRepository.save(user);
            
            log.info("Abonnement annulé pour l'utilisateur: {}", user.getEmail());
            
            redirectAttributes.addFlashAttribute("message", 
                "Votre abonnement a été annulé. Vous conservez l'accès jusqu'à la fin de la période payée.");
            
        } catch (Exception e) {
            log.error("Erreur lors de l'annulation de l'abonnement: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'annulation");
        }
        
        return "redirect:/subscription/manage";
    }

    /**
     * Webhook Stripe - Reçoit les événements de Stripe
     */
    @PostMapping("/webhook")
    @ResponseBody
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        log.info("Webhook Stripe reçu");
        
        try {
            // Vérifier la signature du webhook
            Event event = stripeService.constructWebhookEvent(payload, sigHeader);
            
            log.info("Événement Stripe: {}", event.getType());
            
            // Traiter l'événement selon son type
            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                    
                case "customer.subscription.created":
                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event);
                    break;
                    
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);
                    break;
                    
                case "invoice.payment_succeeded":
                    handleInvoicePaymentSucceeded(event);
                    break;
                    
                case "invoice.payment_failed":
                    handleInvoicePaymentFailed(event);
                    break;
                    
                default:
                    log.info("Événement non géré: {}", event.getType());
            }
            
            return ResponseEntity.ok("Webhook traité");
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement du webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Erreur: " + e.getMessage());
        }
    }

    /**
     * Traite l'événement checkout.session.completed
     */
    private void handleCheckoutSessionCompleted(Event event) {
        try {
            Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();
            
            String userId = session.getMetadata().get("user_id");
            String plan = session.getMetadata().get("plan");
            String period = session.getMetadata().get("period");
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));
            
            activateSubscription(user, plan, period, session.getSubscription());
            
            // Sauvegarder l'ID client Stripe pour les webhooks futurs
            if (session.getCustomer() != null && (user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty())) {
                user.setStripeCustomerId(session.getCustomer());
                userRepository.save(user);
            }
            
            log.info("✅ Abonnement activé via webhook pour: {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement de checkout.session.completed: {}", e.getMessage());
        }
    }

    /**
     * Traite l'événement subscription.updated
     */
    private void handleSubscriptionUpdated(Event event) {
        try {
            log.info("Abonnement mis à jour");
            com.stripe.model.Subscription subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
            
            if (subscription != null) {
                String customerId = subscription.getCustomer();
                String status = subscription.getStatus();
                
                // Trouver l'utilisateur par son Stripe Customer ID
                User user = userRepository.findByStripeCustomerId(customerId)
                    .orElse(null);
                
                if (user != null) {
                    // Mettre à jour le statut selon Stripe
                    if ("active".equals(status)) {
                        user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
                    } else if ("canceled".equals(status) || "unpaid".equals(status)) {
                        user.setSubscriptionStatus(User.SubscriptionStatus.INACTIVE);
                    }
                    userRepository.save(user);
                    log.info("✅ Statut d'abonnement mis à jour pour: {}", user.getEmail());
                } else {
                    log.warn("⚠️ Aucun utilisateur trouvé pour le Stripe customer: {}", customerId);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de subscription.updated: {}", e.getMessage());
        }
    }

    /**
     * Traite l'événement subscription.deleted
     */
    private void handleSubscriptionDeleted(Event event) {
        try {
            log.info("Abonnement supprimé");
            com.stripe.model.Subscription subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
            
            if (subscription != null) {
                // Trouver l'utilisateur et désactiver son abonnement
                String customerId = subscription.getCustomer();
                User user = userRepository.findByStripeCustomerId(customerId)
                    .orElse(null);
                
                if (user != null) {
                    user.setSubscriptionStatus(User.SubscriptionStatus.INACTIVE);
                    user.setSubscriptionEndsAt(LocalDateTime.now());
                    userRepository.save(user);
                    log.info("✅ Abonnement désactivé pour: {}", user.getEmail());
                } else {
                    log.warn("⚠️ Aucun utilisateur trouvé pour le Stripe customer: {}", customerId);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de subscription.deleted: {}", e.getMessage());
        }
    }

    /**
     * Traite l'événement invoice.payment_succeeded
     */
    private void handleInvoicePaymentSucceeded(Event event) {
        try {
            log.info("Paiement de facture réussi");
            com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
            
            if (invoice != null && invoice.getSubscription() != null) {
                // Renouveler l'abonnement
                String customerId = invoice.getCustomer();
                User user = userRepository.findByStripeCustomerId(customerId)
                    .orElse(null);
                
                if (user != null) {
                    // Prolonger la date de fin d'abonnement
                    LocalDateTime newEndDate = user.getSubscriptionEndsAt() != null 
                        ? user.getSubscriptionEndsAt().plusMonths(1) 
                        : LocalDateTime.now().plusMonths(1);
                    
                    user.setSubscriptionEndsAt(newEndDate);
                    user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
                    userRepository.save(user);
                    log.info("✅ Abonnement renouvelé pour: {} jusqu'au {}", user.getEmail(), newEndDate);
                } else {
                    log.warn("⚠️ Aucun utilisateur trouvé pour le Stripe customer: {}", customerId);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de invoice.payment_succeeded: {}", e.getMessage());
        }
    }

    /**
     * Traite l'événement invoice.payment_failed
     */
    private void handleInvoicePaymentFailed(Event event) {
        try {
            log.error("Échec du paiement de facture");
            com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
            
            if (invoice != null) {
                // Gérer l'échec de paiement
                String customerId = invoice.getCustomer();
                User user = userRepository.findByStripeCustomerId(customerId)
                    .orElse(null);
                
                if (user != null) {
                    // Marquer l'abonnement comme impayé
                    user.setSubscriptionStatus(User.SubscriptionStatus.PAYMENT_FAILED);
                    userRepository.save(user);
                    
                    log.warn("⚠️ Paiement échoué pour: {}. Abonnement suspendu.", user.getEmail());
                    
                } else {
                    log.warn("⚠️ Aucun utilisateur trouvé pour le Stripe customer: {}", customerId);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de invoice.payment_failed: {}", e.getMessage());
        }
    }

    /**
     * Active l'abonnement d'un utilisateur
     */
    private void activateSubscription(User user, String plan, String period, String subscriptionId) {
        User.SubscriptionPlan subscriptionPlan = User.SubscriptionPlan.valueOf(plan.toUpperCase());
        
        user.setSubscriptionPlan(subscriptionPlan);
        user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        user.setMaxClients(subscriptionPlan.getMaxClients());
        user.setSubscriptionStartDate(LocalDateTime.now());
        
        // Sauvegarder l'ID d'abonnement Stripe pour pouvoir le modifier plus tard
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            user.setStripeSubscriptionId(subscriptionId);
        }
        
        // Calculer la date de fin selon la période
        if ("yearly".equals(period)) {
            user.setSubscriptionEndsAt(LocalDateTime.now().plusYears(1));
        } else {
            user.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));
        }
        
        userRepository.save(user);
        
        log.info("Abonnement {} activé pour {} jusqu'au {} (stripeSubId={})", 
            subscriptionPlan, user.getEmail(), user.getSubscriptionEndsAt(), subscriptionId);
    }

    /**
     * Récupère l'utilisateur courant
     */
    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
