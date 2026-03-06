package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Case.CaseStatus;
import com.gedavocat.model.Client;
import com.gedavocat.model.DocumentShare;
import com.gedavocat.model.Permission;
import com.gedavocat.model.User;
import com.gedavocat.repository.AppointmentRepository;
import com.gedavocat.repository.DocumentShareRepository;
import com.gedavocat.repository.PermissionRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.ClientService;
import com.gedavocat.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for managing cases (dossiers). Simplified and cleaned for encoding issues.
 */
@Controller
@RequestMapping("/cases")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'LAWYER_SECONDARY')")
@Slf4j
public class CaseController {

    /**
     * SEC-MASS-ASSIGN FIX : restreindre les champs bindables sur Case.
     * Empêche la manipulation de lawyer, client, createdAt, etc.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("title", "description", "reference", "status",
                "type", "jurisdiction", "opposingParty", "opposingLawyer",
                "notes", "priority", "dueDate");
    }

    private final CaseService caseService;
    private final ClientService clientService;
    private final DocumentService documentService;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final DocumentShareRepository documentShareRepository;

    @GetMapping
    public String listCases(@RequestParam(required = false) String search,
                            @RequestParam(required = false) CaseStatus status,
                            Model model,
                            Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<Case> cases = (search != null && !search.isEmpty())
                ? caseService.searchCases(user.getId(), search)
                : (status != null ? caseService.getCasesByStatus(user.getId(), status) : caseService.getCasesByLawyer(user.getId()));

        // initialize lazy collections
        for (Case c : cases) {
            if (c.getClient() != null) c.getClient().getName();
            if (c.getDocuments() != null) c.getDocuments().size();
        }

        model.addAttribute("cases", cases);
        model.addAttribute("statuses", CaseStatus.values());
        return "cases/list";
    }

    @GetMapping("/new")
    @Transactional(readOnly = true)
    public String newCaseForm(@RequestParam(required = false) String clientId, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<Client> clients = isAdmin(authentication) ? clientService.getAllClients() : clientService.getClientsByLawyer(user.getId());
        Case newCase = new Case();
        model.addAttribute("case", newCase);
        model.addAttribute("clients", clients);
        model.addAttribute("selectedClientId", clientId != null ? clientId : "");
        model.addAttribute("isEdit", false);
        model.addAttribute("caseStatuses", CaseStatus.values());
        return "cases/form";
    }

    @PostMapping
    public String createCase(@Valid @ModelAttribute("case") Case caseEntity,
                             BindingResult result,
                             @RequestParam(value = "clientId", required = false) String clientId,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (clientId == null || clientId.isEmpty()) {
            result.rejectValue("client", "error.case", "Veuillez selectionner un client");
        }
        if (result.hasErrors()) {
            User user = getCurrentUser(authentication);
            model.addAttribute("clients", clientService.getClientsByLawyer(user.getId()));
            model.addAttribute("selectedClientId", clientId);
            model.addAttribute("isEdit", false);
            model.addAttribute("caseStatuses", CaseStatus.values());
            return "cases/form";
        }

        User user = getCurrentUser(authentication);
        Case savedCase = caseService.createCase(caseEntity, user.getId(), clientId);
        redirectAttributes.addFlashAttribute("message", "Dossier cree avec succes");
        return "redirect:/cases/" + savedCase.getId();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String viewCase(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(id);
        if (!isAdmin(authentication) && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new AccessDeniedException("Accès non autorisé à ce dossier");
        }
        if (caseEntity.getClient() != null) caseEntity.getClient().getName();
        if (caseEntity.getDocuments() != null) caseEntity.getDocuments().size();

        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documentService.getLatestVersions(id));
        model.addAttribute("documentCount", documentService.getDocumentsByCase(id).size());

        // BIZ-09 FIX : ajouter les attributs manquants attendus par view.html
        try {
            var appointments = appointmentRepository.findByRelatedCaseIdOrderByAppointmentDateDesc(id);
            model.addAttribute("appointments", appointments != null ? appointments : java.util.Collections.emptyList());
        } catch (Exception e) {
            model.addAttribute("appointments", java.util.Collections.emptyList());
        }

        try {
            List<Permission> permissions = permissionRepository.findByCaseEntityId(id);
            model.addAttribute("permissions", permissions != null ? permissions : java.util.Collections.emptyList());
        } catch (Exception e) {
            model.addAttribute("permissions", java.util.Collections.emptyList());
        }

        // Build shareMap: documentId -> Set<role>
        try {
            List<DocumentShare> shares = documentShareRepository.findByCaseId(id);
            Map<String, Set<String>> shareMap = new HashMap<>();
            if (shares != null) {
                for (DocumentShare share : shares) {
                    String docId = share.getDocument().getId();
                    shareMap.computeIfAbsent(docId, k -> new java.util.HashSet<>())
                            .add(share.getTargetRole().name());
                }
            }
            model.addAttribute("shareMap", shareMap);
        } catch (Exception e) {
            model.addAttribute("shareMap", new HashMap<>());
        }

        return "cases/view";
    }

    @GetMapping("/{id}/edit")
    @Transactional(readOnly = true)
    public String editCaseForm(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(id);
        if (!isAdmin(authentication) && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new AccessDeniedException("Accès non autorisé à ce dossier");
        }
        String lawyerId = caseEntity.getLawyer().getId();
        List<Client> clients = isAdmin(authentication) ? clientService.getAllClients() : clientService.getClientsByLawyer(lawyerId);
        model.addAttribute("case", caseEntity);
        model.addAttribute("clients", clients);
        model.addAttribute("selectedClientId", caseEntity.getClient() != null ? caseEntity.getClient().getId() : "");
        model.addAttribute("isEdit", true);
        model.addAttribute("caseStatuses", CaseStatus.values());
        return "cases/form";
    }

    @PostMapping("/{id}")
    public String updateCase(@PathVariable String id,
                             @Valid @ModelAttribute("case") Case caseEntity,
                             BindingResult result,
                             @RequestParam(value = "clientId", required = false) String clientId,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        if (clientId == null || clientId.isEmpty()) {
            result.rejectValue("client", "error.case", "Veuillez selectionner un client");
        }
        if (result.hasErrors()) {
            User user = getCurrentUser(authentication);
            List<Client> clients = isAdmin(authentication) ? clientService.getAllClients() : clientService.getClientsByLawyer(user.getId());
            model.addAttribute("clients", clients);
            model.addAttribute("selectedClientId", clientId);
            model.addAttribute("isEdit", true);
            model.addAttribute("caseStatuses", CaseStatus.values());
            return "cases/form";
        }
        User user = getCurrentUser(authentication);
        Client client = clientService.getClientById(clientId);
        caseEntity.setClient(client);
        String lawyerIdForService = user.getId();
        if (isAdmin(authentication)) {
            Case existing = caseService.getCaseById(id);
            lawyerIdForService = existing.getLawyer().getId();
        }
        caseService.updateCase(id, caseEntity, lawyerIdForService);
        redirectAttributes.addFlashAttribute("message", "Dossier modifie avec succes");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/close")
    public String closeCase(@PathVariable String id, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        caseService.closeCase(id, user.getId());
        redirectAttributes.addFlashAttribute("message", "Dossier ferme avec succes");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archiveCase(@PathVariable String id, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        caseService.archiveCase(id, user.getId());
        redirectAttributes.addFlashAttribute("message", "Dossier archive avec succes");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteCase(@PathVariable String id, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        caseService.deleteCase(id, user.getId());
        redirectAttributes.addFlashAttribute("message", "Dossier supprime avec succes");
        return "redirect:/cases";
    }

    @PostMapping("/{caseId}/permissions/{permissionId}/revoke")
    @Transactional
    public String revokePermission(@PathVariable String caseId, @PathVariable String permissionId, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);
        if (!isAdmin(authentication) && !caseEntity.getLawyer().getId().equals(user.getId())) {
            throw new AccessDeniedException("Accès non autorisé à ce dossier");
        }
        var permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AccessDeniedException("Permission introuvable"));
        if (!permission.getCaseEntity().getId().equals(caseId)) {
            throw new AccessDeniedException("Permission incohérente avec le dossier");
        }
        permission.revoke();
        permissionRepository.save(permission);
        redirectAttributes.addFlashAttribute("message", "Acces collaborateur revoque avec succes");
        return "redirect:/cases/" + caseId;
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Utilisateur non trouvé"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}


