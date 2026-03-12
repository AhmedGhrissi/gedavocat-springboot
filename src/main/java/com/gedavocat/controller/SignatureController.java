package com.gedavocat.controller;

import com.gedavocat.model.Document;
import com.gedavocat.model.Signature;
import com.gedavocat.model.User;
import com.gedavocat.model.Case;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.SignatureRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.DocumentService;
import com.gedavocat.service.NotificationService;
import com.gedavocat.service.YousignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur pour la signature électronique avec Yousign
 */
@Controller
@RequestMapping("/signatures")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
@Slf4j
@SuppressWarnings("null")
public class SignatureController {

    private final YousignService yousignService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final SignatureRepository signatureRepository;
    private final CaseRepository caseRepository;

    /**
     * Page principale des signatures
     */
    @GetMapping
    @Transactional
    public String listSignatures(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        model.addAttribute("user", user);
        model.addAttribute("isConfigured", yousignService.isConfigured());

        // Récupérer les signatures en cours et terminées depuis la base
        List<Signature> allSignatures = signatureRepository.findByRequestedByIdWithCase(user.getId());

        // Synchroniser le statut des signatures PENDING depuis Yousign
        for (Signature sig : allSignatures) {
            if (sig.getStatus() == Signature.SignatureStatus.PENDING) {
                syncSignatureStatus(sig);
            }
        }

        List<Signature> pendingSignatures = allSignatures.stream()
            .filter(s -> s.getStatus() == Signature.SignatureStatus.PENDING)
            .toList();
        List<Signature> completedSignatures = allSignatures.stream()
            .filter(s -> s.getStatus() == Signature.SignatureStatus.SIGNED)
            .toList();
        
        model.addAttribute("pendingSignatures", pendingSignatures);
        model.addAttribute("completedSignatures", completedSignatures);

        return "signatures/index";
    }

    /**
     * Page pour créer une nouvelle demande de signature
     */
    @GetMapping("/new")
    public String newSignature(
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String caseId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!yousignService.isConfigured()) {
            model.addAttribute("error", "Yousign n'est pas configuré. Veuillez configurer votre clé API dans les paramètres.");
            return "redirect:/settings";
        }

        model.addAttribute("user", user);

        // Dossiers de l'avocat (obligatoire pour la signature)
        List<Case> cases = caseRepository.findAllByLawyerIdWithClient(user.getId());
        model.addAttribute("cases", cases);

        // Si un dossier est sélectionné, charger ses documents PDF
        if (caseId != null && !caseId.isBlank()) {
            List<Document> caseDocuments = documentRepository.findByCaseIdAndNotDeleted(caseId)
                .stream()
                .filter(d -> "application/pdf".equals(d.getMimetype()) || d.getFilename().endsWith(".pdf"))
                .toList();
            model.addAttribute("documents", caseDocuments);
            model.addAttribute("selectedCaseId", caseId);
        }

        // Si un document est spécifié, le pré-remplir
        if (documentId != null) {
            Document document = documentRepository.findById(documentId).orElse(null);
            model.addAttribute("document", document);
            if (document != null && document.getCaseEntity() != null) {
                model.addAttribute("selectedCaseId", document.getCaseEntity().getId());
                // Charger les documents du dossier
                List<Document> caseDocuments = documentRepository.findByCaseIdAndNotDeleted(document.getCaseEntity().getId())
                    .stream()
                    .filter(d -> "application/pdf".equals(d.getMimetype()) || d.getFilename().endsWith(".pdf"))
                    .toList();
                model.addAttribute("documents", caseDocuments);
            }
        }

        // Niveaux de signature disponibles
        model.addAttribute("signatureLevels", new String[]{"simple", "advanced", "qualified"});

