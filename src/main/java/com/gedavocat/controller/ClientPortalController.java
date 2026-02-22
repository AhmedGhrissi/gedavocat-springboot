package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
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
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private final AppointmentService appointmentService;

    /**
     * Liste des dossiers du client connecté
     */
    /** Retourne la page d'attente si le lien client→user n'existe pas encore. */
    private String notLinked(Model model) {
        model.addAttribute("errorMessage",
                "Votre profil client n'a pas encore été activé. Contactez votre avocat.");
        return "client-portal/pending";
    }

    @GetMapping
    public String listMyCases(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        
        // Récupérer le client associé à cet utilisateur
        java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) return notLinked(model);
        Client client = clientOpt.get();

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
        java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) return notLinked(model);
        Client client = clientOpt.get();

        // Récupérer le dossier
        Case caseEntity = caseService.getCaseById(caseId);
        
        // SÉCURITÉ : Vérifier que ce dossier appartient bien à ce client
        if (caseEntity.getClient() == null || !caseEntity.getClient().getId().equals(client.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "client-portal/pending";
        }
        
        // Récupérer les documents du dossier
        List<Document> documents = documentService.getLatestVersions(caseId);

        // Récupérer les rendez-vous liés au dossier
        List<Appointment> appointments;
        try {
            appointments = appointmentService.getAppointmentsByCase(caseId);
        } catch (Exception e) {
            appointments = Collections.emptyList();
        }
        
        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documents);
        model.addAttribute("appointments", appointments);
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
        java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) return notLinked(model);
        Client client = clientOpt.get();

        // Récupérer le dossier
        Case caseEntity = caseService.getCaseById(caseId);
        
        // SÉCURITÉ : Vérifier que ce dossier appartient bien à ce client
        if (caseEntity.getClient() == null || !caseEntity.getClient().getId().equals(client.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "client-portal/pending";
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
            java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
            if (clientOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Votre profil client n'est pas encore activé.");
                return "redirect:/my-cases";
            }
            Client client = clientOpt.get();

            Case caseEntity = caseService.getCaseById(caseId);
            if (caseEntity.getClient() == null || !caseEntity.getClient().getId().equals(client.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à ce dossier.");
                return "redirect:/my-cases";
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
     * Upload AJAX (utilisé par le scanner).
     * Même logique que l'upload standard — filigrane COPIE sur les PDF.
     * Retourne JSON : {"success": true/false, "message": "..."}
     */
    @PostMapping("/{caseId}/upload-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadDocumentAjax(
            @PathVariable String caseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
            if (clientOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "Profil client non activé."));
            }
            Client client = clientOpt.get();

            Case caseEntity = caseService.getCaseById(caseId);
            if (caseEntity.getClient() == null || !caseEntity.getClient().getId().equals(client.getId())) {
                return ResponseEntity.ok(Map.of("success", false, "message", "Accès non autorisé à ce dossier."));
            }

            MultipartFile fileToUpload = file;

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
            return ResponseEntity.ok(Map.of("success", true, "message", "Document scanné et enregistré."));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
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
            java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
            if (clientOpt.isEmpty()) {
                return ResponseEntity.status(403).build();
            }
            Client client = clientOpt.get();

            Document document = documentService.getDocumentById(documentId);

            // SÉCURITÉ : vérifier que le document appartient bien au dossier du client
            if (document.getCaseEntity().getClient() == null ||
                    !document.getCaseEntity().getClient().getId().equals(client.getId())) {
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

    // =========================================================================
    // Export ZIP de tous les documents d'un dossier
    // =========================================================================

    @GetMapping("/{caseId}/export-zip")
    public ResponseEntity<Resource> exportCaseDocumentsZip(
            @PathVariable String caseId,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
            if (clientOpt.isEmpty()) return ResponseEntity.status(403).build();
            Client client = clientOpt.get();

            Case caseEntity = caseService.getCaseById(caseId);
            if (caseEntity.getClient() == null || !caseEntity.getClient().getId().equals(client.getId())) {
                return ResponseEntity.status(403).build();
            }

            List<Document> documents = documentService.getLatestVersions(caseId);
            if (documents.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (Document doc : documents) {
                    try {
                        Path filePath = documentService.downloadDocument(doc.getId(), user.getId());
                        byte[] fileBytes = Files.readAllBytes(filePath);

                        // Appliquer filigrane CONFIDENTIEL sur les PDF
                        if (watermarkService.isPdf(fileBytes)) {
                            byte[] watermarked = watermarkService.addWatermark(
                                    new ByteArrayInputStream(fileBytes),
                                    WatermarkService.WATERMARK_CONFIDENTIEL);
                            if (watermarked != null) fileBytes = watermarked;
                        }

                        ZipEntry entry = new ZipEntry(doc.getOriginalName());
                        zos.putNextEntry(entry);
                        zos.write(fileBytes);
                        zos.closeEntry();
                    } catch (Exception ignored) {
                        // Skip documents that can't be read
                    }
                }
            }

            String zipName = "Dossier_" + caseEntity.getName().replaceAll("[^a-zA-Z0-9àâéèêëïîôùûüç_\\- ]", "").trim() + ".zip";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                    .body(new ByteArrayResource(baos.toByteArray()));

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'export ZIP : " + e.getMessage());
        }
    }

    // =========================================================================
    // Profil du client – informations personnelles
    // =========================================================================

    /** Affiche la page de profil du client connecté. */
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) return notLinked(model);
        model.addAttribute("client", clientOpt.get());
        model.addAttribute("user", user);
        return "client-portal/profile";
    }

    /** Sauvegarde les informations personnelles du client. */
    @PostMapping("/profile")
    public String updateProfile(
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "siret", required = false) String siret,
            @RequestParam(value = "birthDate", required = false) String birthDate,
            @RequestParam(value = "nationality", required = false) String nationality,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        User user = getCurrentUser(authentication);
        java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Profil client introuvable");
            return "redirect:/my-cases";
        }
        Client client = clientOpt.get();
        client.setPhone(phone);
        client.setAddress(address);
        if (client.getClientType() == Client.ClientType.PROFESSIONAL) {
            client.setCompanyName(companyName);
            client.setSiret(siret);
        }
        clientRepository.save(client);
        redirectAttributes.addFlashAttribute("message", "Vos informations ont été mises à jour.");
        return "redirect:/my-cases/profile";
    }
}
