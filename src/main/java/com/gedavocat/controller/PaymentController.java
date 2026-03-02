package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.PayPlugService;
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

    /**
     * Page de pricing (liste des plans)
     */
    @GetMapping("/pricing")
    public String pricing(Model model, Authentication authentication) {
        if (authentication != null) {
            User user = getCurrentUser(authentication);
            model.addAttribute("user", user);
            model.addAttribute("currentPlan", user.getSubscriptionPlan());
        }
        return "payment/pricing";
    }

    /**
     * Initier un paiement (redirection vers PayPlug)
     */
    @GetMapping("/checkout")
    public String checkout(
            @RequestParam String plan,
            @RequestParam(required = false, defaultValue = "monthly") String period,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);

            // Calculer le montant selon le plan et la période
            double amount = calculateAmount(plan, period);

            if (amount == 0) {
                redirectAttributes.addFlashAttribute("error", "Plan invalide");
                return "redirect:/payment/pricing";
            }

            // Créer le paiement PayPlug
            String paymentUrl = payPlugService.createPayment(user, plan, amount);

            // SEC FIX CTL-05 : validation de l'URL de redirection (open redirect)
            if (paymentUrl != null && isAllowedPaymentRedirect(paymentUrl)) {
                return "redirect:" + paymentUrl;
            } else if (paymentUrl != null) {
                log.warn("URL de paiement rejetée (domaine non autorisé) : {}", paymentUrl);
                redirectAttributes.addFlashAttribute("error", "Erreur lors de la création du paiement");
                return "redirect:/payment/pricing";
            } else {
                redirectAttributes.addFlashAttribute("error", "Erreur lors de la création du paiement");
                return "redirect:/payment/pricing";
            }

        } catch (Exception e) {
            log.error("Erreur lors du checkout paiement", e);
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue");
            return "redirect:/payment/pricing";
        }
    }

    /**
     * Page de succès après paiement
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
     * Annuler mon abonnement
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
     * Calculer le montant selon le plan et la période
     */
    private double calculateAmount(String planStr, String period) {
        try {
            User.SubscriptionPlan plan = User.SubscriptionPlan.valueOf(planStr);
            double monthlyAmount = plan.getPrice();

            // Si annuel, appliquer -20%
            if ("yearly".equals(period)) {
                return monthlyAmount * 12 * 0.8;
            }

            return monthlyAmount;
        } catch (IllegalArgumentException e) {
            return 0.0;
        }
    }

    /**
     * SEC FIX CTL-05/06 : Vérifie que l'URL de redirection de paiement
     * pointe vers un domaine autorisé (PayPlug ou Stripe uniquement).
     */
    private boolean isAllowedPaymentRedirect(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.endsWith(".payplug.com") || host.equals("payplug.com")
                || host.endsWith(".stripe.com") || host.equals("stripe.com");
        } catch (Exception e) {
            return false;
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
