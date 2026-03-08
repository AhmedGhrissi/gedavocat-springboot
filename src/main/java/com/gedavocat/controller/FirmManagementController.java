package com.gedavocat.controller;

import com.gedavocat.dto.*;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.FirmManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Contrôleur de gestion du cabinet
 * Permet à un admin de gérer les membres et d'affecter des dossiers
 */
@Controller
@RequestMapping("/firm")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
@Slf4j
public class FirmManagementController {

    private final FirmManagementService firmManagementService;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;

    @GetMapping("/members")
    public String membersPage(Authentication auth, Model model) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));

        if (currentUser.getFirm() == null) {
            model.addAttribute("error", "Vous devez avoir un cabinet pour gérer les membres");
            return "error";
        }

        String firmId = currentUser.getFirm().getId();
        List<FirmMemberResponse> members = firmManagementService.getAllMembers(firmId, currentUser.getId());

        model.addAttribute("members", members);
        model.addAttribute("firmName", currentUser.getFirm().getName());
        model.addAttribute("addRequest", new AddFirmMemberRequest());

        return "firm/members";
    }

    @PostMapping("/members/add")
    public String addMember(@Valid @ModelAttribute("addRequest") AddFirmMemberRequest request,
                           BindingResult result,
                           Authentication auth,
                           RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            redirectAttrs.addFlashAttribute("error", "Données invalides");
            return "redirect:/firm/members";
        }

        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));
        String firmId = currentUser.getFirm().getId();

        try {
            firmManagementService.addMember(firmId, request, currentUser.getId());
            redirectAttrs.addFlashAttribute("success", "Membre ajouté avec succès");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error adding member", e);
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/firm/members";
    }

    @PostMapping("/members/remove/{memberId}")
    public String removeMember(@PathVariable String memberId,
                              Authentication auth,
                              RedirectAttributes redirectAttrs) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));
        String firmId = currentUser.getFirm().getId();

        try {
            firmManagementService.removeMember(firmId, memberId, currentUser.getId());
            redirectAttrs.addFlashAttribute("success", "Membre retiré avec succès");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error removing member", e);
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/firm/members";
    }

    @GetMapping("/cases/assign")
    public String assignCasePage(Authentication auth, Model model) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));

        if (currentUser.getFirm() == null) {
            model.addAttribute("error", "Vous devez avoir un cabinet");
            return "error";
        }

        String firmId = currentUser.getFirm().getId();
        List<FirmMemberResponse> members = firmManagementService.getAllMembers(firmId, currentUser.getId());

        model.addAttribute("members", members);
        model.addAttribute("cases", caseRepository.findByLawyerId(currentUser.getId()));
        model.addAttribute("assignRequest", new AssignCaseRequest());

        return "firm/assign-case";
    }

    @PostMapping("/cases/assign")
    public String assignCase(@Valid @ModelAttribute("assignRequest") AssignCaseRequest request,
                            BindingResult result,
                            Authentication auth,
                            RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            redirectAttrs.addFlashAttribute("error", "Données invalides");
            return "redirect:/firm/cases/assign";
        }

        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));
        String firmId = currentUser.getFirm().getId();

        try {
            firmManagementService.assignCase(firmId, request, currentUser.getId());
            redirectAttrs.addFlashAttribute("success", "Dossier affecté avec succès");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error assigning case", e);
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/firm/cases/assign";
    }

    @PostMapping("/cases/unassign/{assignmentId}")
    public String unassignCase(@PathVariable String assignmentId,
                              Authentication auth,
                              RedirectAttributes redirectAttrs) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));
        String firmId = currentUser.getFirm().getId();

        try {
            firmManagementService.unassignCase(firmId, assignmentId, currentUser.getId());
            redirectAttrs.addFlashAttribute("success", "Affectation révoquée");
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error unassigning case", e);
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/firm/cases/assign";
    }
}



