package com.gedavocat.controller;

import com.gedavocat.dto.AuthRequest;
import com.gedavocat.dto.AuthResponse;
import com.gedavocat.dto.RegisterRequest;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AuthService;
import com.gedavocat.service.BarreauService;
import com.gedavocat.service.EmailVerificationService;
import com.gedavocat.security.JwtBlacklistService;
import com.gedavocat.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Contrôleur pour l'authentification et l'inscription
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final BarreauService barreauService;
    private final EmailVerificationService emailVerificationService;
    private final UserRepository userRepository;
    private final JwtBlacklistService jwtBlacklistService;
    private final JwtService jwtService;

    /**
     * Page de connexion
     */
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model
    ) {
        if (error != null) {
            model.addAttribute("error", "Email ou mot de passe incorrect");
        }
        // logout param kept for backward compat but logoutSuccessUrl now redirects to /login directly
        model.addAttribute("authRequest", new AuthRequest());
        return "auth/login";
    }

    /**
     * Page d'inscription
     */
    @GetMapping("/register")
    public String registerPage(
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String billing,
            Model model) {
        RegisterRequest request = new RegisterRequest();
        if (plan != null) {
            request.setSubscriptionPlan(plan);
        }
        model.addAttribute("registerRequest", request);
        model.addAttribute("selectedPlan", plan);
        model.addAttribute("selectedBilling", billing != null ? billing : "monthly");

        // Ajouter la liste des barreaux pour le select
        model.addAttribute("barreaux", barreauService.getAllBarreauxActifs());

        return "auth/register";
    }

    /**
     * Traitement de l'inscription — redirige vers la vérification email
     */
    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegisterRequest request,
            BindingResult result,
            @RequestParam(required = false) String billing,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        log.info("🔵 POST /register - Tentative d'inscription pour email: {}", request.getEmail());
        log.debug("📋 Données reçues: firstName={}, lastName={}, plan={}, billing={}",
            request.getFirstName(), request.getLastName(), request.getSubscriptionPlan(), billing);

        // Validation personnalisée : vérifier que les mots de passe correspondent
        if (request.getPassword() != null && request.getConfirmPassword() != null
            && !request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("⚠️ Les mots de passe ne correspondent pas");
            result.rejectValue("confirmPassword", "password.mismatch",
                "Les mots de passe ne correspondent pas");
        }

        // Validation des checkboxes
        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            log.warn("⚠️ CGU non acceptées");
            result.rejectValue("termsAccepted", "terms.required",
                "Vous devez accepter les conditions d'utilisation");
        }

        if (!Boolean.TRUE.equals(request.getGdprConsent())) {
            log.warn("⚠️ Consentement RGPD non donné");
            result.rejectValue("gdprConsent", "gdpr.required",
                "Vous devez accepter le traitement de vos données personnelles");
        }

        if (result.hasErrors()) {
            log.error("❌ Erreurs de validation détectées: {}", result.getAllErrors());
            model.addAttribute("selectedPlan", request.getSubscriptionPlan());
            model.addAttribute("selectedBilling", billing != null ? billing : "monthly");
            return "auth/register";
        }

        try {
            log.info("✅ Validation OK - Appel du service d'inscription");
            authService.register(request);

            // Envoyer le code de vérification et rediriger
            String email = request.getEmail().trim().toLowerCase();
            log.info("📧 Envoi du code de vérification à: {}", email);
            emailVerificationService.generateAndSend(email);

            redirectAttributes.addFlashAttribute("info",
                "Un code de vérification à 6 chiffres a été envoyé à " + email + ". Veuillez le saisir ci-dessous.");
            log.info("🎉 Inscription réussie - Redirection vers /verify-email");
            return "redirect:/verify-email?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'inscription: {}", e.getMessage(), e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("selectedPlan", request.getSubscriptionPlan());
            model.addAttribute("selectedBilling", billing != null ? billing : "monthly");
            return "auth/register";
        }
    }

    // ----------------------------------------------------------------
    // Vérification email (post-inscription)
    // ----------------------------------------------------------------

    @GetMapping("/verify-email")
    public String verifyEmailPage(
            @RequestParam(required = false) String email,
            Model model
    ) {
        model.addAttribute("email", email != null ? email : "");
        return "auth/verify-email";
    }

    @PostMapping("/verify-email")
    @Transactional
    public String verifyEmailSubmit(
            @RequestParam String email,
            @RequestParam String code,
            Model model,
            RedirectAttributes ra,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String emailLower = email.trim().toLowerCase();
        boolean ok = emailVerificationService.verifyCode(emailLower, code.trim());
        if (!ok) {
            model.addAttribute("email", emailLower);
            model.addAttribute("error", "Code incorrect ou expiré. Vérifiez votre email ou demandez un nouveau code.");
            return "auth/verify-email";
        }
        // Marquer l'utilisateur comme vérifié
        var optUser = userRepository.findByEmail(emailLower);
        if (optUser.isPresent()) {
            var user = optUser.get();
            user.setEmailVerified(true);
            userRepository.save(user);

            // Auto-login pour permettre le checkout Stripe
            try {
                var userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(user.getEmail())
                    .password(user.getPassword())
                    .authorities("ROLE_" + user.getRole().name())
                    .build();
                var authToken = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authToken);
                // Sauvegarder dans la session HTTP
                var session = request.getSession(true);
                session.setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    org.springframework.security.core.context.SecurityContextHolder.getContext());

                log.info("Auto-login réussi pour {} — redirection vers le paiement Stripe", emailLower);
            } catch (Exception e) {
                log.error("Erreur auto-login après vérification email: {}", e.getMessage());
                ra.addFlashAttribute("message", "Email vérifié ! Connectez-vous pour procéder au paiement.");
                return "redirect:/login";
            }

            // Rediriger vers Stripe Checkout avec le plan choisi lors de l'inscription
            // FUNC-06 FIX : conserver la période de facturation choisie lors de l'inscription
            String plan = user.getSubscriptionPlan() != null 
                ? user.getSubscriptionPlan().name() : "ESSENTIEL";
            String period = user.getBillingPeriod() != null ? user.getBillingPeriod() : "monthly";
            return "redirect:/subscription/checkout?plan=" + plan + "&period=" + period;
        }
        ra.addFlashAttribute("message", "Email vérifié ! Connectez-vous pour commencer.");
        return "redirect:/login";
    }

    @PostMapping("/verify-email/resend")
    public String resendVerificationCode(
            @RequestParam String email,
            RedirectAttributes ra
    ) {
        String emailLower = email.trim().toLowerCase();
        // Renvoyer seulement si l'utilisateur existe et n'est pas encore vérifié
        userRepository.findByEmail(emailLower).ifPresent(u -> {
            if (!u.isEmailVerified()) {
                emailVerificationService.generateAndSend(emailLower);
            }
        });
        ra.addFlashAttribute("info", "Un nouveau code a été envoyé à " + emailLower + " (si le compte existe).");
        return "redirect:/verify-email?email=" + URLEncoder.encode(emailLower, StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------
    // API REST
    // ----------------------------------------------------------------

    /**
     * API REST - Connexion
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = authService.authenticate(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", true,
                "message", "Identifiants incorrects"
            ));
        }
    }

    /**
     * API REST - Inscription
     * SEC-AUTH FIX : ne retourne plus de JWT directement — exige la vérification email
     */
    @PostMapping("/api/auth/register")
    @ResponseBody
    public ResponseEntity<?> registerApi(@Valid @RequestBody RegisterRequest request) {
        try {
            authService.register(request);
            // Envoyer le code de vérification (comme le flow web)
            String email = request.getEmail().trim().toLowerCase();
            emailVerificationService.generateAndSend(email);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Inscription réussie. Un code de vérification a été envoyé à " + email + ". Veuillez vérifier votre email avant de vous connecter.",
                "requiresVerification", true
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", true,
                "message", e.getMessage() != null ? e.getMessage() : "Erreur lors de l'inscription"
            ));
        }
    }

    /**
     * API REST - Rafraîchir le token
     */
    @PostMapping("/api/auth/refresh")
    @ResponseBody
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }
        String jwt = token.substring(7);
        AuthResponse response = authService.refreshToken(jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * API REST - Logout (blackliste le JWT)
     * SEC-01 FIX : permet la révocation côté serveur du token JWT
     */
    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<?> logoutApi(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            try {
                java.util.Date expiration = jwtService.extractExpiration(jwt);
                jwtBlacklistService.blacklist(jwt, expiration);
            } catch (Exception e) {
                // Token déjà expiré ou invalide — on ignore silencieusement
            }
        }
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }
}
