package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.CaseShareLink;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.CaseShareService;
import com.gedavocat.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur de partage de dossier entre avocats.
 *
 *  - GET  /cases/{id}/share        → formulaire de création d'un lien
 *  - POST /cases/{id}/share        → crée le lien
 *  - POST /cases/{id}/share/{linkId}/revoke → révoque un lien
 *  - GET  /cases/shared?token=xxx  → accès en lecture via token (route publique)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CaseShareController {

    private final CaseShareService shareService;
    private final CaseService caseService;
    private final DocumentService documentService;
    private final UserRepository userRepository;

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    // =========================================================================
    // Pages protégées (avocat propriétaire)
    // =========================================================================

    /**
     * Formulaire de création d'un lien de partage
     */
    @GetMapping("/cases/{id}/share")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    @Transactional(readOnly = true)
    public String shareForm(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(id);

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new RuntimeException("Accès non autorisé");
        }

        List<CaseShareLink> existingLinks = shareService.getLinksForCase(id);

        // Build public URLs for each existing link so the UI shows the same link sent by email
        Map<String, String> linkPublicUrls = new HashMap<>();
        for (CaseShareLink link : existingLinks) {
            String publicPath;
            boolean userExists = false;
            try {
                if (link.getRecipientEmail() != null) {
                    userExists = userRepository.findByEmail(link.getRecipientEmail()).isPresent();
                }
            } catch (Exception ignore) { }
            if (userExists) {
                publicPath = baseUrl + "/cases/shared?token=" + link.getToken();
            } else {
                // Route based on recipientRole
                String invitePath = link.getRecipientRole() == User.UserRole.HUISSIER
                        ? "/huissiers/accept-invitation"
                        : "/collaborators/accept-invitation";
                publicPath = baseUrl + invitePath + "?token=" + link.getToken();
            }
            linkPublicUrls.put(link.getId(), publicPath);
        }

        model.addAttribute("case", caseEntity);
        model.addAttribute("existingLinks", existingLinks);
        model.addAttribute("linkPublicUrls", linkPublicUrls);
        return "cases/share";
    }

    /**
     * Crée un lien de partage pour un dossier
     */
    @PostMapping("/cases/{id}/share")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    public String createShareLink(
            @PathVariable String id,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime expiresAt,
            @RequestParam(required = false) Integer maxAccessCount,
            @RequestParam(required = false) String emailTo,
            @RequestParam(required = false, defaultValue = "LAWYER_SECONDARY") String recipientRole,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            User.UserRole role = User.UserRole.valueOf(recipientRole);
            CaseShareLink link = shareService.createShareLink(id, user, description, expiresAt, maxAccessCount, emailTo, role);

            // Build the full public URL (same logic used in email)
            String fullPublicUrl = shareService.buildPublicUrl(link.getToken(), emailTo, role);
            redirectAttributes.addFlashAttribute("shareFullUrl", fullPublicUrl);
            redirectAttributes.addFlashAttribute("message", "Lien de partage créé avec succès !");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur : " + e.getMessage());
        }
        return "redirect:/cases/" + id + "/share";
    }

    /**
     * Révoque un lien de partage
     */
    @PostMapping("/cases/{id}/share/{linkId}/revoke")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    public String revokeLink(
            @PathVariable String id,
            @PathVariable String linkId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            shareService.revokeLink(linkId, user);
            redirectAttributes.addFlashAttribute("message", "Lien révoqué.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur : " + e.getMessage());
        }
        return "redirect:/cases/" + id + "/share";
    }

    // =========================================================================
    // Accès via token (pas d'authentification requise)
    // =========================================================================

    /**
     * Accès en lecture au dossier partagé via token
     */
    @GetMapping("/cases/shared")
    public String accessSharedCase(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("error", "Token de partage manquant");
            return "cases/shared-expired";
        }
        try {
            CaseShareLink link = shareService.accessByToken(token);

            // Si le lien a un destinataire email et qu'il n'a pas encore de compte →
            // rediriger vers la page de création de compte collaborateur ou huissier
            if (link.getRecipientEmail() != null && !link.getRecipientEmail().isBlank()) {
                boolean accountExists = userRepository.findByEmail(link.getRecipientEmail()).isPresent();
                if (!accountExists) {
                    String invitePath = link.getRecipientRole() == User.UserRole.HUISSIER
                            ? "/huissiers/accept-invitation"
                            : "/collaborators/accept-invitation";
                    return "redirect:" + invitePath + "?token=" + token;
                }
            }

            model.addAttribute("case", link.getSharedCase());
            model.addAttribute("documents", documentService.getLatestVersions(link.getSharedCase().getId()));
            model.addAttribute("link", link);
            model.addAttribute("token", token);
            return "cases/shared-view";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "cases/shared-expired";
        }
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
