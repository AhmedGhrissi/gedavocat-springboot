package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.RpvaCommunication;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.RpvaCommunicationRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.RPVAService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur pour les communications RPVA (e-Barreau)
 */
@Slf4j
@Controller
@RequestMapping("/rpva")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
@SuppressWarnings("null")
public class RPVAController {

    private final RPVAService rpvaService;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final RpvaCommunicationRepository rpvaCommunicationRepository;

    /**
     * Page principale RPVA
     */
    @GetMapping
    public String index(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        model.addAttribute("user", user);
        model.addAttribute("isConfigured", rpvaService.isConfigured());

        // Récupérer les communications envoyées et reçues depuis la base
        List<RpvaCommunication> sentCommunications = rpvaCommunicationRepository
            .findBySentById(user.getId());
        // Filtrer les notifications (reçues) vs les autres (envoyées)
        List<RpvaCommunication> receivedCommunications = sentCommunications.stream()
            .filter(c -> c.getType() == RpvaCommunication.CommunicationType.NOTIFICATION)
            .toList();
        sentCommunications = sentCommunications.stream()
            .filter(c -> c.getType() != RpvaCommunication.CommunicationType.NOTIFICATION)
            .toList();

        model.addAttribute("sentCommunications", sentCommunications);
        model.addAttribute("receivedCommunications", receivedCommunications);

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

            model.addAttribute("user", user);
            model.addAttribute("isConfigured", rpvaService.isConfigured());

            // Récupérer les communications des 30 derniers jours
            if (rpvaService.isConfigured()) {
                Map<String, Object> communications = rpvaService.getReceivedCommunications(
                        LocalDateTime.now().minusDays(30),
                        LocalDateTime.now(),
                        status != null ? status : "all"
                );
                model.addAttribute("communications", communications);
            }

            model.addAttribute("selectedStatus", status);

            return "rpva/received";

        } catch (Exception e) {
            log.error("Erreur récupération communications RPVA", e);
            model.addAttribute("error", "Erreur lors de la récupération des communications");
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

        model.addAttribute("user", user);
        model.addAttribute("isConfigured", rpvaService.isConfigured());

        // Si un dossier est spécifié
        if (caseId != null) {
            Case caseEntity = caseRepository.findByIdWithClientAndLawyer(caseId).orElse(null);
            // Ownership check: verify case belongs to authenticated lawyer
            if (caseEntity != null && caseEntity.getLawyer() != null
                    && !caseEntity.getLawyer().getId().equals(user.getId())) {
                caseEntity = null; // do not expose foreign case
            }
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

        // Liste des dossiers de l'utilisateur pour le formulaire d'envoi
        List<Case> userCases = caseRepository.findByLawyerId(user.getId());
        model.addAttribute("userCases", userCases);

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
            // Vérifier que l'utilisateur existe
            userRepository.findByEmail(userDetails.getUsername())
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
                "Erreur lors de l'envoi de la communication");
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

            // Ownership check: verify communication belongs to user
            RpvaCommunication comm = rpvaCommunicationRepository.findById(communicationId).orElse(null);
            if (comm != null && !comm.getSentBy().getId().equals(user.getId())) {
                model.addAttribute("error", "Accès non autorisé");
                return "redirect:/rpva";
            }

            // Récupérer le statut de la communication
            Map<String, Object> status = rpvaService.getCommunicationStatus(communicationId);

            model.addAttribute("user", user);
            model.addAttribute("communication", status);
            model.addAttribute("communicationId", communicationId);

            return "rpva/view";

        } catch (Exception e) {
            log.error("Erreur affichage communication {}", communicationId, e);
            model.addAttribute("error", "Erreur lors de la récupération de la communication");
            return "redirect:/rpva";
        }
    }

    /**
     * Télécharger l'accusé de réception
     */
    @GetMapping("/communications/{communicationId}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable String communicationId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        try {
            // Ownership check: verify communication belongs to authenticated user
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouv\u00e9"));
            RpvaCommunication comm = rpvaCommunicationRepository.findById(communicationId).orElse(null);
            if (comm != null && !comm.getSentBy().getId().equals(user.getId())) {
                return ResponseEntity.status(403).build();
            }

            byte[] receipt = rpvaService.downloadReceipt(communicationId);
            if (receipt == null || receipt.length == 0) {
                return ResponseEntity.notFound().build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "accuse-reception-" + communicationId + ".pdf");
            headers.setContentLength(receipt.length);
            return ResponseEntity.ok().headers(headers).body(receipt);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
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
            log.error("Erreur recherche juridiction", e);
            model.addAttribute("error", "Erreur lors de la recherche de juridiction");
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
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouv\u00e9"));

            Case caseEntity = caseRepository.findByIdWithClientAndLawyer(caseId)
                    .orElseThrow(() -> new RuntimeException("Dossier non trouv\u00e9"));

            // Ownership check: verify this case belongs to the authenticated lawyer
            if (caseEntity.getLawyer() == null || !caseEntity.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Acc\u00e8s non autoris\u00e9 \u00e0 ce dossier");
                return "redirect:/rpva";
            }

            // Récupérer les parties depuis le dossier
            Map<String, Object> parties = new HashMap<>();
            parties.put("plaintiff", Map.of(
                "name", caseEntity.getClient() != null ? caseEntity.getClient().getName() : "Non défini",
                "type", "PERSON"
            ));
            parties.put("defendant", Map.of(
                "name", "À définir",
                "type", "PERSON"
            ));

            rpvaService.registerCase(
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
                "Erreur lors de l'enregistrement du dossier RPVA");
            return "redirect:/rpva";
        }
    }
}
