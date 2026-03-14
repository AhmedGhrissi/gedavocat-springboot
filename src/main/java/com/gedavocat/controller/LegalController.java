package com.gedavocat.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrôleur pour les pages légales (CGU, Politique de confidentialité).
 * Renvoie une version avec sidebar si l'utilisateur est connecté,
 * standalone sinon.
 */
@Controller
@RequestMapping("/legal")
public class LegalController {

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    @GetMapping("/privacy")
    public String showPrivacy(Model model, Authentication auth) {
        model.addAttribute("page", "legal");
        model.addAttribute("title", "Politique de confidentialité");
        return isAuthenticated(auth) ? "legal/privacy-auth" : "legal/privacy";
    }

    @GetMapping("/terms")
    public String showTerms(Model model, Authentication auth) {
        model.addAttribute("page", "legal");
        model.addAttribute("title", "Conditions Générales d'Utilisation");
        return isAuthenticated(auth) ? "legal/terms-auth" : "legal/terms";
    }

    @GetMapping("/cgu")
    public String showCgu(Model model, Authentication auth) {
        model.addAttribute("page", "legal");
        model.addAttribute("title", "Conditions Générales d'Utilisation");
        return isAuthenticated(auth) ? "legal/terms-auth" : "legal/terms";
    }
}

