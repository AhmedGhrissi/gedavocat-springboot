package com.gedavocat.controller;

import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.YousignService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur pour la signature électronique avec Yousign
 */
@Controller
@RequestMapping("/signatures")
@RequiredArgsConstructor
public class SignatureController {

    private final YousignService yousignService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * Page principale des signatures
     */
    @GetMapping
    public String listSignatures(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        model.addAttribute("user", user);
        model.addAttribute("isConfigured", yousignService.isConfigured());

        // TODO: Récupérer les signatures en cours depuis la base
        model.addAttribute("pendingSignatures", new ArrayList<>());
        model.addAttribute("completedSignatures", new ArrayList<>());

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
    public String createSignature(
            @RequestParam String documentId,
            @RequestParam String signerName,
            @RequestParam String signerEmail,
            @RequestParam(defaultValue = "advanced") String signatureLevel,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document non trouvé"));

            // Créer la demande de signature
            Map<String, Object> result = yousignService.createSignatureRequest(
                    document.getPath(),
                    signerName,
                    signerEmail,
                    signatureLevel
            );

            String signatureId = (String) result.get("id");

            // TODO: Sauvegarder la demande de signature en base

            redirectAttributes.addFlashAttribute("message",
                "Demande de signature envoyée à " + signerEmail);

            return "redirect:/signatures/" + signatureId;

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
            Model model
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            // Récupérer le statut de la signature
            Map<String, Object> signatureStatus = yousignService.getSignatureStatus(signatureId);

            model.addAttribute("user", user);
            model.addAttribute("signature", signatureStatus);
            model.addAttribute("signatureId", signatureId);

            return "signatures/view";

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la récupération de la signature");
            return "redirect:/signatures";
        }
    }

    /**
     * Télécharger le document signé
     */
    @GetMapping("/{signatureId}/download")
    public String downloadSignedDocument(
            @PathVariable String signatureId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            byte[] signedDocument = yousignService.downloadSignedDocument(signatureId);

            // TODO: Sauvegarder le document signé et retourner le fichier

            redirectAttributes.addFlashAttribute("message", "Document signé téléchargé");
            return "redirect:/signatures/" + signatureId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors du téléchargement: " + e.getMessage());
            return "redirect:/signatures/" + signatureId;
        }
    }

    /**
     * Annuler une demande de signature
     */
    @PostMapping("/{signatureId}/cancel")
    public String cancelSignature(
            @PathVariable String signatureId,
            RedirectAttributes redirectAttributes
    ) {
        try {
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
     * Relancer un signataire
     */
    @PostMapping("/{signatureId}/remind/{signerId}")
    public String remindSigner(
            @PathVariable String signatureId,
            @PathVariable String signerId,
            RedirectAttributes redirectAttributes
    ) {
        try {
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
