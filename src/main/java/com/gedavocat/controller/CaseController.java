package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Case.CaseStatus;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.ClientService;
import com.gedavocat.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Contrôleur de gestion des dossiers
 * RÉSERVÉ AUX AVOCATS - Les clients utilisent ClientPortalController
 */
@Controller
@RequestMapping("/cases")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'LAWYER_SECONDARY')")
public class CaseController {

    private final CaseService caseService;
    private final ClientService clientService;
    private final DocumentService documentService;
    private final UserRepository userRepository;

    /**
     * Liste des dossiers
     */
    @GetMapping
    public String listCases(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CaseStatus status,
            Model model,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        List<Case> cases;
        if (search != null && !search.isEmpty()) {
            cases = caseService.searchCases(user.getId(), search);
        } else if (status != null) {
            cases = caseService.getCasesByStatus(user.getId(), status);
        } else {
            cases = caseService.getCasesByLawyer(user.getId());
        }

        model.addAttribute("cases", cases);
        model.addAttribute("search", search);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", CaseStatus.values());

        return "cases/list";
    }

    /**
     * Formulaire de création
     */
    @GetMapping("/new")
    public String newCaseForm(
            @RequestParam(required = false) String clientId,
            Model model, 
            Authentication authentication
    ) {
        System.out.println("=== DEBUG: newCaseForm appelé pour /cases/new ===");
        User user = getCurrentUser(authentication);
        List<Client> clients = isAdmin(authentication)
                ? clientService.getAllClients()
                : clientService.getClientsByLawyer(user.getId());

        Case newCase = new Case();
        
        // Si un clientId est fourni, pré-sélectionner le client
        if (clientId != null && !clientId.isEmpty()) {
            Client selectedClient = clientService.getClientById(clientId);
            // Vérifier que le client appartient bien à l'avocat
            if (selectedClient.getLawyer().getId().equals(user.getId())) {
                newCase.setClient(selectedClient);
            }
        }

        model.addAttribute("case", newCase);
        model.addAttribute("clients", clients);
        model.addAttribute("selectedClientId", clientId);
        model.addAttribute("isEdit", false);
        System.out.println("=== DEBUG: Retour vers cases/form avec " + clients.size() + " clients ===");
        return "cases/form";
    }

    /**
     * Créer un dossier
     */
    @PostMapping
    public String createCase(
            @Valid @ModelAttribute("case") Case caseEntity,
            @RequestParam(value = "clientId", required = false) String clientId,
            BindingResult result,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        System.out.println("=== DEBUG createCase: clientId reçu = " + clientId);
        System.out.println("=== DEBUG createCase: case.name = " + caseEntity.getName());
        System.out.println("=== DEBUG createCase: case.description = " + caseEntity.getDescription());
        
        // Validation manuelle du clientId
        if (clientId == null || clientId.isEmpty()) {
            result.rejectValue("client", "error.case", "Veuillez sélectionner un client");
        }
        
        if (result.hasErrors()) {
            System.out.println("=== DEBUG createCase: Erreurs de validation détectées");
            result.getAllErrors().forEach(error -> System.out.println("Erreur: " + error));
            User user = getCurrentUser(authentication);
            model.addAttribute("clients", clientService.getClientsByLawyer(user.getId()));
            model.addAttribute("selectedClientId", clientId);
            model.addAttribute("isEdit", false);
            return "cases/form";
        }

        try {
            User user = getCurrentUser(authentication);
            System.out.println("=== DEBUG createCase: User ID = " + user.getId());
            
            // Récupérer et associer le client
            Client client = clientService.getClientById(clientId);
            System.out.println("=== DEBUG createCase: Client trouvé = " + client.getName());
            caseEntity.setClient(client);
            
            Case savedCase = caseService.createCase(caseEntity, user.getId());
            System.out.println("=== DEBUG createCase: Dossier créé avec ID = " + savedCase.getId());
            
            redirectAttributes.addFlashAttribute("message", "Dossier créé avec succès");
            return "redirect:/cases/" + savedCase.getId();
        } catch (Exception e) {
            System.err.println("=== ERREUR createCase: " + e.getMessage());
            e.printStackTrace();
            User user = getCurrentUser(authentication);
            model.addAttribute("clients", clientService.getClientsByLawyer(user.getId()));
            model.addAttribute("selectedClientId", clientId);
            model.addAttribute("isEdit", false);
            model.addAttribute("error", e.getMessage());
            return "cases/form";
        }
    }

