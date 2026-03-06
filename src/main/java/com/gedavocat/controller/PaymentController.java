package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.PayPlugService;
import com.gedavocat.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Contrôleur de gestion des paiements et abonnements
 * Compatible avec le modèle User existant
 */
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PayPlugService payPlugService;
    private final UserRepository userRepository;
    private final StripeService stripeService;

    /**
     * CRIT-03 FIX : /payment/pricing redirige vers /subscription/pricing (Stripe est le système principal)
     */
    @GetMapping("/pricing")
    public String pricing() {
        return "redirect:/subscription/pricing";
    }

    /**
     * CRIT-03 FIX : /payment/checkout redirige vers /subscription/checkout
     */
    @GetMapping("/checkout")
    public String checkout(
            @RequestParam(required = false) String plan,
            @RequestParam(required = false, defaultValue = "monthly") String period
    ) {
        return "redirect:/subscription/checkout?plan=" + (plan != null ? plan : "") + "&period=" + period;
    }

    /**
     * FUNC-04 FIX : Page de succès — active réellement l'abonnement 
     */
    @GetMapping("/success")
    public String success(
            @RequestParam(required = false) String payment_id,
            Model model,
            Authentication authentication
    ) {
        if (payment_id != null && authentication != null) {
            boolean isPaid = payPlugService.verifyPayment(payment_id);

            if (isPaid) {
                User user = getCurrentUser(authentication);
                // FUNC-04 FIX : activer l'abonnement si pas encore fait
                if (user.getSubscriptionStatus() != User.SubscriptionStatus.ACTIVE) {
                    user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
                    if (user.getSubscriptionStartDate() == null) {
                        user.setSubscriptionStartDate(LocalDateTime.now());
                    }
                    if (user.getSubscriptionEndsAt() == null) {
                        user.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));
                    }
                    userRepository.save(user);
                    log.info("✅ Abonnement activé via PayPlug pour {}", user.getEmail());
                }
                model.addAttribute("user", user);
                model.addAttribute("plan", user.getSubscriptionPlan());
                model.addAttribute("success", true);
            } else {
                model.addAttribute("error", "Le paiement n'a pas pu être vérifié");
            }
        }

        return "payment/success";
    }

    /**
     * Page d'annulation
     */
    @GetMapping("/cancel")
    public String cancel(Model model) {
        return "payment/cancel";
    }

    /**
     * Webhook PayPlug (appelé automatiquement)
     * Vérifie la signature HMAC-SHA256 du payload pour prévenir les falsifications.
     */
    @PostMapping("/webhook")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> webhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "PayPlug-Signature", required = false) String signature
    ) {
        try {
            // B4 FIX : Vérification de signature PayPlug
            if (!payPlugService.verifyWebhookSignature(rawPayload, signature)) {
                return ResponseEntity.status(403).body("Invalid signature");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawPayload, Map.class);
            Boolean isPaid = (Boolean) payload.get("is_paid");

            if (Boolean.TRUE.equals(isPaid)) {
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = (Map<String, String>) payload.get("metadata");
                String userId = metadata.get("user_id");
                String planStr = metadata.get("plan");

                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

                // ✅ Conversion String → Enum SubscriptionPlan
                User.SubscriptionPlan plan = User.SubscriptionPlan.valueOf(planStr);

                user.setSubscriptionPlan(plan);
                user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
                user.setMaxClients(plan.getMaxClients());

                // ✅ Utiliser subscriptionEndsAt au lieu de subscriptionEndDate
                user.setSubscriptionEndsAt(LocalDateTime.now().plusMonths(1));

                userRepository.save(user);

                log.info("Abonnement activé pour {} - Plan: {}", user.getEmail(), plan);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Erreur lors du traitement du webhook PayPlug", e);
            return ResponseEntity.status(500).body("ERROR");
        }
    }

    /**
     * Gérer mon abonnement
     */
    @GetMapping("/manage")
    @Transactional(readOnly = true)
    public String manage(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);

        model.addAttribute("user", user);
        model.addAttribute("plan", user.getSubscriptionPlan());
        model.addAttribute("status", user.getSubscriptionStatus());
        model.addAttribute("endDate", user.getSubscriptionEndsAt());

        return "payment/manage";
    }

    /**
     * CRIT-04 FIX : Annuler l'abonnement — appelle aussi l'API Stripe
     */
    @PostMapping("/cancel-subscription")
    public String cancelSubscription(
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);

            // CRIT-04 FIX : annuler côté Stripe si un subscriptionId existe
            if (user.getStripeSubscriptionId() != null && !user.getStripeSubscriptionId().isBlank()) {
                try {
                    stripeService.cancelSubscriptionByIdAtPeriodEnd(user.getStripeSubscriptionId());
                    log.info("✅ Abonnement Stripe annulé (fin de période) pour {}", user.getEmail());
                } catch (Exception stripeEx) {
                    log.error("Erreur annulation Stripe: {}", stripeEx.getMessage());
                }
            } else if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
                try {
                    stripeService.cancelSubscriptionAtPeriodEnd(user.getStripeCustomerId());
                    log.info("✅ Abonnement Stripe annulé par customerId pour {}", user.getEmail());
                } catch (Exception stripeEx) {
                    log.error("Erreur annulation Stripe par customer: {}", stripeEx.getMessage());
                }
            }

            user.setSubscriptionStatus(User.SubscriptionStatus.CANCELLED);
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("message",
                "Abonnement annulé. Vous pouvez continuer jusqu'au " +
                user.getSubscriptionEndsAt());

            return "redirect:/payment/manage";

        } catch (Exception e) {
            log.error("Erreur lors de l'annulation de l'abonnement", e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'annulation");
            return "redirect:/payment/manage";
        }
    }

    /**
     * Récupérer l'utilisateur connecté
     */
    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
