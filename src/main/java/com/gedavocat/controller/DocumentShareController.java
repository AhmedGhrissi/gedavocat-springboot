package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.DocumentService;
import com.gedavocat.service.DocumentShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST pour gérer le partage granulaire de documents
 * avec les collaborateurs et huissiers.
 */
@Slf4j
@RestController
@RequestMapping("/api/document-shares")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
public class DocumentShareController {

    private final DocumentShareService documentShareService;
    private final DocumentService documentService;
    private final CaseService caseService;
    private final UserRepository userRepository;

    /**
     * Toggle le partage d'un document pour un rôle donné.
     *
     * POST /api/document-shares/toggle
     * Body: { documentId, caseId, role (LAWYER_SECONDARY ou HUISSIER), shared (true/false) }
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleShare(
            @RequestParam String documentId,
            @RequestParam String caseId,
            @RequestParam String role,
            @RequestParam boolean shared,
            Authentication authentication
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = getCurrentUser(authentication);
            Case caseEntity = caseService.getCaseById(caseId);

            // Vérifier que l'utilisateur est propriétaire du dossier ou admin
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Accès non autorisé"));
            }

            User.UserRole targetRole = User.UserRole.valueOf(role);
            Document document = documentService.getDocumentById(documentId);

            // IDOR FIX : vérifier que le document appartient bien au dossier demandé
            if (document.getCaseEntity() == null || !document.getCaseEntity().getId().equals(caseId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Ce document n'appartient pas à ce dossier"));
            }

            if (shared) {
                documentShareService.shareDocument(document, caseEntity, targetRole, false, user.getId());
            } else {
                documentShareService.unshareDocument(documentId, targetRole);
            }

            result.put("success", true);
            result.put("documentId", documentId);
            result.put("role", role);
            result.put("shared", shared);
            log.info("[DocShare] {} document {} avec {} pour dossier {}",
                    shared ? "Partagé" : "Départagé", documentId, role, caseId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[DocShare] Erreur toggle: {}", e.getMessage());
            result.put("success", false);
            result.put("error", "Erreur lors du partage du document");
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Partage/départage en masse tous les documents d'un dossier pour un rôle.
     *
     * POST /api/document-shares/bulk-toggle
     * Params: caseId, role, shared
     */
    @PostMapping("/bulk-toggle")
    public ResponseEntity<Map<String, Object>> bulkToggle(
            @RequestParam String caseId,
            @RequestParam String role,
            @RequestParam boolean shared,
            Authentication authentication
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = getCurrentUser(authentication);
            Case caseEntity = caseService.getCaseById(caseId);

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !caseEntity.getLawyer().getId().equals(user.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Accès non autorisé"));
            }

            User.UserRole targetRole = User.UserRole.valueOf(role);
            List<Document> documents = documentService.getLatestVersions(caseId);
            documentShareService.bulkShare(caseId, documents, caseEntity, targetRole, shared, user.getId());

            result.put("success", true);
            result.put("caseId", caseId);
            result.put("role", role);
            result.put("shared", shared);
            result.put("count", documents.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[DocShare] Erreur bulk toggle: {}", e.getMessage());
            result.put("success", false);
            result.put("error", "Erreur lors du partage en masse");
            return ResponseEntity.badRequest().body(result);
        }
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
