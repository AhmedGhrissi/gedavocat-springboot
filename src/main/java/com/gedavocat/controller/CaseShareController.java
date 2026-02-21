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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

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

        if (!caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new RuntimeException("Accès non autorisé");
        }

        List<CaseShareLink> existingLinks = shareService.getLinksForCase(id);

        model.addAttribute("case", caseEntity);
        model.addAttribute("existingLinks", existingLinks);
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
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            CaseShareLink link = shareService.createShareLink(id, user, description, expiresAt, maxAccessCount, emailTo);

            String shareUrl = "/cases/shared?token=" + link.getToken();
            redirectAttributes.addFlashAttribute("shareUrl", shareUrl);
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
    @Transactional
    public String accessSharedCase(@RequestParam String token, Model model) {
        try {
            CaseShareLink link = shareService.accessByToken(token);
            Case caseEntity = link.getSharedCase();

            // Charger les données nécessaires
            caseEntity.getName(); // init lazy
            if (caseEntity.getClient() != null) caseEntity.getClient().getName();

            model.addAttribute("case", caseEntity);
            model.addAttribute("documents", documentService.getLatestVersions(caseEntity.getId()));
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
