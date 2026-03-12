package com.gedavocat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrôleur pour les pages légales (CGU, Politique de confidentialité)
 */
@Controller
@RequestMapping("/legal")
public class LegalController {

    /**
     * Affiche la politique de confidentialité et RGPD
     */
    @GetMapping("/privacy")
    public String showPrivacy(Model model) {
        model.addAttribute("page", "legal");
        model.addAttribute("title", "Politique de confidentialité");
        return "legal/privacy";
    }

    /**
     * Affiche les conditions générales d'utilisation
     */
    @GetMapping("/terms")
    public String showTerms(Model model) {
        model.addAttribute("page", "legal");
        model.addAttribute("title", "Conditions Générales d'Utilisation");
        return "legal/terms";
    }

    /**
     * CGU — alias français de /legal/terms
     */
    @GetMapping("/cgu")
    public String showCgu(Model model) {
        model.addAttribute("page", "legal");
        model.addAttribute("title", "Conditions Générales d'Utilisation");
        return "legal/terms";
    }
}
