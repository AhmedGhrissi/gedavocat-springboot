package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.DocumentService;
import com.gedavocat.service.WatermarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.gedavocat.util.ByteArrayMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur de gestion des documents
 * RÉSERVÉ AUX AVOCATS - Les clients utilisent ClientPortalController
 */
@Slf4j
@Controller
@RequestMapping("/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'LAWYER_SECONDARY', 'AVOCAT_ADMIN')")
@SuppressWarnings("null")
public class DocumentController {

    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final com.gedavocat.service.CaseService caseService;
    private final WatermarkService watermarkService;
    private final com.gedavocat.repository.DocumentDeletionRequestRepository documentDeletionRequestRepository;
    private final com.gedavocat.service.NotificationService notificationService;

    /**
     * Page d'accueil des documents - liste tous les documents de l'utilisateur
     */
    @GetMapping
    public String documentsHome(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<Document> allDocuments = documentService.getAllDocumentsByUser(user.getId());
        
        // Grouper les documents par dossier
        Map<Case, List<Document>> docsByCase = allDocuments.stream()
                .filter(doc -> doc.getCaseEntity() != null)
                .collect(Collectors.groupingBy(
                        Document::getCaseEntity,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        
        // Récupérer tous les dossiers accessibles pour le modal d'upload
        List<Case> accessibleCases = caseService.getAccessibleCases(user.getId());
        
        model.addAttribute("documents", allDocuments);
        model.addAttribute("docsByCase", docsByCase);
        model.addAttribute("cases", accessibleCases);
        model.addAttribute("user", user);
        
        return "documents/index";
    }

    /**
     * Liste des documents d'un dossier
     */
    @GetMapping("/case/{caseId}")
    public String listDocuments(
            @PathVariable String caseId,
            Model model,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);
        
        // SÉCURITÉ : vérifier que l'utilisateur a accès au dossier
        Case caseEntity = caseService.getCaseById(caseId);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé à ce dossier");
        }
        
        // Récupérer tous les dossiers accessibles pour le modal d'upload
        List<Case> accessibleCases = caseService.getAccessibleCases(user.getId());
        
        model.addAttribute("documents", documentService.getLatestVersions(caseId));
        model.addAttribute("caseId", caseId);
        model.addAttribute("cases", accessibleCases);
        model.addAttribute("user", user);
        return "documents/list";
    }

