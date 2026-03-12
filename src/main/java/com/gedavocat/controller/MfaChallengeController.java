package com.gedavocat.controller;

import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.UserDetailsServiceImpl;
import com.gedavocat.security.mfa.MultiFactorAuthenticationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * SEC FIX F-06 — Étape MFA du flow de connexion (second facteur TOTP)
 *
 * Flow :
 *  1. SecurityConfig.successHandler détecte ROLE_ADMIN + mfaEnabled → stocke
 *     MFA_PENDING_EMAIL en session, vide le SecurityContext, redirige ici.
 *  2. GET /mfa-challenge : affiche le formulaire de saisie du code TOTP.
 *  3. POST /mfa-challenge : valide le code ; si OK, restaure l'authentification complète.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MfaChallengeController {

    private final UserRepository userRepository;
    private final MultiFactorAuthenticationService mfaService;
    private final UserDetailsServiceImpl userDetailsService;

    @GetMapping("/mfa-challenge")
    public String mfaChallengeForm(HttpSession session, Model model) {
        if (session.getAttribute("MFA_PENDING_EMAIL") == null) {
            return "redirect:/login";
        }
        return "auth/mfa-challenge";
    }

    @PostMapping("/mfa-challenge")
    public String mfaChallengeSubmit(
            @RequestParam String code,
            HttpSession session,
            Model model,
            RedirectAttributes ra
    ) {
        String pendingEmail = (String) session.getAttribute("MFA_PENDING_EMAIL");
        String targetUrl    = (String) session.getAttribute("MFA_TARGET_URL");

        if (pendingEmail == null) {
            return "redirect:/login";
        }

        var optUser = userRepository.findByEmail(pendingEmail);
        if (optUser.isEmpty()) {
            session.invalidate();
            return "redirect:/login";
        }

        // Valider le code TOTP (ou un code de secours)
        var result = mfaService.validateMFA(optUser.get(), code.trim());
        if (!result.isValid()) {
            log.warn("[MFA] Code invalide pour {} — méthode : {}", pendingEmail, result.getMethod());
            model.addAttribute("error", "Code invalide. Vérifiez votre application d'authentification.");
            return "auth/mfa-challenge";
        }

        // Code correct — restaurer l'authentification complète dans le SecurityContext
        UserDetails userDetails = userDetailsService.loadUserByUsername(pendingEmail);
        var authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authToken);
        SecurityContextHolder.setContext(ctx);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

        session.removeAttribute("MFA_PENDING_EMAIL");
        session.removeAttribute("MFA_TARGET_URL");

        log.info("[MFA] Validation réussie pour {} (méthode : {})", pendingEmail, result.getMethod());
        return "redirect:" + (targetUrl != null ? targetUrl : "/admin");
    }
}
