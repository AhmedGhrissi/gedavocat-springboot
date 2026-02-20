package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.DocumentService;
import com.gedavocat.service.WatermarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.gedavocat.util.ByteArrayMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final WatermarkService watermarkService;

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

    /**
     * Upload d'un document par le client.
     * Si le fichier est un PDF, un filigrane "COPIE" est ajouté avant stockage.
     */
    @PostMapping("/{caseId}/upload")
    public String uploadDocument(
            @PathVariable String caseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            Client client = clientRepository.findByClientUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Aucun profil client trouvé"));

            Case caseEntity = caseService.getCaseById(caseId);
            if (!caseEntity.getClient().getId().equals(client.getId())) {
                throw new RuntimeException("Accès non autorisé à ce dossier");
            }

            MultipartFile fileToUpload = file;

            // Appliquer le filigrane "COPIE" sur les PDF
            if ("application/pdf".equalsIgnoreCase(file.getContentType())) {
                byte[] watermarked = watermarkService.addWatermark(
                        file.getInputStream(), WatermarkService.WATERMARK_COPIE);
                if (watermarked != null) {
                    fileToUpload = new ByteArrayMultipartFile(
                            file.getName(),
                            file.getOriginalFilename(),
                            file.getContentType(),
                            watermarked);
                }
            }

            documentService.uploadDocument(caseId, fileToUpload, user.getId(), "CLIENT");
            redirectAttributes.addFlashAttribute("message", "Document uploadé avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload : " + e.getMessage());
        }
        return "redirect:/my-cases/" + caseId;
    }

    /**
     * Téléchargement d'un document par le client.
     * Un filigrane "CONFIDENTIEL" est appliqué à la volée sur les PDF.
     */
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String documentId,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            Client client = clientRepository.findByClientUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Aucun profil client trouvé"));

            Document document = documentService.getDocumentById(documentId);

            // SÉCURITÉ : vérifier que le document appartient bien au dossier du client
            if (!document.getCaseEntity().getClient().getId().equals(client.getId())) {
                throw new RuntimeException("Accès non autorisé à ce document");
            }

            Path filePath = documentService.downloadDocument(documentId, user.getId());
            byte[] fileBytes = Files.readAllBytes(filePath);

            // Appliquer le filigrane "CONFIDENTIEL" sur les PDF
            if (watermarkService.isPdf(fileBytes)) {
                byte[] watermarked = watermarkService.addWatermark(
                        new ByteArrayInputStream(fileBytes),
                        WatermarkService.WATERMARK_CONFIDENTIEL);
                if (watermarked != null) {
                    fileBytes = watermarked;
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimetype()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + document.getOriginalName() + "\"")
                    .body(new ByteArrayResource(fileBytes));

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du téléchargement : " + e.getMessage());
        }
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
