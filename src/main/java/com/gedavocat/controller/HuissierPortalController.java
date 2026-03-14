package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.Permission;
import com.gedavocat.model.User;
import com.gedavocat.repository.PermissionRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.DocumentService;
import com.gedavocat.service.DocumentShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Portail pour les huissiers.
 * Similaire au portail collaborateur mais avec le rôle HUISSIER
 * et filtrage des documents par DocumentShare.
 */
@Controller
@RequestMapping("/my-cases-huissier")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HUISSIER')")
public class HuissierPortalController {

    private final CaseService caseService;
    private final DocumentService documentService;
    private final DocumentShareService documentShareService;
    private final UserRepository userRepository;
    private final AppointmentService appointmentService;
    private final PermissionRepository permissionRepository;

    @GetMapping
    public String listMyCases(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<Case> myCases = caseService.getCasesByCollaborator(user.getId());

        for (Case c : myCases) {
            if (c.getLawyer() != null) c.getLawyer().getName();
            if (c.getClient() != null) c.getClient().getName();
        }

        model.addAttribute("cases", myCases);
        model.addAttribute("user", user);

        java.util.Map<String, java.time.LocalDateTime> expirations = new java.util.HashMap<>();
        try {
            List<Permission> perms = permissionRepository.findActiveByLawyerId(user.getId());
            for (Permission p : perms) {
                if (p.getCaseEntity() != null && p.getExpiresAt() != null) {
                    expirations.put(p.getCaseEntity().getId(), p.getExpiresAt());
                }
            }
        } catch (Exception ignore) {}
        model.addAttribute("expirations", expirations);

        return "huissier-portal/cases";
    }

    @GetMapping("/{caseId}")
    public String viewMyCase(@PathVariable String caseId, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);

        if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "huissier-portal/pending";
        }

        // Get all documents then filter only those shared with HUISSIER
        List<Document> allDocuments = documentService.getLatestVersions(caseId);
        List<Document> documents = documentShareService.filterSharedDocuments(caseId, allDocuments, User.UserRole.HUISSIER);

        List<Appointment> appointments;
        try {
            appointments = appointmentService.getAppointmentsByCase(caseId);
            for (Appointment a : appointments) {
                if (a.getClient() != null) a.getClient().getName();
                if (a.getRelatedCase() != null) a.getRelatedCase().getName();
            }
        } catch (Exception e) {
            appointments = Collections.emptyList();
        }

        if (caseEntity.getLawyer() != null) {
            caseEntity.getLawyer().getName();
            caseEntity.getLawyer().getEmail();
        }

        model.addAttribute("case", caseEntity);
        model.addAttribute("documents", documents);
        model.addAttribute("appointments", appointments);
        model.addAttribute("user", user);

        return "huissier-portal/case-detail";
    }

    // =====================
    // Calendrier huissier
    // =====================

    @GetMapping("/calendar")
    @Transactional(readOnly = true)
    public String calendar(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        List<Case> myCases = caseService.getCasesByCollaborator(user.getId());

        List<Appointment> allAppointments = new ArrayList<>();
        for (Case c : myCases) {
            try {
                List<Appointment> caseAppointments = appointmentService.getAppointmentsByCase(c.getId());
                allAppointments.addAll(caseAppointments);
            } catch (Exception ignore) {}
        }
        for (Appointment a : allAppointments) {
            if (a.getClient() != null) a.getClient().getName();
            if (a.getRelatedCase() != null) a.getRelatedCase().getName();
            if (a.getLawyer() != null) a.getLawyer().getFirstName();
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        List<Appointment> upcoming = allAppointments.stream()
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isAfter(java.time.LocalDateTime.now()))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate))
                .limit(10)
                .toList();
        List<Appointment> todayAppointments = allAppointments.stream()
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().toLocalDate().equals(today))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate))
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("appointments", allAppointments);
        model.addAttribute("upcomingAppointments", upcoming);
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("currentMonth", java.time.YearMonth.now());
        model.addAttribute("currentDate", today);
        return "huissier-portal/calendar";
    }

    @GetMapping("/calendar/api/events")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> calendarEvents(Authentication authentication,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate start,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate end) {
        User user = getCurrentUser(authentication);
        List<Case> myCases = caseService.getCasesByCollaborator(user.getId());

        List<java.util.Map<String, Object>> events = new ArrayList<>();
        for (Case c : myCases) {
            try {
                List<Appointment> caseAppointments = appointmentService.getAppointmentsByCase(c.getId());
                for (Appointment a : caseAppointments) {
                    if (a.getAppointmentDate() == null) continue;
                    java.time.LocalDate aptDate = a.getAppointmentDate().toLocalDate();
                    if (!aptDate.isBefore(start) && aptDate.isBefore(end)) {
                        java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
                        event.put("id", a.getId());
                        event.put("title", a.getTitle());
                        event.put("start", a.getAppointmentDate().toString());
                        if (a.getEndDate() != null) event.put("end", a.getEndDate().toString());
                        event.put("color", a.getColor());
                        event.put("type", a.getType().getDisplayName());
                        event.put("status", a.getStatus().name());
                        events.add(event);
                    }
                }
            } catch (Exception ignore) {}
        }
        return events;
    }

    // =====================
    // Profil huissier
    // =====================
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        model.addAttribute("user", user);
        return "huissier-portal/profile";
    }

    @PostMapping("/profile")
    @Transactional
    public String updateProfile(@RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "officeNumber", required = false) String officeNumber,
                                Authentication authentication,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        user.setPhone(phone);
        user.setBarNumber(officeNumber);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("message", "Vos informations ont été mises à jour.");
        return "redirect:/my-cases-huissier/profile";
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
