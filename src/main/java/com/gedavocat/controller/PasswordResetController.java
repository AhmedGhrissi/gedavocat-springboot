package com.gedavocat.controller;

import com.gedavocat.service.PasswordResetService;
import com.gedavocat.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Flux de réinitialisation de mot de passe
 *  GET  /forgot-password       → formulaire saisie email
 *  POST /forgot-password       → envoi du lien par email
 *  GET  /reset-password?token= → formulaire nouveau mot de passe
 *  POST /reset-password        → validation + sauvegarde
 */
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ----------------------------------------------------------------
    // Étape 1 : saisie de l'email
    // ----------------------------------------------------------------

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(
            @RequestParam String email,
            RedirectAttributes ra
    ) {
        passwordResetService.requestReset(email.trim().toLowerCase());
        // Réponse neutre (ne pas révéler si l'email existe)
        ra.addFlashAttribute("message",
            "Si cet email est associé à un compte, un lien de réinitialisation vient d'être envoyé.");
        return "redirect:/forgot-password";
    }

    // ----------------------------------------------------------------
    // Étape 2 : saisie du nouveau mot de passe via le lien
    // ----------------------------------------------------------------

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (token == null || passwordResetService.validateToken(token) == null) {
            model.addAttribute("error", "Ce lien de réinitialisation est invalide ou a expiré.");
            return "auth/reset-password";
        }
        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model,
            RedirectAttributes ra
    ) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Les mots de passe ne correspondent pas.");
            model.addAttribute("token", token);
            return "auth/reset-password";
        }
        if (password.length() < 8) {
            model.addAttribute("error", "Le mot de passe doit contenir au moins 8 caractères.");
            model.addAttribute("token", token);
            return "auth/reset-password";
        }
        // SEC-15 FIX : Mêmes exigences de complexité que l'inscription
        if (!PasswordValidator.isValid(password)) {
            model.addAttribute("error", PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE);
            model.addAttribute("token", token);
            return "auth/reset-password";
        }

        boolean ok = passwordResetService.resetPassword(token, password);
        if (!ok) {
            model.addAttribute("error", "Ce lien de réinitialisation est invalide ou a expiré.");
            return "auth/reset-password";
        }
        ra.addFlashAttribute("message",
            "Votre mot de passe a été réinitialisé. Vous pouvez maintenant vous connecter.");
        return "redirect:/login";
    }
}
