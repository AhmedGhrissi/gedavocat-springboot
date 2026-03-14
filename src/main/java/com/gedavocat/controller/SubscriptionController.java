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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrôleur pour la gestion des abonnements avec Stripe
 */
@Controller
@RequestMapping("/subscription")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class SubscriptionController {

    private final StripeService stripeService;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    /** Idempotence : Stripe session_ids déjà traités (CRIT-01 FIX) */
    private final Set<String> processedSessionIds = ConcurrentHashMap.newKeySet();
    /** Idempotence webhook : event IDs déjà traités (CRIT-05 FIX) */
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /** Mémoire: nettoyage périodique des sets d'idempotence */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000) // 1 heure
    public void cleanupIdempotencySets() {
        if (processedSessionIds.size() > 1000) processedSessionIds.clear();
        if (processedEventIds.size() > 1000) processedEventIds.clear();
    }

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
     * FUNC-03 FIX : empêche la double souscription
     */
    @GetMapping("/checkout")
    public String createCheckout(
            @RequestParam(required = false) String plan,
            @RequestParam(required = false, defaultValue = "monthly") String period,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            // FUNC-08 FIX : valider le plan
            if (plan == null || plan.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner un plan.");
                return "redirect:/subscription/pricing";
            }
            try {
                User.SubscriptionPlan.valueOf(plan.toUpperCase());
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", "Plan invalide. Veuillez en choisir un.");
                return "redirect:/subscription/pricing";
            }

            if (!stripeService.isConfigured()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Stripe n'est pas configuré. Veuillez configurer vos clés API.");
                return "redirect:/subscription/pricing";
            }

            User user = getCurrentUser(authentication);

            // FUNC-03 FIX : empêcher la double souscription
            if (user.hasActiveSubscription()) {
                redirectAttributes.addFlashAttribute("error", "Vous avez déjà un abonnement actif. Utilisez la page de changement de plan.");
                return "redirect:/subscription/manage";
            }

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
     * Fonctionne avec ou sans authentification (session HTTP peut expirer pendant le paiement Stripe)
     * Si non authentifié, utilise les métadonnées Stripe (user_id) pour retrouver l'utilisateur
     */
    @GetMapping("/success")
    public String paymentSuccess(
            @RequestParam(required = false) String session_id,
            Model model,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        log.info("🎯 Accès à /subscription/success - session_id={}, authenticated={}", 
                session_id, authentication != null);
        
        if (session_id == null || session_id.isBlank()) {
            log.warn("⚠️ Aucun session_id fourni");
            model.addAttribute("error", "Session de paiement invalide");
            return "payment/success";
        }
        
        try {
            // CRIT-01 FIX : idempotence — ne pas retraiter une session déjà activée
            if (processedSessionIds.contains(session_id)) {
                log.info("♻️ Session déjà traitée (idempotence): {}", session_id);
                User user = resolveUser(authentication, session_id);
                if (user != null) {
                    model.addAttribute("user", user);
                    model.addAttribute("plan", user.getSubscriptionPlan());
                }
                model.addAttribute("success", true);
                model.addAttribute("message", "Votre abonnement est déjà actif !");
                logoutUser(httpRequest);
                return "payment/success";
            }

            log.info("📡 Récupération de la session Stripe: {}", session_id);
            Session session = stripeService.getSession(session_id);
            log.info("✅ Session récupérée - status={}, metadata={}", 
                    session.getStatus(), session.getMetadata());
            
            if ("complete".equals(session.getStatus()) || "paid".equals(session.getPaymentStatus())) {
                // Résoudre l'utilisateur : via auth si connecté, sinon via metadata Stripe
                User user = resolveUser(authentication, session, session_id);
                
                if (user == null) {
                    log.error("❌ Impossible de résoudre l'utilisateur pour session_id: {}", session_id);
                    log.error("   - Metadata: {}", session.getMetadata());
                    log.error("   - Customer: {}", session.getCustomer());
                    log.error("   - Customer email: {}", session.getCustomerEmail());
                    model.addAttribute("error", "Utilisateur non trouvé. Veuillez vous connecter et vérifier votre espace abonné.");
                    return "payment/success";
                }

                log.info("👤 Utilisateur résolu: {} (id={})", user.getEmail(), user.getId());

                // CRIT-01 FIX : ne pas réactiver un abonnement déjà actif
                if (user.hasActiveSubscription() && user.getSubscriptionStatus() == User.SubscriptionStatus.ACTIVE) {
                    log.info("ℹ️ Abonnement déjà actif pour: {}", user.getEmail());
                    model.addAttribute("user", user);
                    model.addAttribute("plan", user.getSubscriptionPlan());
                    model.addAttribute("success", true);
                    model.addAttribute("message", "Votre abonnement est déjà actif !");
                    processedSessionIds.add(session_id);
                    logoutUser(httpRequest);
                    return "payment/success";
                }
                
                // Récupérer les métadonnées
                String plan = session.getMetadata() != null ? session.getMetadata().get("plan") : null;
                String period = session.getMetadata() != null ? session.getMetadata().get("period") : null;
                
                log.info("💳 Activation de l'abonnement - plan={}, period={}", plan, period);
                
                // Activer l'abonnement
                activateSubscription(user, plan, period, session.getSubscription());
                processedSessionIds.add(session_id);
                
                // Sauvegarder l'ID client Stripe
                if (session.getCustomer() != null && (user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty())) {
                    user.setStripeCustomerId(session.getCustomer());
                    userRepository.save(user);
                    log.info("💾 Stripe Customer ID sauvegardé: {}", session.getCustomer());
                }
                
                model.addAttribute("user", user);
                model.addAttribute("plan", user.getSubscriptionPlan());
                model.addAttribute("success", true);
                model.addAttribute("message", "Votre abonnement a été activé avec succès !");
                log.info("✅ Abonnement activé via success page pour: {} (auth={})",
                    user.getEmail(), authentication != null);
                logoutUser(httpRequest);
            } else {
                log.warn("⚠️ Statut de session non complete: {} (payment_status={})", 
                        session.getStatus(), session.getPaymentStatus());
                model.addAttribute("error", "Le paiement est en cours de traitement. Veuillez patienter quelques instants.");
            }
        } catch (StripeException e) {
            log.error("❌ Erreur Stripe lors de la récupération de la session: {}", e.getMessage(), e);
            model.addAttribute("error", "Erreur lors de la vérification du paiement. Veuillez contacter le support.");
        } catch (Exception e) {
            log.error("❌ Erreur inattendue lors du traitement du succès de paiement: {}", e.getMessage(), e);
            model.addAttribute("error", "Une erreur technique est survenue. Votre paiement a peut-être été traité. Veuillez vérifier votre espace abonné.");
        }
        
        return "payment/success";
    }

    /**
     * Déconnecte l'utilisateur (supprime session + contexte Spring Security).
     * Appelé après affichage de la page success pour forcer un login conscient.
     */
    private void logoutUser(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    /**
     * Résout l'utilisateur à partir de l'authentification ou des métadonnées Stripe
     */
    private User resolveUser(Authentication authentication, Session session, String sessionId) {
        // 1. Si authentifié, utiliser l'auth
        if (authentication != null) {
            try {
                return getCurrentUser(authentication);
            } catch (Exception e) {
                log.warn("Impossible de résoudre l'utilisateur via l'authentification: {}", e.getMessage());
            }
        }
        // 2. Sinon, utiliser les métadonnées Stripe (user_id)
        return resolveUserFromStripeSession(session);
    }

    /**
     * Résout l'utilisateur à partir d'un session_id déjà traité (idempotence)
     */
    private User resolveUser(Authentication authentication, String sessionId) {
        if (authentication != null) {
            try {
                return getCurrentUser(authentication);
            } catch (Exception e) {
                log.warn("Impossible de résoudre l'utilisateur via l'authentification: {}", e.getMessage());
            }
        }
        // Pour les sessions déjà traitées, essayer de récupérer depuis Stripe
        try {
            Session session = stripeService.getSession(sessionId);
            return resolveUserFromStripeSession(session);
        } catch (Exception e) {
            log.warn("Impossible de récupérer la session Stripe pour idempotence: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Résout l'utilisateur à partir des métadonnées d'une session Stripe
     */
    private User resolveUserFromStripeSession(Session session) {
        try {
            log.debug("🔍 Résolution utilisateur depuis session Stripe...");
            
            // Vérifier si les métadonnées existent
            if (session.getMetadata() != null && !session.getMetadata().isEmpty()) {
                // 1. Essayer avec user_id
                String userId = session.getMetadata().get("user_id");
                if (userId != null && !userId.isBlank()) {
                    log.debug("   Tentative avec user_id: {}", userId);
                    var user = userRepository.findById(userId);
                    if (user.isPresent()) {
                        log.info("✅ Utilisateur trouvé via user_id: {}", user.get().getEmail());
                        return user.get();
                    }
                }
                
                // 2. Fallback : utiliser l'email des métadonnées
                String userEmail = session.getMetadata().get("user_email");
                if (userEmail != null && !userEmail.isBlank()) {
                    log.debug("   Tentative avec user_email: {}", userEmail);
                    var user = userRepository.findByEmail(userEmail);
                    if (user.isPresent()) {
                        log.info("✅ Utilisateur trouvé via user_email: {}", userEmail);
                        return user.get();
                    }
                }
            } else {
                log.warn("⚠️ Aucune métadonnée dans la session Stripe");
            }
            
            // 3. Dernier fallback : utiliser le customer_email de la session Stripe
            String customerEmail = session.getCustomerEmail();
            if (customerEmail != null && !customerEmail.isBlank()) {
                log.debug("   Tentative avec customer_email: {}", customerEmail);
                var user = userRepository.findByEmail(customerEmail);
                if (user.isPresent()) {
                    log.info("✅ Utilisateur trouvé via customer_email: {}", customerEmail);
                    return user.get();
                }
            }
            
            log.error("❌ Impossible de résoudre l'utilisateur - aucune donnée disponible");
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de la résolution utilisateur depuis Stripe: {}", e.getMessage(), e);
        }
        return null;
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
            // FUNC-08 FIX : valider le plan avec message propre
            User.SubscriptionPlan newPlan;
            try {
                newPlan = User.SubscriptionPlan.valueOf(plan.toUpperCase());
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", "Plan invalide.");
                return "redirect:/subscription/change-plan";
            }
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

            // ── Upgrade : paiement immédiat obligatoire ──────────────────────────
            // ── Downgrade : pas de prorata, facturation réduite au renouvellement ─
            String existingSubId = user.getStripeSubscriptionId();
            if (existingSubId != null && !existingSubId.isBlank()) {
                try {
                    com.stripe.model.Subscription updatedSub =
                            stripeService.updateSubscriptionPlan(existingSubId, plan, period, isUpgrade);

                    if (isUpgrade) {
                        // Upgrade : vérifier que la facture Stripe a bien été payée
                        String invoiceStatus = updatedSub.getLatestInvoice() != null
                                ? stripeService.getInvoiceStatus(updatedSub.getLatestInvoice())
                                : null;
                        if ("paid".equals(invoiceStatus)) {
                            user.setSubscriptionPlan(newPlan);
                            user.setMaxClients(newPlan.getMaxClients());
                            userRepository.save(user);
                            log.info("Upgrade confirmé (facture payée) {} → {} pour {}", currentPlan, newPlan, user.getEmail());
                            redirectAttributes.addFlashAttribute("message",
                                "Upgrade effectué ! Votre plan " + newPlan.getDisplayName() + " est actif.");
                        } else {
                            // Paiement en attente/échoué — ne pas débloquer le plan
                            log.warn("Upgrade {} → {} pour {} : facture non payée (status={})",
                                currentPlan, newPlan, user.getEmail(), invoiceStatus);
                            redirectAttributes.addFlashAttribute("error",
                                "Le paiement est en cours de traitement. Votre plan sera mis à jour dès confirmation.");
                        }
                    } else {
                        // Downgrade : accès réduit immédiatement, facturation au prochain renouvellement
                        user.setSubscriptionPlan(newPlan);
                        user.setMaxClients(newPlan.getMaxClients());
                        userRepository.save(user);
                        log.info("Downgrade {} → {} pour {}", currentPlan, newPlan, user.getEmail());
                        redirectAttributes.addFlashAttribute("message",
                            "Plan rétrogradé vers " + newPlan.getDisplayName()
                            + ". Le nouveau tarif s'applique à partir de votre prochain renouvellement.");
                    }
                    return "redirect:/subscription/change-plan";
                } catch (StripeException se) {
                    log.warn("Impossible de mettre à jour l'abonnement Stripe {} : {}", existingSubId, se.getMessage());
                    // L'abonnement est peut-être annulé/expiré — on crée un nouveau checkout
                }
            }

            // Pas d'abonnement existant (ou mise à jour Stripe échouée) → nouveau checkout
            if (isUpgrade) {
                String checkoutUrl = stripeService.createCheckoutSession(user, plan, period);
                if (checkoutUrl != null) {
                    log.info("Upgrade {} → {} pour {} (nouveau checkout)", currentPlan, newPlan, user.getEmail());
                    return "redirect:" + checkoutUrl;
                }
                redirectAttributes.addFlashAttribute("error", "Erreur lors de la création du paiement.");
            } else {
                // Downgrade sans abonnement Stripe actif : appliquer directement en DB
                user.setSubscriptionPlan(newPlan);
                user.setMaxClients(newPlan.getMaxClients());
                userRepository.save(user);
                redirectAttributes.addFlashAttribute("message",
                    "Plan rétrogradé vers " + newPlan.getDisplayName() + ".");
            }
            return "redirect:/subscription/change-plan";

        } catch (Exception e) {
            log.error("Erreur lors du changement de plan: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue lors du changement de plan.");
            return "redirect:/subscription/change-plan";
        }
    }

    /**
     * Annuler l'abonnement :
     * - Essai (14 jours) → annulation immédiate, accès coupé
     * - Payé → annulation en fin de période, accès maintenu jusque-là
     */
    @PostMapping("/cancel-subscription")
    public String cancelSubscription(
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            boolean isTrial = user.isTrial();
            
            // 1. Annuler côté Stripe
            if (stripeService.isConfigured()) {
                try {
                    com.stripe.model.Subscription cancelled = null;
                    
                    if (isTrial) {
                        // Essai → annulation immédiate sur Stripe
                        if (user.getStripeSubscriptionId() != null) {
                            cancelled = stripeService.cancelSubscription(user.getStripeSubscriptionId());
                        } else {
                            // Pas de subscriptionId : pas d'abonnement actif Stripe à annuler
                            log.info("Annulation d'essai sans subscriptionId Stripe pour: {}", user.getEmail());
                        }
                        log.info("Abonnement d'essai Stripe annulé immédiatement pour: {}", user.getEmail());
                    } else {
                        // Payé → annulation en fin de période
                        if (user.getStripeSubscriptionId() != null) {
                            cancelled = stripeService.cancelSubscriptionByIdAtPeriodEnd(user.getStripeSubscriptionId());
                        } else if (user.getStripeCustomerId() != null) {
                            cancelled = stripeService.cancelSubscriptionAtPeriodEnd(user.getStripeCustomerId());
                        }
                        if (cancelled != null) {
                            log.info("Abonnement Stripe annulé en fin de période pour: {}", user.getEmail());
                        } else {
                            log.warn("Aucun abonnement Stripe trouvé pour: {} (subId={}, custId={})", 
                                     user.getEmail(), user.getStripeSubscriptionId(), user.getStripeCustomerId());
                        }
                    }
                } catch (Exception stripeEx) {
                    log.error("Erreur Stripe lors de l'annulation pour {}: {}", 
                              user.getEmail(), stripeEx.getMessage());
                    // On continue pour mettre à jour le statut local
                }
            }
            
            // 2. Mettre à jour le statut local
            if (isTrial) {
                // Essai → accès coupé immédiatement
                user.setSubscriptionStatus(User.SubscriptionStatus.INACTIVE);
                user.setSubscriptionEndsAt(null);
                user.setStripeSubscriptionId(null);
                log.info("Abonnement d'essai annulé immédiatement pour: {}", user.getEmail());
                redirectAttributes.addFlashAttribute("message", 
                    "Votre période d'essai a été annulée.");
            } else {
                // Payé → CANCELLED, accès maintenu jusqu'à subscriptionEndsAt
                user.setSubscriptionStatus(User.SubscriptionStatus.CANCELLED);
                log.info("Abonnement annulé (fin de période) pour: {}", user.getEmail());
                redirectAttributes.addFlashAttribute("message", 
                    "Votre abonnement a été annulé. Vous conservez l'accès jusqu'au " 
                    + (user.getSubscriptionEndsAt() != null 
                       ? user.getSubscriptionEndsAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) 
                       : "fin de la période payée") + ".");
            }
            userRepository.save(user);
            
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
            
            // CRIT-05 FIX : idempotence — ne pas retraiter un événement déjà vu
            if (!processedEventIds.add(event.getId())) {
                log.info("Événement Stripe déjà traité (idempotence): {}", event.getId());
                return ResponseEntity.ok("Déjà traité");
            }
            // Limiter la taille du set pour éviter une fuite mémoire
            if (processedEventIds.size() > 10_000) {
                processedEventIds.clear();
            }
            
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
            return ResponseEntity.ok("Webhook reçu");
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
     * FUNC-02 FIX : synchronise aussi le plan depuis Stripe
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

                    // FUNC-02 FIX : synchroniser le plan depuis le price_id Stripe
                    try {
                        if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
                            String priceId = subscription.getItems().getData().get(0).getPrice().getId();
                            User.SubscriptionPlan syncedPlan = stripeService.getPlanFromPriceId(priceId);
                            if (syncedPlan != null && syncedPlan != user.getSubscriptionPlan()) {
                                log.info("Plan synchronisé depuis Stripe: {} → {} pour {}", 
                                         user.getSubscriptionPlan(), syncedPlan, user.getEmail());
                                user.setSubscriptionPlan(syncedPlan);
                                user.setMaxClients(syncedPlan.getMaxClients());
                            }
                        }
                    } catch (Exception planEx) {
                        log.warn("Impossible de synchroniser le plan depuis Stripe: {}", planEx.getMessage());
                    }

                    // Sauvegarder le subscriptionId si manquant
                    if (user.getStripeSubscriptionId() == null || user.getStripeSubscriptionId().isBlank()) {
                        user.setStripeSubscriptionId(subscription.getId());
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
                    // CRIT-05 FIX : Déterminer la période depuis Stripe ou le billing_reason
                    String billingReason = invoice.getBillingReason();
                    boolean isRenewal = "subscription_cycle".equals(billingReason);
                    
                    if (!isRenewal) {
                        // Première facture ou upgrade — ne pas prolonger, juste s'assurer ACTIVE
                        user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
                        userRepository.save(user);
                        log.info("✅ Paiement initial/upgrade pour: {}", user.getEmail());
                    } else {
                        // Renouvellement — prolonger selon la période
                        // Lire current_period_end depuis la subscription Stripe
                        LocalDateTime newEndDate;
                        try {
                            String subId = invoice.getSubscription();
                            if (subId != null) {
                                com.stripe.model.Subscription sub = com.stripe.model.Subscription.retrieve(subId);
                                newEndDate = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochSecond(sub.getCurrentPeriodEnd()),
                                    java.time.ZoneId.systemDefault());
                            } else {
                                // Fallback : +1 mois
                                newEndDate = user.getSubscriptionEndsAt() != null 
                                    ? user.getSubscriptionEndsAt().plusMonths(1) 
                                    : LocalDateTime.now().plusMonths(1);
                            }
                        } catch (Exception stripeEx) {
                            log.warn("Impossible de lire current_period_end depuis Stripe: {}", stripeEx.getMessage());
                            newEndDate = user.getSubscriptionEndsAt() != null 
                                ? user.getSubscriptionEndsAt().plusMonths(1) 
                                : LocalDateTime.now().plusMonths(1);
                        }
                        
                        user.setSubscriptionEndsAt(newEndDate);
                        user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
                        userRepository.save(user);
                        log.info("✅ Abonnement renouvelé pour: {} jusqu'au {}", user.getEmail(), newEndDate);
                    }
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