    /**
     * Corbeille (documents supprimés)
     */
    @GetMapping("/case/{caseId}/trash")
    public String viewTrash(
            @PathVariable String caseId,
            Model model,
            Authentication authentication
    ) {
        // Vérification ownership : le dossier doit appartenir à l'avocat connecté
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
        }
        model.addAttribute("documents", documentService.getDeletedDocuments(caseId));
        model.addAttribute("caseId", caseId);
        return "documents/trash";
    }

    /**
     * Upload de documents.
     * Filigrane CONFIDENTIEL appliqué de manière persistante sur les PDF au moment de l'upload.
     */
    @PostMapping("/case/{caseId}/upload")
    public String uploadDocument(
            @PathVariable String caseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner un fichier");
            return "redirect:/cases/" + caseId;
        }

        try {
            User user = getCurrentUser(authentication);
            // Vérification : le dossier doit appartenir à l'avocat connecté
            Case caseEntity = caseService.getCaseById(caseId);
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à ce dossier");
                return "redirect:/cases";
            }
            // Filigrane CONFIDENTIEL persistant sur les PDF
            MultipartFile fileToStore = applyWatermarkIfPdf(file, WatermarkService.WATERMARK_CONFIDENTIEL);
            documentService.uploadDocument(caseId, fileToStore, user.getId(), user.getRole().name());
            redirectAttributes.addFlashAttribute("message", "Document uploadé avec succès");
        } catch (Exception e) {
            log.error("Erreur upload document dossier {}", caseId, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload du document");
        }

        return "redirect:/cases/" + caseId;
    }

    /**
     * API Upload pour AJAX
     */
    @PostMapping("/case/{caseId}/upload-ajax")
    @ResponseBody
    public ResponseEntity<?> uploadDocumentAjax(
            @PathVariable String caseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "Veuillez sélectionner un fichier"));
        }

        try {
            User user = getCurrentUser(authentication);
            // SÉCURITÉ : vérifier que l'utilisateur a accès au dossier
            Case caseEntity = caseService.getCaseById(caseId);
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
                return ResponseEntity.status(403)
                    .body(Map.of("success", false, "message", "Accès non autorisé à ce dossier"));
            }
            // Filigrane CONFIDENTIEL persistant sur les PDF
            MultipartFile fileToStore = applyWatermarkIfPdf(file, WatermarkService.WATERMARK_CONFIDENTIEL);
            Document document = documentService.uploadDocument(caseId, fileToStore, user.getId(), user.getRole().name());
            return ResponseEntity.ok()
                .body(Map.of("success", true, "message", "Document uploadé avec succès", "documentId", document.getId()));
        } catch (Exception e) {
            log.error("Erreur upload AJAX dossier {}", caseId, e);
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "Erreur lors de l'upload du document"));
        }
    }

    /**
     * Upload d'une nouvelle version
     */
    @PostMapping("/{documentId}/upload-version")
    public String uploadNewVersion(
            @PathVariable String documentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner un fichier");
            return "redirect:/documents/" + documentId;
        }

        try {
            User user = getCurrentUser(authentication);
            // SÉCURITÉ : vérifier ownership du document parent
            Document parentDoc = documentService.getDocumentById(documentId);
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !parentDoc.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/documents";
            }
            Document document = documentService.uploadNewVersion(documentId, file, user.getId());
            redirectAttributes.addFlashAttribute("message", "Nouvelle version uploadée (v" + document.getVersion() + ")");
            return "redirect:/cases/" + document.getCaseEntity().getId();
        } catch (Exception e) {
            log.error("Erreur upload nouvelle version document {}", documentId, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload de la nouvelle version");
            return "redirect:/documents/" + documentId;
        }
    }

    /**
     * Télécharger un document.
     * Le filigrane est déjà appliqué sur le fichier stocké (persistant).
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String id,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            byte[] fileBytes = documentService.downloadDocument(id, user.getId());
            Document document = documentService.getDocumentById(id);

            // SEC-01 FIX : vérifier ownership selon le rôle
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isClient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"));
            if (!isAdmin) {
                if (isClient) {
                    // SEC-01 FIX : vérifier que le client est bien associé au dossier
                    Case caseEntity = document.getCaseEntity();
                    boolean isClientOfCase = caseEntity.getClient() != null
                        && caseEntity.getClient().getClientUser() != null
                        && caseEntity.getClient().getClientUser().getId().equals(user.getId());
                    if (!isClientOfCase) {
                        throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
                    }
                } else if (!document.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
                }
            }

            Resource resource = new ByteArrayResource(fileBytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimetype()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + document.getOriginalName().replaceAll("[\\r\\n\"\\\\]", "_") + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Erreur téléchargement document {}", id, e);
            throw new RuntimeException("Erreur lors du téléchargement du document");
        }
    }

    /**
     * Prévisualisation d'un document (affichage inline dans le navigateur).
     * Le filigrane est déjà appliqué sur le fichier stocké (persistant).
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> previewDocument(
            @PathVariable String id,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            byte[] fileBytes = documentService.downloadDocument(id, user.getId());
            Document document = documentService.getDocumentById(id);

            // SEC-01 FIX : vérifier ownership selon le rôle
            boolean isAdminPreview = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isClientPreview = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"));
            if (!isAdminPreview) {
                if (isClientPreview) {
                    Case caseEntity = document.getCaseEntity();
                    boolean isClientOfCase = caseEntity.getClient() != null
                        && caseEntity.getClient().getClientUser() != null
                        && caseEntity.getClient().getClientUser().getId().equals(user.getId());
                    if (!isClientOfCase) {
                        throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
                    }
                } else if (!document.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
                }
            }

            Resource resource = new ByteArrayResource(fileBytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(document.getMimetype()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + document.getOriginalName().replaceAll("[\\r\\n\"\\\\]", "_") + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Erreur prévisualisation document {}", id, e);
            throw new RuntimeException("Erreur lors de la prévisualisation du document");
        }
    }

    /**
     * Supprimer un document (corbeille)
     */
    @PostMapping("/{id}/delete")
    public String deleteDocument(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            Document document = documentService.getDocumentById(id);
            String caseId = document.getCaseEntity().getId();

            // SÉCURITÉ : vérifier ownership
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !document.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
            }

            documentService.softDeleteDocument(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Document supprimé (déplacé vers la corbeille)");
            return "redirect:/cases/" + caseId;
        } catch (Exception e) {
            log.error("Erreur suppression document {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression du document");
            return "redirect:/documents/" + id;
        }
    }

    /**
     * Restaurer un document
     */
    @PostMapping("/{id}/restore")
    public String restoreDocument(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            Document document = documentService.getDocumentById(id);
            String caseId = document.getCaseEntity().getId();

            // SÉCURITÉ : vérifier ownership
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !document.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
            }

            documentService.restoreDocument(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Document restauré avec succès");
            return "redirect:/cases/" + caseId;
        } catch (Exception e) {
            log.error("Erreur restauration document {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la restauration du document");
            return "redirect:/documents";
        }
    }

    /**
     * Suppression définitive
     */
    @PostMapping("/{id}/delete-permanent")
    public String permanentDeleteDocument(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            Document document = documentService.getDocumentById(id);
            String caseId = document.getCaseEntity().getId();

            // SÉCURITÉ : vérifier ownership
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !document.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Accès non autorisé");
            }

            documentService.permanentDeleteDocument(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Document supprimé définitivement");
            return "redirect:/documents/case/" + caseId + "/trash";
        } catch (Exception e) {
            log.error("Erreur suppression définitive document {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression définitive");
            return "redirect:/documents/" + id;
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

    // =========================================================================
    // Gestion des demandes de suppression de documents
    // =========================================================================

    /**
     * Liste les demandes de suppression en attente pour l'avocat.
     */
    @GetMapping("/deletion-requests")
    @ResponseBody
    public ResponseEntity<?> getDeletionRequests(Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            var requests = documentDeletionRequestRepository.findByLawyerAndStatus(
                    user.getId(),
                    com.gedavocat.model.DocumentDeletionRequest.RequestStatus.PENDING);
            var result = requests.stream().map(r -> java.util.Map.of(
                    "id", r.getId(),
                    "documentName", r.getDocument().getOriginalName(),
                    "documentId", r.getDocument().getId(),
                    "caseId", r.getDocument().getCaseEntity().getId(),
                    "caseName", r.getDocument().getCaseEntity().getName(),
                    "requestedBy", r.getRequestedBy().getName(),
                    "reason", r.getReason() != null ? r.getReason() : "",
                    "createdAt", r.getCreatedAt().toString()
            )).collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur récupération demandes de suppression", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * L'avocat approuve la demande de suppression → soft delete du document.
     */
    @PostMapping("/deletion-requests/{requestId}/approve")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> approveDeletionRequest(
            @PathVariable String requestId,
            Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            var request = documentDeletionRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Demande non trouvée"));

            // SÉCURITÉ : vérifier que l'avocat possède le dossier
            Document doc = request.getDocument();
            if (!doc.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(java.util.Map.of("success", false, "message", "Accès non autorisé"));
            }

            // Approuver et soft-delete le document
            request.setStatus(com.gedavocat.model.DocumentDeletionRequest.RequestStatus.APPROVED);
            request.setReviewedBy(user);
            request.setReviewedAt(java.time.LocalDateTime.now());
            documentDeletionRequestRepository.save(request);

            documentService.softDeleteDocument(doc.getId(), user.getId());

            // Notifier le client
            if (request.getRequestedBy() != null) {
                notificationService.create(
                    request.getRequestedBy(),
                    "DOCUMENT_DELETION_APPROVED",
                    "Suppression approuvée",
                    "Votre demande de suppression du document « " + doc.getOriginalName() + " » a été approuvée",
                    "/my-cases/" + doc.getCaseEntity().getId(),
                    "fa-check-circle",
                    "success"
                );
            }

            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Document supprimé"));
        } catch (Exception e) {
            log.error("Erreur approbation demande de suppression {}", requestId, e);
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "Erreur lors de l'approbation"));
        }
    }

    /**
     * L'avocat rejette la demande de suppression.
     */
    @PostMapping("/deletion-requests/{requestId}/reject")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> rejectDeletionRequest(
            @PathVariable String requestId,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            var request = documentDeletionRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Demande non trouvée"));

            // SÉCURITÉ : vérifier ownership
            Document doc = request.getDocument();
            if (!doc.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(java.util.Map.of("success", false, "message", "Accès non autorisé"));
            }

            request.setStatus(com.gedavocat.model.DocumentDeletionRequest.RequestStatus.REJECTED);
            request.setReviewedBy(user);
            request.setReviewedAt(java.time.LocalDateTime.now());
            request.setReviewComment(body != null ? body.get("comment") : null);
            documentDeletionRequestRepository.save(request);

            // Notifier le client
            if (request.getRequestedBy() != null) {
                notificationService.create(
                    request.getRequestedBy(),
                    "DOCUMENT_DELETION_REJECTED",
                    "Suppression refusée",
                    "Votre demande de suppression du document « " + doc.getOriginalName() + " » a été refusée",
                    "/my-cases/" + doc.getCaseEntity().getId(),
                    "fa-times-circle",
                    "danger"
                );
            }

            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Demande rejetée"));
        } catch (Exception e) {
            log.error("Erreur rejet demande de suppression {}", requestId, e);
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "Erreur lors du rejet"));
        }
    }
}
