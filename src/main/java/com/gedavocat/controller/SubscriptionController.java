package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

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
        return "payement/pricing";
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
        return "payement/success";
    }

    /**
     * Page d'annulation
     */
    @GetMapping("/cancel")
    public String cancelPayment(Model model) {
        model.addAttribute("message", "Vous avez annulé le processus de paiement");
        return "payement/cancel";
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
        
        return "payement/manage";
    }

    /**
     * Annuler l'abonnement
     */
    @PostMapping("/cancel-subscription")
    public String cancelSubscription(
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            
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
            
            log.info("✅ Abonnement activé via webhook pour: {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement de checkout.session.completed: {}", e.getMessage());
        }
    }

    /**
     * Traite l'événement subscription.updated
     */
    private void handleSubscriptionUpdated(Event event) {
        log.info("Abonnement mis à jour");
        // TODO: Implémenter la logique de mise à jour
    }

    /**
     * Traite l'événement subscription.deleted
     */
    private void handleSubscriptionDeleted(Event event) {
        log.info("Abonnement supprimé");
        // TODO: Implémenter la logique de suppression
    }

    /**
     * Traite l'événement invoice.payment_succeeded
     */
    private void handleInvoicePaymentSucceeded(Event event) {
        log.info("Paiement de facture réussi");
        // TODO: Implémenter la logique de renouvellement
    }

    /**
     * Traite l'événement invoice.payment_failed
     */
    private void handleInvoicePaymentFailed(Event event) {
        log.error("Échec du paiement de facture");
        // TODO: Implémenter la logique d'échec de paiement
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
        
        // Calculer la date de fin selon la période
        if ("yearly".equals(period)) {
            user.setSubscriptionEndsAt(LocalDateTime.now().plusYears(1));
        } else {
            user.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));
        }
        
        userRepository.save(user);
        
        log.info("Abonnement {} activé pour {} jusqu'au {}", 
            subscriptionPlan, user.getEmail(), user.getSubscriptionEndsAt());
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
