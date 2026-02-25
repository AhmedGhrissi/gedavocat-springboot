package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.Document;
import com.gedavocat.model.Permission;
import com.gedavocat.model.Signature;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.PermissionRepository;
import com.gedavocat.repository.SignatureRepository;
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
import org.springframework.transaction.annotation.Transactional;

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
    private final PermissionRepository permissionRepository;
    private final SignatureRepository signatureRepository;

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
    @Transactional(readOnly = true)
    public String listMyCases(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        
        // Récupérer le client associé à cet utilisateur
        java.util.Optional<Client> clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) return notLinked(model);
        Client client = clientOpt.get();

        // Récupérer UNIQUEMENT les dossiers de ce client
        List<Case> myCases = caseService.getCasesByClient(client.getId());
        
        // Force-initialiser les proxies lazy (open-in-view=false)
        for (Case c : myCases) {
            if (c.getLawyer() != null) {
                c.getLawyer().getName(); // force init
            }
            if (c.getClient() != null) {
                c.getClient().getName(); // force init
            }
        }
        
        model.addAttribute("cases", myCases);
        model.addAttribute("user", user);
        model.addAttribute("client", client);

        // Signatures du client (par email)
        List<Signature> signatures;
        try {
            signatures = signatureRepository.findBySignerEmail(user.getEmail());
        } catch (Exception e) {
            signatures = Collections.emptyList();
        }
        long pendingSignatures = signatures.stream()
                .filter(s -> s.getStatus() == Signature.SignatureStatus.PENDING).count();
        long signedSignatures = signatures.stream()
                .filter(s -> s.getStatus() == Signature.SignatureStatus.SIGNED).count();
        model.addAttribute("signatures", signatures);
        model.addAttribute("pendingSignatures", pendingSignatures);
        model.addAttribute("signedSignatures", signedSignatures);
        
        return "client-portal/cases";
    }

    /**
     * Détail d'un dossier (avec vérification de propriété)
     */
    @GetMapping("/{caseId}")
    @Transactional(readOnly = true)
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
            // Force-initialiser les proxies lazy (open-in-view=false)
            for (Appointment a : appointments) {
                if (a.getClient() != null) a.getClient().getName();
                if (a.getRelatedCase() != null) a.getRelatedCase().getName();
            }
        } catch (Exception e) {
            appointments = Collections.emptyList();
        }
        
        // Force-initialiser les proxies lazy du dossier
        if (caseEntity.getLawyer() != null) {
            caseEntity.getLawyer().getName();
            caseEntity.getLawyer().getEmail();
        }

        // Récupérer les intervenants partageant ce dossier (collaborateurs, huissiers)
        List<Permission> activePermissions;
        try {
            activePermissions = permissionRepository.findActiveByCaseId(caseId);
            for (Permission p : activePermissions) {
                if (p.getLawyer() != null) {
                    p.getLawyer().getName();
                    p.getLawyer().getRole();
                }
            }
        } catch (Exception e) {
            activePermissions = Collections.emptyList();
        }
        
        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documents);
        model.addAttribute("appointments", appointments);
        model.addAttribute("activePermissions", activePermissions);
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
     * Filigrane COPIE appliqué de manière persistante sur les PDF au moment de l'upload.
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

            // Filigrane COPIE persistant sur les PDF
            MultipartFile fileToStore = applyWatermarkIfPdf(file, WatermarkService.WATERMARK_COPIE);
            documentService.uploadDocument(caseId, fileToStore, user.getId(), "CLIENT");
            redirectAttributes.addFlashAttribute("message", "Document uploadé avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload : " + e.getMessage());
        }
        return "redirect:/my-cases/" + caseId;
    }

    /**
     * Upload AJAX (utilisé par le scanner).
     * Filigrane COPIE appliqué de manière persistante sur les PDF au moment de l'upload.
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

            // Filigrane COPIE persistant sur les PDF
            MultipartFile fileToStore = applyWatermarkIfPdf(file, WatermarkService.WATERMARK_COPIE);
            documentService.uploadDocument(caseId, fileToStore, user.getId(), "CLIENT");
            return ResponseEntity.ok(Map.of("success", true, "message", "Document scanné et enregistré."));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Téléchargement d'un document par le client.
     * Le filigrane est déjà appliqué sur le fichier stocké (persistant).
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

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimetype()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + sanitizeFilename(document.getOriginalName()) + "\"")
                    .body(new ByteArrayResource(fileBytes));

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du téléchargement : " + e.getMessage());
        }
    }

    /**
     * Prévisualisation d'un document par le client (affichage inline).
     * Le filigrane est déjà appliqué sur le fichier stocké (persistant).
     */
    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<Resource> previewDocument(
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

            if (document.getCaseEntity().getClient() == null ||
                    !document.getCaseEntity().getClient().getId().equals(client.getId())) {
                return ResponseEntity.status(403).build();
            }

            Path filePath = documentService.downloadDocument(documentId, user.getId());
            byte[] fileBytes = Files.readAllBytes(filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimetype()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + sanitizeFilename(document.getOriginalName()) + "\"")
                    .body(new ByteArrayResource(fileBytes));

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la prévisualisation : " + e.getMessage());
        }
    }

    /**
     * Applique un filigrane sur un PDF. Retourne le fichier d'origine si
     * ce n'est pas un PDF ou si le filigrane échoue.
     */
    private MultipartFile applyWatermarkIfPdf(MultipartFile file, String watermarkText) {
        try {
            byte[] bytes = file.getBytes();
            if (watermarkService.isPdf(bytes)) {
                byte[] watermarked = watermarkService.addWatermark(
                        new ByteArrayInputStream(bytes), watermarkText);
                if (watermarked != null) {
                    return new ByteArrayMultipartFile(
                            file.getName(), file.getOriginalFilename(),
                            file.getContentType(), watermarked);
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, on stocke le fichier original
        }
        return file;
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    /**
     * Nettoie un nom de fichier pour éviter l'injection dans les en-têtes HTTP
     * et les attaques Zip Slip (traversal de répertoire).
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) return "document";
        // Supprimer tout chemin de répertoire (Zip Slip prevention)
        String sanitized = filename.replace("\\", "/");
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) sanitized = sanitized.substring(lastSlash + 1);
        // Supprimer caractères dangereux pour les en-têtes HTTP
        sanitized = sanitized.replaceAll("[\\r\\n\"]", "_");
        return sanitized.isEmpty() ? "document" : sanitized;
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

                        ZipEntry entry = new ZipEntry(sanitizeFilename(doc.getOriginalName()));
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
