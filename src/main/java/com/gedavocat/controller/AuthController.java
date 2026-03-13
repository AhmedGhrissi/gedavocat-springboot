package com.gedavocat.controller;

import com.gedavocat.dto.AuthRequest;
import com.gedavocat.dto.AuthResponse;
import com.gedavocat.dto.RegisterRequest;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AuthService;
import com.gedavocat.service.BarreauService;
import com.gedavocat.service.EmailVerificationService;
import com.gedavocat.security.JwtBlacklistService;
import com.gedavocat.security.JwtServiceRS256;
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
    private final JwtServiceRS256 jwtService;

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
        log.info("🔵 POST /register - Tentative d'inscription (email masqué pour RGPD)");
        log.debug("📋 Données reçues: plan={}, billing={}",
            request.getSubscriptionPlan(), billing);

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
            model.addAttribute("barreaux", barreauService.getAllBarreauxActifs());
            return "auth/register";
        }

        try {
            log.info("✅ Validation OK - Appel du service d'inscription");
            authService.register(request);

            // Envoyer le code de vérification et rediriger
            String email = request.getEmail().trim().toLowerCase();
            log.info("📧 Envoi du code de vérification (email masqué pour RGPD)");
            emailVerificationService.generateAndSend(email);

            redirectAttributes.addFlashAttribute("info",
                "Un code de vérification à 6 chiffres a été envoyé à " + email + ". Veuillez le saisir ci-dessous.");
            log.info("🎉 Inscription réussie - Redirection vers /verify-email");
            String verifyUrl = "/verify-email?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
            String chosenPlan = request.getSubscriptionPlan();
            if (chosenPlan != null && !chosenPlan.isBlank()) {
                verifyUrl += "&plan=" + URLEncoder.encode(chosenPlan, StandardCharsets.UTF_8);
                if (billing != null && !billing.isBlank()) {
                    verifyUrl += "&billing=" + URLEncoder.encode(billing, StandardCharsets.UTF_8);
                }
            }
            return "redirect:" + verifyUrl;
        } catch (IllegalArgumentException e) {
            // Erreur de validation métier - analyser le message pour identifier le champ
            String message = e.getMessage();
            log.warn("⚠️ Erreur de validation métier: {}", message);
            
            if (message != null) {
                if (message.contains("email") || message.contains("Email")) {
                    result.rejectValue("email", "email.error", message);
                } else if (message.contains("mot de passe") || message.contains("password")) {
                    result.rejectValue("password", "password.error", message);
                } else if (message.contains("SIREN")) {
                    result.rejectValue("firmSiren", "siren.error", message);
                } else if (message.contains("téléphone") || message.contains("phone")) {
                    result.rejectValue("phone", "phone.error", message);
                } else if (message.contains("conditions") || message.contains("CGU")) {
                    result.rejectValue("termsAccepted", "terms.error", message);
                } else if (message.contains("RGPD") || message.contains("données")) {
                    result.rejectValue("gdprConsent", "gdpr.error", message);
                } else {
                    // Erreur générique si on ne peut pas identifier le champ
                    model.addAttribute("error", message);
                }
            } else {
                model.addAttribute("error", "Erreur lors de l'inscription. Vérifiez vos données.");
            }
            
            model.addAttribute("selectedPlan", request.getSubscriptionPlan());
            model.addAttribute("selectedBilling", billing != null ? billing : "monthly");
            model.addAttribute("barreaux", barreauService.getAllBarreauxActifs());
            return "auth/register";
        } catch (RuntimeException e) {
            // Autres erreurs runtime (ex: email déjà utilisé)
            String message = e.getMessage();
            log.error("❌ Erreur runtime lors de l'inscription: {}", message, e);
            
            if (message != null && (message.contains("email") || message.contains("Email") || message.contains("Erreur lors de l'inscription"))) {
                result.rejectValue("email", "email.exists", "Cette adresse email est déjà utilisée");
            } else if (message != null) {
                model.addAttribute("error", message);
            } else {
                model.addAttribute("error", "Erreur lors de l'inscription. Vérifiez vos données et réessayez.");
            }
            
            model.addAttribute("selectedPlan", request.getSubscriptionPlan());
            model.addAttribute("selectedBilling", billing != null ? billing : "monthly");
            model.addAttribute("barreaux", barreauService.getAllBarreauxActifs());
            return "auth/register";
        } catch (Exception e) {
            // Erreur inattendue
            log.error("❌ Erreur inattendue lors de l'inscription: {}", e.getMessage(), e);
            model.addAttribute("error", "Une erreur technique s'est produite. Veuillez réessayer plus tard.");
            model.addAttribute("selectedPlan", request.getSubscriptionPlan());
            model.addAttribute("selectedBilling", billing != null ? billing : "monthly");
            model.addAttribute("barreaux", barreauService.getAllBarreauxActifs());
            return "auth/register";
        }
    }

    // ----------------------------------------------------------------
    // Vérification email (post-inscription)
    // ----------------------------------------------------------------

    @GetMapping("/verify-email")
    public String verifyEmailPage(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String billing,
            Model model
    ) {
        model.addAttribute("email", email != null ? email : "");
        model.addAttribute("plan", plan);
        model.addAttribute("billing", billing);
        return "auth/verify-email";
    }

    @PostMapping("/verify-email")
    @Transactional
    public String verifyEmailSubmit(
            @RequestParam String email,
            @RequestParam String code,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String billing,
            Model model,
            RedirectAttributes ra,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String emailLower = email.trim().toLowerCase();
        boolean ok = emailVerificationService.verifyCode(emailLower, code.trim());
        if (!ok) {
            model.addAttribute("email", emailLower);
            model.addAttribute("plan", plan);
            model.addAttribute("billing", billing);
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

                log.info("Auto-login réussi (email masqué pour RGPD) — redirection vers checkout/pricing");
                ra.addFlashAttribute("success", "Votre compte a été créé avec succès ! Finalisez votre abonnement pour commencer.");
                if (plan != null && !plan.isBlank()) {
                    String period = (billing != null && !billing.isBlank()) ? billing : "monthly";
                    return "redirect:/subscription/checkout?plan="
                        + URLEncoder.encode(plan, StandardCharsets.UTF_8)
                        + "&period=" + URLEncoder.encode(period, StandardCharsets.UTF_8);
                }
                return "redirect:/subscription/pricing";
            } catch (Exception e) {
                log.error("Erreur auto-login après vérification email: {}", e.getMessage());
                ra.addFlashAttribute("success", "Email vérifié ! Connectez-vous pour accéder à votre compte.");
                return "redirect:/login";
            }
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
            log.warn("Erreur lors de l'inscription API: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", true,
                "message", "Erreur lors de l'inscription. Vérifiez vos données et réessayez."
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
