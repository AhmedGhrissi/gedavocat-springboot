package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.RPVAService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Contrôleur pour les communications RPVA (e-Barreau)
 */
@Controller
@RequestMapping("/rpva")
@RequiredArgsConstructor
public class RPVAController {

    private final RPVAService rpvaService;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    /**
     * Page principale RPVA
     */
    @GetMapping
    public String index(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        model.addAttribute("user", user);
        model.addAttribute("isConfigured", rpvaService.isConfigured());

        // TODO: Récupérer les communications depuis la base
        model.addAttribute("sentCommunications", new ArrayList<>());
        model.addAttribute("receivedCommunications", new ArrayList<>());

        return "rpva/index";
    }

    /**
     * Page des communications reçues
     */
    @GetMapping("/received")
    public String receivedCommunications(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            if (!rpvaService.isConfigured()) {
                model.addAttribute("error", "RPVA n'est pas configuré");
                return "redirect:/settings";
            }

            // Récupérer les communications des 30 derniers jours
            Map<String, Object> communications = rpvaService.getReceivedCommunications(
                    LocalDateTime.now().minusDays(30),
                    LocalDateTime.now(),
                    status != null ? status : "all"
            );

            model.addAttribute("user", user);
            model.addAttribute("communications", communications);
            model.addAttribute("selectedStatus", status);

            return "rpva/received";

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la récupération: " + e.getMessage());
            return "rpva/index";
        }
    }

    /**
     * Page pour envoyer une communication
     */
    @GetMapping("/send")
    public String sendCommunicationPage(
            @RequestParam(required = false) String caseId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!rpvaService.isConfigured()) {
            model.addAttribute("error", "RPVA n'est pas configuré");
            return "redirect:/settings";
        }

        model.addAttribute("user", user);

        // Si un dossier est spécifié
        if (caseId != null) {
            Case caseEntity = caseRepository.findById(caseId).orElse(null);
            model.addAttribute("case", caseEntity);
        }

        // Liste des juridictions courantes
        Map<String, String> jurisdictions = new HashMap<>();
        jurisdictions.put("TJ-PARIS", "Tribunal Judiciaire de Paris");
        jurisdictions.put("TJ-LYON", "Tribunal Judiciaire de Lyon");
        jurisdictions.put("TJ-MARSEILLE", "Tribunal Judiciaire de Marseille");
        jurisdictions.put("CA-PARIS", "Cour d'Appel de Paris");
        jurisdictions.put("CC-PARIS", "Cour de Cassation");

        model.addAttribute("jurisdictions", jurisdictions);

        return "rpva/send";
    }

    /**
     * Envoyer une communication au RPVA
     */
    @PostMapping("/send")
    public String sendCommunication(
            @RequestParam String jurisdictionCode,
            @RequestParam String caseReference,
            @RequestParam String subject,
            @RequestParam String content,
            @RequestParam(required = false) String[] attachments,
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            // Envoyer la communication
            Map<String, Object> result = rpvaService.sendCommunication(
                    jurisdictionCode,
                    caseReference,
                    subject,
                    content,
                    attachments != null ? attachments : new String[0]
            );

            String communicationId = (String) result.get("id");

            redirectAttributes.addFlashAttribute("message",
                "Communication envoyée avec succès à " + jurisdictionCode);

            return "redirect:/rpva/communications/" + communicationId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de l'envoi: " + e.getMessage());
            return "redirect:/rpva/send";
        }
    }

    /**
     * Voir le détail d'une communication
     */
    @GetMapping("/communications/{communicationId}")
    public String viewCommunication(
            @PathVariable String communicationId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            // Récupérer le statut de la communication
            Map<String, Object> status = rpvaService.getCommunicationStatus(communicationId);

            model.addAttribute("user", user);
            model.addAttribute("communication", status);
            model.addAttribute("communicationId", communicationId);

            return "rpva/view";

        } catch (Exception e) {
            model.addAttribute("error", "Erreur: " + e.getMessage());
            return "redirect:/rpva";
        }
    }

    /**
     * Télécharger l'accusé de réception
     */
    @GetMapping("/communications/{communicationId}/receipt")
    public String downloadReceipt(
            @PathVariable String communicationId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            byte[] receipt = rpvaService.downloadReceipt(communicationId);

            // TODO: Retourner le fichier PDF

            redirectAttributes.addFlashAttribute("message", "Accusé de réception téléchargé");
            return "redirect:/rpva/communications/" + communicationId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors du téléchargement: " + e.getMessage());
            return "redirect:/rpva/communications/" + communicationId;
        }
    }

    /**
     * Rechercher une juridiction
     */
    @GetMapping("/jurisdictions/search")
    public String searchJurisdiction(
            @RequestParam String postalCode,
            @RequestParam(defaultValue = "TJ") String type,
            Model model
    ) {
        try {
            Map<String, Object> result = rpvaService.findJurisdiction(postalCode, type);

            model.addAttribute("jurisdictions", result);
            model.addAttribute("postalCode", postalCode);
            model.addAttribute("type", type);

            return "rpva/search-results";

        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la recherche: " + e.getMessage());
            return "rpva/index";
        }
    }

    /**
     * Enregistrer un nouveau dossier au RPVA
     */
    @PostMapping("/cases/register")
    public String registerCase(
            @RequestParam String caseId,
            @RequestParam String jurisdictionCode,
            @RequestParam String caseType,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Case caseEntity = caseRepository.findById(caseId)
                    .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));

            Map<String, Object> parties = new HashMap<>();
            // TODO: Récupérer les parties depuis le dossier

            Map<String, Object> result = rpvaService.registerCase(
                    caseEntity.getName(),
                    jurisdictionCode,
                    caseType,
                    parties
            );

            redirectAttributes.addFlashAttribute("message",
                "Dossier enregistré au RPVA avec succès");

            return "redirect:/cases/" + caseId;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Erreur lors de l'enregistrement: " + e.getMessage());
            return "redirect:/rpva";
        }
    }
}