        return "signatures/new";
    }

    /**
     * API JSON — documents PDF d'un dossier (pour le JS dynamique)
     */
    @GetMapping("/api/cases/{caseId}/documents")
    @ResponseBody
    public List<Map<String, String>> getDocumentsByCase(
            @PathVariable String caseId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // Vérifier que le dossier appartient à l'avocat
        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null || !caseEntity.getLawyer().getId().equals(user.getId())) {
            return List.of();
        }
        
        return documentRepository.findByCaseIdAndNotDeleted(caseId)
            .stream()
            .filter(d -> "application/pdf".equals(d.getMimetype()) || d.getFilename().endsWith(".pdf"))
            .map(d -> Map.of("id", d.getId(), "name", d.getOriginalName()))
            .toList();
    }

    /**
     * Créer une demande de signature
     */
    @PostMapping("/create")
    @Transactional
    public String createSignature(
            @RequestParam(required = false) String documentId,
            @RequestParam String caseId,
            @RequestParam(required = false) MultipartFile uploadFile,
            @RequestParam String signerFirstName,
            @RequestParam String signerLastName,
            @RequestParam String signerEmail,
            @RequestParam(defaultValue = "advanced") String signatureLevel,
            @RequestParam(required = false) String signerPhone,
            @RequestParam(defaultValue = "7") int expirationDays,
            @RequestParam(required = false) String message,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            // SEC-10 FIX : valider les paramètres de signature
            if (signerEmail == null || !signerEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                redirectAttributes.addFlashAttribute("error", "Email du signataire invalide.");
                return "redirect:/signatures/new?caseId=" + caseId;
            }
            if (signerFirstName == null || signerFirstName.isBlank() || signerFirstName.length() > 100) {
                redirectAttributes.addFlashAttribute("error", "Prénom du signataire invalide.");
                return "redirect:/signatures/new?caseId=" + caseId;
            }
            if (signerLastName == null || signerLastName.isBlank() || signerLastName.length() > 100) {
                redirectAttributes.addFlashAttribute("error", "Nom du signataire invalide.");
                return "redirect:/signatures/new?caseId=" + caseId;
            }
            if (!java.util.Set.of("advanced", "qualified", "simple").contains(signatureLevel)) {
                signatureLevel = "advanced";
            }
            if (expirationDays < 1 || expirationDays > 90) {
                expirationDays = 7;
            }

            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            // Vérifier que le dossier appartient à l'avocat
            Case caseEntity = caseRepository.findById(caseId)
                    .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));
            if (!caseEntity.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à ce dossier");
                return "redirect:/signatures";
            }

            Document document;

            // Soit on utilise un document existant, soit on uploade un nouveau
            if (uploadFile != null && !uploadFile.isEmpty()) {
                // Upload du nouveau document dans le dossier
                document = documentService.uploadDocument(caseId, uploadFile, user.getId(), user.getRole().name());
            } else if (documentId != null && !documentId.isBlank()) {
                document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new RuntimeException("Document non trouvé"));
                // Vérifier que le document appartient au bon dossier
                if (document.getCaseEntity() == null || !document.getCaseEntity().getId().equals(caseId)) {
                    redirectAttributes.addFlashAttribute("error", "Le document ne correspond pas au dossier");
                    return "redirect:/signatures/new?caseId=" + caseId;
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner ou importer un document PDF");
                return "redirect:/signatures/new?caseId=" + caseId;
            }

            // Créer la demande de signature
            String signerName = signerFirstName.trim() + " " + signerLastName.trim();
            byte[] fileBytes = documentService.downloadDocument(document.getId(), user.getId());
            Map<String, Object> result = yousignService.createSignatureRequest(
                    fileBytes,
                    document.getOriginalName(),
                    signerFirstName.trim(),
                    signerLastName.trim(),
                    signerEmail,
                    signatureLevel
            );

            String signatureId = (String) result.get("id");

            // Sauvegarder la demande de signature en base
            Signature signature = new Signature();
            signature.setId(signatureId != null ? signatureId : java.util.UUID.randomUUID().toString());
            signature.setYousignSignatureRequestId(signatureId);
            signature.setDocument(document);
            signature.setDocumentName(document.getOriginalName());
            signature.setSignerName(signerName);
            signature.setSignerEmail(signerEmail);
            signature.setLevel(signatureLevel);
            signature.setStatus(Signature.SignatureStatus.PENDING);
            signature.setRequestedBy(user);
            signature.setCaseEntity(caseEntity);
            signatureRepository.save(signature);

            // Notification pour l'avocat
            notificationService.notifySignaturePending(user, signerName, document.getOriginalName(), signature.getId());

            // Notification pour le client :
            // 1. Par email du signataire (s'il a un compte)
            // 2. Par le client lié au dossier (si différent)
            java.util.Set<String> notifiedUserIds = new java.util.HashSet<>();
            userRepository.findByEmail(signerEmail).ifPresent(clientUser -> {
                notificationService.notifyClientSignatureRequest(clientUser, user.getName(), document.getOriginalName());
                notifiedUserIds.add(clientUser.getId());
            });
            // Notifier aussi le client du dossier s'il a un compte utilisateur
            if (caseEntity.getClient() != null && caseEntity.getClient().getClientUser() != null) {
                User clientUser = caseEntity.getClient().getClientUser();
                if (!notifiedUserIds.contains(clientUser.getId())) {
                    notificationService.notifyClientSignatureRequest(clientUser, user.getName(), document.getOriginalName());
                }
            }

            redirectAttributes.addFlashAttribute("message",
                "Demande de signature envoyée à " + signerEmail);

            return "redirect:/signatures/" + signature.getId();

        } catch (Exception e) {
            log.error("Erreur création signature", e);
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de la création de la demande de signature");
            return "redirect:/signatures/new";
        }
    }

    /**
     * Voir le détail d'une signature
     */
    @GetMapping("/{signatureId}")
    public String viewSignature(
            @PathVariable String signatureId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Signature signature = signatureRepository.findByIdWithCase(signatureId)
                    .orElseThrow(() -> new RuntimeException("Signature introuvable"));

            // Vérifier que la signature appartient à l'utilisateur
            if (!signature.getRequestedBy().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à cette signature.");
                return "redirect:/signatures";
            }

            // Synchroniser le statut depuis Yousign si la signature est en attente
            if (signature.getStatus() == Signature.SignatureStatus.PENDING) {
                syncSignatureStatus(signature);
            }

            model.addAttribute("user", user);
            model.addAttribute("signature", signature);

            return "signatures/view";

        } catch (Exception e) {
            log.error("Erreur récupération signature {}", signatureId, e);
            redirectAttributes.addFlashAttribute("error", "Signature introuvable");
            return "redirect:/signatures";
        }
    }

    /**
     * Télécharger le document signé — retourne le fichier directement
     */
    @GetMapping("/{signatureId}/download")
    public ResponseEntity<byte[]> downloadSignedDocument(
            @PathVariable String signatureId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        try {
            // Vérifier la propriété
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            Signature sig = signatureRepository.findById(signatureId).orElse(null);
            if (sig != null && !sig.getRequestedBy().getId().equals(user.getId())) {
                return ResponseEntity.status(403).build();
            }

            byte[] signedDocument = yousignService.downloadSignedDocument(signatureId);

            // Mettre à jour le statut en base si besoin
            signatureRepository.findByYousignSignatureRequestId(signatureId).ifPresent(s -> {
                if (s.getStatus() != Signature.SignatureStatus.SIGNED) {
                    s.setStatus(Signature.SignatureStatus.SIGNED);
                    s.setSignedAt(java.time.LocalDateTime.now());
                    signatureRepository.save(s);
                }
            });

            // Trouver le nom du fichier
            String filename = signatureRepository
                .findByYousignSignatureRequestId(signatureId)
                .map(s -> "signed_" + s.getDocumentName())
                .orElse("document_signe.pdf");

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(signedDocument);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Annuler une demande de signature
     */
    @PostMapping("/{signatureId}/cancel")
    public String cancelSignature(
            @PathVariable String signatureId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            Signature sig = signatureRepository.findById(signatureId).orElse(null);
            if (sig != null && !sig.getRequestedBy().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé.");
                return "redirect:/signatures";
            }
            yousignService.cancelSignatureRequest(signatureId);

            redirectAttributes.addFlashAttribute("message", "Demande de signature annulée");
            return "redirect:/signatures";

        } catch (Exception e) {
            log.error("Erreur annulation signature {}", signatureId, e);
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de l'annulation de la signature");
            return "redirect:/signatures/" + signatureId;
        }
    }

    /**
     * Relancer un signataire — route simple sans signerId (utilisée depuis la liste)
     */
    @PostMapping("/{signatureId}/remind")
    public String remindSignerSimple(
            @PathVariable String signatureId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            // Ownership check
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            Signature sig = signatureRepository.findById(signatureId).orElse(null);
            if (sig != null && !sig.getRequestedBy().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/signatures";
            }

            // Relancer sans signerId spécifique (tous les signataires en attente)
            yousignService.remindSigner(signatureId, "");
            redirectAttributes.addFlashAttribute("message", "Relance envoyée au signataire");
        } catch (Exception e) {
            log.error("Erreur relance signataire signature {}", signatureId, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la relance");
        }
        return "redirect:/signatures";
    }

    /**
     * Synchroniser le statut d'une signature depuis l'API Yousign
     * Yousign statuses: draft, ongoing, done, declined, expired, canceled
     */
    private void syncSignatureStatus(Signature signature) {
        if (signature.getYousignSignatureRequestId() == null || !yousignService.isConfigured()) {
            return;
        }
        try {
            Map<String, Object> yousignData = yousignService.getSignatureStatus(
                    signature.getYousignSignatureRequestId());
            if (yousignData == null) return;

            String yousignStatus = (String) yousignData.get("status");
            if (yousignStatus == null) return;

            Signature.SignatureStatus newStatus = mapYousignStatus(yousignStatus);
            if (newStatus != null && newStatus != signature.getStatus()) {
                Signature.SignatureStatus oldStatus = signature.getStatus();
                signature.setStatus(newStatus);
                if (newStatus == Signature.SignatureStatus.SIGNED) {
                    signature.setSignedAt(LocalDateTime.now());
                }
                signatureRepository.save(signature);
                log.info("Signature {} : statut mis à jour {} → {}",
                        signature.getId(), oldStatus, newStatus);

                // Notifications
                if (newStatus == Signature.SignatureStatus.SIGNED) {
                    notificationService.notifySignatureSigned(
                            signature.getRequestedBy(),
                            signature.getSignerName(),
                            signature.getDocumentName(),
                            signature.getId());
                } else if (newStatus == Signature.SignatureStatus.REJECTED) {
                    notificationService.notifySignatureRejected(
                            signature.getRequestedBy(),
                            signature.getSignerName(),
                            signature.getDocumentName(),
                            signature.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de synchroniser le statut de la signature {} : {}",
                    signature.getId(), e.getMessage());
        }
    }

    /**
     * Mapper les statuts Yousign vers les statuts locaux
     */
    private Signature.SignatureStatus mapYousignStatus(String yousignStatus) {
        return switch (yousignStatus) {
            case "draft" -> Signature.SignatureStatus.DRAFT;
            case "ongoing", "approval" -> Signature.SignatureStatus.PENDING;
            case "done" -> Signature.SignatureStatus.SIGNED;
            case "declined", "canceled" -> Signature.SignatureStatus.REJECTED;
            case "expired", "deleted" -> Signature.SignatureStatus.EXPIRED;
            default -> null;
        };
    }

    /**
     * Relancer un signataire — route avec signerId
     */
    @PostMapping("/{signatureId}/remind/{signerId}")
    public String remindSigner(
            @PathVariable String signatureId,
            @PathVariable String signerId,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            // Ownership check
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            Signature sig = signatureRepository.findById(signatureId).orElse(null);
            if (sig != null && !sig.getRequestedBy().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/signatures";
            }

            yousignService.remindSigner(signatureId, signerId);

            redirectAttributes.addFlashAttribute("message", "Relance envoyée au signataire");
            return "redirect:/signatures/" + signatureId;

        } catch (Exception e) {
            log.error("Erreur relance signataire {} signature {}", signerId, signatureId, e);
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de la relance");
            return "redirect:/signatures/" + signatureId;
        }
    }
}
