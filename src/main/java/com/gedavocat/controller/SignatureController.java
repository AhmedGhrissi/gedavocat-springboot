package com.gedavocat.controller;

import com.gedavocat.model.Document;
import com.gedavocat.model.Signature;
import com.gedavocat.model.User;
import com.gedavocat.model.Case;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.SignatureRepository;
import com.gedavocat.repository.UserRepository;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur pour la signature électronique avec Yousign
 */
@Controller
@RequestMapping("/signatures")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
public class SignatureController {

    private final YousignService yousignService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final SignatureRepository signatureRepository;
    private final CaseRepository caseRepository;

    /**
     * Page principale des signatures
     */
    @GetMapping
    public String listSignatures(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        model.addAttribute("user", user);
        model.addAttribute("isConfigured", yousignService.isConfigured());

        // Récupérer les signatures en cours et terminées depuis la base
        List<Signature> allSignatures = signatureRepository.findByRequestedByIdWithCase(user.getId());
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

        // Documents de l'avocat connecté uniquement (sécurité) — avec le Case chargé
        List<Document> allDocuments = documentRepository.findByLawyerIdWithCase(user.getId());
        model.addAttribute("documents", allDocuments);

        // Dossiers de l'avocat pour le filtre par dossier
        List<Case> cases = caseRepository.findAllByLawyerIdWithClient(user.getId());
        model.addAttribute("cases", cases);

        // Si un document est spécifié, le pré-remplir
        if (documentId != null) {
            Document document = documentRepository.findById(documentId).orElse(null);
            model.addAttribute("document", document);
        }

        // Niveaux de signature disponibles
        model.addAttribute("signatureLevels", new String[]{"simple", "advanced", "qualified"});

        return "signatures/new";
    }

    /**
     * Créer une demande de signature
     */
    @PostMapping("/create")
    @Transactional
    public String createSignature(
            @RequestParam String documentId,
            @RequestParam String signerName,
            @RequestParam String signerEmail,
            @RequestParam(defaultValue = "advanced") String signatureLevel,
            @RequestParam(required = false) String signerPhone,
            @RequestParam(defaultValue = "7") int expirationDays,
            @RequestParam(required = false) String message,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document non trouvé"));

            // Ownership check: verify document belongs to authenticated lawyer
            if (document.getCaseEntity() == null || document.getCaseEntity().getLawyer() == null
                    || !document.getCaseEntity().getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à ce document");
                return "redirect:/signatures";
            }

            // Créer la demande de signature
            Map<String, Object> result = yousignService.createSignatureRequest(
                    document.getPath(),
                    signerName,
                    signerEmail,
                    signatureLevel
            );

            String signatureId = (String) result.get("id");

            // Sauvegarder la demande de signature en base
            Signature signature = new Signature();
            signature.setId(signatureId != null ? signatureId : java.util.UUID.randomUUID().toString());
            signature.setYousignSignatureRequestId(signatureId);
            signature.setDocument(document);
            signature.setDocumentName(document.getFilename());
            signature.setSignerName(signerName);
            signature.setSignerEmail(signerEmail);
            signature.setLevel(signatureLevel);
            signature.setStatus(Signature.SignatureStatus.PENDING);
            signature.setRequestedBy(user);
            // Lier au dossier via le document
            if (document.getCaseEntity() != null) {
                signature.setCaseEntity(document.getCaseEntity());
            }
            signatureRepository.save(signature);

            redirectAttributes.addFlashAttribute("message",
                "Demande de signature envoyée à " + signerEmail);

            return "redirect:/signatures/" + signature.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de la création de la signature: " + e.getMessage());
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

            Signature signature = signatureRepository.findById(signatureId)
                    .orElseThrow(() -> new RuntimeException("Signature introuvable"));

            // Vérifier que la signature appartient à l'utilisateur
            if (!signature.getRequestedBy().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à cette signature.");
                return "redirect:/signatures";
            }

            model.addAttribute("user", user);
            model.addAttribute("signature", signature);

            return "signatures/view";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Signature introuvable : " + e.getMessage());
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
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de l'annulation: " + e.getMessage());
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
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la relance: " + e.getMessage());
        }
        return "redirect:/signatures";
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
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de la relance: " + e.getMessage());
            return "redirect:/signatures/" + signatureId;
        }
    }
}