    /**
     * Détails d'un dossier
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String viewCase(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(id);

        // Vérifier l'accès (ADMIN peut voir tous les dossiers)
        if (!isAdmin(authentication) && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new RuntimeException("Accès non autorisé");
        }

        // Forcer le chargement du client (lazy loading)
        if (caseEntity.getClient() != null) {
            caseEntity.getClient().getName();
        }

        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documentService.getLatestVersions(id));
        model.addAttribute("documentCount", documentService.getDocumentsByCase(id).size());

        return "cases/view";
    }

    /**
     * Formulaire d'édition
     */
    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String editCaseForm(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(id);

        if (!isAdmin(authentication) && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new RuntimeException("Accès non autorisé");
        }

        // Utiliser le lawyerId du DOSSIER pour charger les bons clients
        String lawyerId = caseEntity.getLawyer().getId();
        List<Client> clients = isAdmin(authentication)
                ? clientService.getAllClients()
                : clientService.getClientsByLawyer(lawyerId);

        // Garantir que le client actuellement lié au dossier figure toujours dans la liste
        // (sécurité en cas de désynchronisation entre avocat et clients)
        if (caseEntity.getClient() != null) {
            final String currentClientId = caseEntity.getClient().getId();
            boolean alreadyPresent = clients.stream().anyMatch(c -> c.getId().equals(currentClientId));
            if (!alreadyPresent) {
                clients = new java.util.ArrayList<>(clients);
                clients.add(0, caseEntity.getClient());
            }
        }

        model.addAttribute("case", caseEntity);
        model.addAttribute("clients", clients);
        model.addAttribute("selectedClientId", caseEntity.getClient() != null ? caseEntity.getClient().getId() : "");
        model.addAttribute("isEdit", true);
        return "cases/form";
    }

    /**
     * Mettre à jour un dossier
     */
    @PostMapping("/{id}")
    public String updateCase(
            @PathVariable String id,
            @Valid @ModelAttribute("case") Case caseEntity,
            @RequestParam(value = "clientId", required = false) String clientId,
            BindingResult result,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        System.out.println("=== DEBUG updateCase: ID = " + id + ", clientId = " + clientId);
        
        // Validation manuelle du clientId
        if (clientId == null || clientId.isEmpty()) {
            result.rejectValue("client", "error.case", "Veuillez sélectionner un client");
        }
        
        if (result.hasErrors()) {
            System.out.println("=== DEBUG updateCase: Erreurs de validation détectées");
            User user = getCurrentUser(authentication);
            // ADMIN: charge tous les clients; sinon: ceux de l'avocat courant
            List<Client> clients = isAdmin(authentication)
                    ? clientService.getAllClients()
                    : clientService.getClientsByLawyer(user.getId());
            model.addAttribute("clients", clients);
            model.addAttribute("selectedClientId", clientId);
            model.addAttribute("isEdit", true);
            return "cases/form";
        }

        try {
            User user = getCurrentUser(authentication);
            // Récupérer et associer le client
            Client client = clientService.getClientById(clientId);
            System.out.println("=== DEBUG updateCase: Client trouvé = " + client.getName());
            caseEntity.setClient(client);

            // Pour ADMIN, passer l'ID du LAWYER propriétaire du dossier afin de
            // contourner les vérifications d'appartenance dans le service.
            String lawyerIdForService = user.getId();
            if (isAdmin(authentication)) {
                Case existing = caseService.getCaseById(id);
                lawyerIdForService = existing.getLawyer().getId();
            }
            caseService.updateCase(id, caseEntity, lawyerIdForService);
            System.out.println("=== DEBUG updateCase: Dossier mis à jour avec succès");
            
            redirectAttributes.addFlashAttribute("message", "Dossier modifié avec succès");
            return "redirect:/cases/" + id;
        } catch (Exception e) {
            System.err.println("=== ERREUR updateCase: " + e.getMessage());
            e.printStackTrace();
            User user = getCurrentUser(authentication);
            List<Client> clients = isAdmin(authentication)
                    ? clientService.getAllClients()
                    : clientService.getClientsByLawyer(user.getId());
            model.addAttribute("clients", clients);
            model.addAttribute("selectedClientId", clientId);
            model.addAttribute("isEdit", true);
            model.addAttribute("error", e.getMessage());
            return "cases/form";
        }
    }

    /**
     * Fermer un dossier
     */
    @PostMapping("/{id}/close")
    public String closeCase(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            caseService.closeCase(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Dossier fermé avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    /**
     * Archiver un dossier
     */
    @PostMapping("/{id}/archive")
    public String archiveCase(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            caseService.archiveCase(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Dossier archivé avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    /**
     * Supprimer un dossier
     */
    @PostMapping("/{id}/delete")
    public String deleteCase(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            caseService.deleteCase(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Dossier supprimé avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases";
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
