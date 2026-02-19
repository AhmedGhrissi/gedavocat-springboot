package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur pour le portail client
 * Les clients ne voient QUE leurs propres dossiers et documents
 */
@Controller
@RequestMapping("/my-cases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class ClientPortalController {

    private final CaseService caseService;
    private final DocumentService documentService;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    /**
     * Liste des dossiers du client connecté
     */
    @GetMapping
    public String listMyCases(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        
        // Récupérer le client associé à cet utilisateur
        Client client = clientRepository.findByClientUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Aucun profil client trouvé pour cet utilisateur"));
        
        // Récupérer UNIQUEMENT les dossiers de ce client
        List<Case> myCases = caseService.getCasesByClient(client.getId());
        
        model.addAttribute("cases", myCases);
        model.addAttribute("user", user);
        model.addAttribute("client", client);
        
        return "client-portal/cases";
    }

    /**
     * Détail d'un dossier (avec vérification de propriété)
     */
    @GetMapping("/{caseId}")
    public String viewMyCase(
            @PathVariable String caseId,
            Model model,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        
        // Récupérer le client
        Client client = clientRepository.findByClientUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Aucun profil client trouvé"));
        
        // Récupérer le dossier
        Case caseEntity = caseService.getCaseById(caseId);
        
        // SÉCURITÉ : Vérifier que ce dossier appartient bien à ce client
        if (!caseEntity.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Accès non autorisé à ce dossier");
        }
        
        // Récupérer les documents du dossier
        List<Document> documents = documentService.getLatestVersions(caseId);
        
        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documents);
        model.addAttribute("user", user);
        model.addAttribute("client", client);
        
        return "client-portal/case-detail";
    }

    /**
     * Liste des documents du client
     */
    @GetMapping("/{caseId}/documents")
    public String listMyDocuments(
            @PathVariable String caseId,
            Model model,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        
        // Récupérer le client
        Client client = clientRepository.findByClientUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Aucun profil client trouvé"));
        
        // Récupérer le dossier
        Case caseEntity = caseService.getCaseById(caseId);
        
        // SÉCURITÉ : Vérifier que ce dossier appartient bien à ce client
        if (!caseEntity.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Accès non autorisé à ce dossier");
        }
        
        // Récupérer les documents
        List<Document> documents = documentService.getLatestVersions(caseId);
        
        model.addAttribute("documents", documents);
        model.addAttribute("case", caseEntity);
        model.addAttribute("user", user);
        model.addAttribute("client", client);
        
        return "client-portal/documents";
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
