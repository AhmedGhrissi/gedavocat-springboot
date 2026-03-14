package com.gedavocat.controller;

import com.gedavocat.service.ClientInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Contrôleur public pour l'acceptation des invitations client.
 * Pas de @PreAuthorize — accès contrôlé uniquement par SecurityConfig (permitAll).
 * Suit le même pattern que CollaboratorInvitationController.
 */
@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientInvitationController {

    private final ClientInvitationService invitationService;

    @GetMapping("/accept-invitation")
    public String acceptInvitationForm(@RequestParam String token, Model model) {
        var entry = invitationService.validateToken(token);
        if (entry.isEmpty()) {
            model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
            return "clients/invitation-expired";
        }
        model.addAttribute("token", token);
        model.addAttribute("email", entry.get().email());
        return "clients/accept-invitation";
    }

    @PostMapping("/accept-invitation")
    public String processAcceptInvitation(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Les mots de passe ne correspondent pas.");
            return "clients/accept-invitation";
        }
        if (password.length() < 8) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Le mot de passe doit contenir au moins 8 caractères.");
            return "clients/accept-invitation";
        }
        if (!com.gedavocat.util.PasswordValidator.isValid(password)) {
            model.addAttribute("token", token);
            model.addAttribute("error", com.gedavocat.util.PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE);
            return "clients/accept-invitation";
        }
        try {
            invitationService.acceptInvitation(token, password);
            redirectAttributes.addFlashAttribute("message", "Compte créé avec succès ! Connectez-vous avec votre email.");
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Erreur lors de l'activation du compte");
            return "clients/accept-invitation";
        }
    }
}
