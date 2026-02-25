package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.Permission;
import com.gedavocat.model.User;
import com.gedavocat.repository.PermissionRepository;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Contrôleur pour le portail Collaborateur
 * Similaire au portail client mais pour les collaborateurs (rôle COLLABORATOR)
 */
@Controller
@RequestMapping("/my-cases-collab")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LAWYER_SECONDARY')")
public class CollaboratorPortalController {

    private final CaseService caseService;
    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final WatermarkService watermarkService;
    private final AppointmentService appointmentService;
    private final PermissionRepository permissionRepository;



    @GetMapping
    public String listMyCases(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);

        // Récupérer uniquement les dossiers où l'utilisateur est assigné comme collaborator
        List<Case> myCases = caseService.getCasesByCollaborator(user.getId());

        // Force-init proxies
        for (Case c : myCases) {
            if (c.getLawyer() != null) c.getLawyer().getName();
            if (c.getClient() != null) c.getClient().getName();
        }

        model.addAttribute("cases", myCases);
        model.addAttribute("user", user);

        // Build a map caseId -> expiresAt for display in the portal
        java.util.Map<String, java.time.LocalDateTime> expirations = new java.util.HashMap<>();
        try {
            List<com.gedavocat.model.Permission> perms = permissionRepository.findActiveByLawyerId(user.getId());
            for (com.gedavocat.model.Permission p : perms) {
                if (p.getCaseEntity() != null && p.getExpiresAt() != null) {
                    expirations.put(p.getCaseEntity().getId(), p.getExpiresAt());
                }
            }
        } catch (Exception ignore) {}
        model.addAttribute("expirations", expirations);

        return "collaborator-portal/cases";
    }

    @GetMapping("/{caseId}")
    public String viewMyCase(@PathVariable String caseId, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);

        // Vérifier que ce collaborateur a une permission active sur le dossier
        if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "collaborator-portal/pending";
        }

        List<Document> documents = documentService.getLatestVersions(caseId);

        List<Appointment> appointments;
        try {
            appointments = appointmentService.getAppointmentsByCase(caseId);
            for (Appointment a : appointments) {
                if (a.getClient() != null) a.getClient().getName();
                if (a.getRelatedCase() != null) a.getRelatedCase().getName();
            }
        } catch (Exception e) {
            appointments = Collections.emptyList();
        }

        if (caseEntity.getLawyer() != null) {
            caseEntity.getLawyer().getName();
            caseEntity.getLawyer().getEmail();
        }

        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documents);
        model.addAttribute("appointments", appointments);
        model.addAttribute("user", user);

        return "collaborator-portal/case-detail";
    }

    @GetMapping("/{caseId}/documents")
    public String listMyDocuments(@PathVariable String caseId, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);
        if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "collaborator-portal/pending";
        }
        List<Document> documents = documentService.getLatestVersions(caseId);
        model.addAttribute("documents", documents);
        model.addAttribute("case", caseEntity);
        model.addAttribute("user", user);
        return "collaborator-portal/documents";
    }

    @PostMapping("/{caseId}/upload")
    public String uploadDocument(@PathVariable String caseId,
                                 @RequestParam("file") MultipartFile file,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à ce dossier.");
                return "redirect:/my-cases-collab";
            }
            MultipartFile fileToStore = applyWatermarkIfPdf(file, WatermarkService.WATERMARK_COPIE);
            documentService.uploadDocument(caseId, fileToStore, user.getId(), "COLLABORATOR");
            redirectAttributes.addFlashAttribute("message", "Document uploadé avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload : " + e.getMessage());
        }
        return "redirect:/my-cases-collab/" + caseId;
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String documentId, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            Document document = documentService.getDocumentById(documentId);
            String caseIdFromDoc = document.getCaseEntity() != null ? document.getCaseEntity().getId() : null;
            if (caseIdFromDoc == null || !caseService.hasCollaboratorAccess(caseIdFromDoc, user.getId())) {
                return ResponseEntity.status(403).build();
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

    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<Resource> previewDocument(@PathVariable String documentId, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            Document document = documentService.getDocumentById(documentId);
            String caseIdFromDoc = document.getCaseEntity() != null ? document.getCaseEntity().getId() : null;
            if (caseIdFromDoc == null || !caseService.hasCollaboratorAccess(caseIdFromDoc, user.getId())) {
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

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) return "document";
        String sanitized = filename.replace("\\", "/");
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) sanitized = sanitized.substring(lastSlash + 1);
        sanitized = sanitized.replaceAll("[\\r\\n\"]", "_");
        return sanitized.isEmpty() ? "document" : sanitized;
    }

    @GetMapping("/{caseId}/export-zip")
    public ResponseEntity<Resource> exportCaseDocumentsZip(@PathVariable String caseId, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            Case caseEntity = caseService.getCaseById(caseId);
            if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
                return ResponseEntity.status(403).build();
            }

            List<Document> documents = documentService.getLatestVersions(caseId);
            if (documents.isEmpty()) return ResponseEntity.noContent().build();

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

    // =====================
    // Profil collaborateur
    // =====================
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        model.addAttribute("user", user);
        return "collaborator-portal/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "barNumber", required = false) String barNumber,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        user.setPhone(phone);
        user.setBarNumber(barNumber);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("message", "Vos informations ont été mises à jour.");
        return "redirect:/my-cases-collab/profile";
    }
}
