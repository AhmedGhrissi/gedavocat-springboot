package com.gedavocat.controller;

import com.gedavocat.dto.AuthRequest;
import com.gedavocat.dto.AuthResponse;
import com.gedavocat.dto.RegisterRequest;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AuthService;
import com.gedavocat.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Contrôleur pour l'authentification et l'inscription
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final UserRepository userRepository;

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
        return "auth/register";
    }

    /**
     * Traitement de l'inscription — redirige vers la vérification email
     */
    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegisterRequest request,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (result.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.register(request);
            // Envoyer le code de vérification et rediriger
            String email = request.getEmail().trim().toLowerCase();
            emailVerificationService.generateAndSend(email);
            redirectAttributes.addFlashAttribute("info",
                "Un code de vérification à 6 chiffres a été envoyé à " + email + ". Veuillez le saisir ci-dessous.");
            return "redirect:/verify-email?email=" + email;
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
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
            RedirectAttributes ra
    ) {
        String emailLower = email.trim().toLowerCase();
        boolean ok = emailVerificationService.verifyCode(emailLower, code.trim());
        if (!ok) {
            model.addAttribute("email", emailLower);
            model.addAttribute("error", "Code incorrect ou expiré. Vérifiez votre email ou demandez un nouveau code.");
            return "auth/verify-email";
        }
        // Marquer l'utilisateur comme vérifié
        userRepository.findByEmail(emailLower).ifPresent(u -> {
            u.setEmailVerified(true);
            userRepository.save(u);
        });
        ra.addFlashAttribute("message", "Email vérifié avec succès ! Vous pouvez maintenant vous connecter.");
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
        return "redirect:/verify-email?email=" + emailLower;
    }

    // ----------------------------------------------------------------
    // API REST
    // ----------------------------------------------------------------

    /**
     * API REST - Connexion
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API REST - Inscription
     */
    @PostMapping("/api/auth/register")
    @ResponseBody
    public ResponseEntity<AuthResponse> registerApi(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * API REST - Rafraîchir le token
     */
    @PostMapping("/api/auth/refresh")
    @ResponseBody
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String token) {
        String jwt = token.substring(7); // Enlever "Bearer "
        AuthResponse response = authService.refreshToken(jwt);
        return ResponseEntity.ok(response);
    }
}
