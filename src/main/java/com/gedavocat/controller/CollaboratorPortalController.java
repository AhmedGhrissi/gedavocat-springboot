package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.PermissionRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.DocumentService;
import com.gedavocat.service.DocumentShareService;
import com.gedavocat.service.WatermarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import com.gedavocat.util.ByteArrayMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contrôleur pour le portail Collaborateur
 * Similaire au portail client mais pour les collaborateurs (rôle COLLABORATOR)
 */
@Controller
@RequestMapping("/my-cases-collab")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LAWYER_SECONDARY')")
public class CollaboratorPortalController {

    private final CaseService caseService;
    private final DocumentService documentService;
    private final DocumentShareService documentShareService;
    private final UserRepository userRepository;
    private final WatermarkService watermarkService;
    private final AppointmentService appointmentService;
    private final PermissionRepository permissionRepository;



    @GetMapping
    public String listMyCases(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);

        // Récupérer uniquement les dossiers où l'utilisateur est assigné comme collaborator
        List<Case> myCases = caseService.getCasesByCollaborator(user.getId());

        // Force-init proxies
        for (Case c : myCases) {
            if (c.getLawyer() != null) c.getLawyer().getName();
            if (c.getClient() != null) c.getClient().getName();
        }

        model.addAttribute("cases", myCases);
        model.addAttribute("user", user);

        // Build a map caseId -> expiresAt for display in the portal
        java.util.Map<String, java.time.LocalDateTime> expirations = new java.util.HashMap<>();
        try {
            List<com.gedavocat.model.Permission> perms = permissionRepository.findActiveByLawyerId(user.getId());
            for (com.gedavocat.model.Permission p : perms) {
                if (p.getCaseEntity() != null && p.getExpiresAt() != null) {
                    expirations.put(p.getCaseEntity().getId(), p.getExpiresAt());
                }
            }
        } catch (Exception ignore) {}
        model.addAttribute("expirations", expirations);

        return "collaborator-portal/cases";
    }

    @GetMapping("/{caseId}")
    public String viewMyCase(@PathVariable String caseId, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);

        // Vérifier que ce collaborateur a une permission active sur le dossier
        if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "collaborator-portal/pending";
        }

        // Filter: only show documents shared with LAWYER_SECONDARY
        List<Document> allDocs = documentService.getLatestVersions(caseId);
        List<Document> documents = documentShareService.filterSharedDocuments(caseId, allDocs, User.UserRole.LAWYER_SECONDARY);

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

        return "collaborator-portal/case-detail";
    }

    @GetMapping("/{caseId}/documents")
    public String listMyDocuments(@PathVariable String caseId, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Case caseEntity = caseService.getCaseById(caseId);
        if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
            model.addAttribute("errorMessage", "Vous n'avez pas accès à ce dossier.");
            return "collaborator-portal/pending";
        }
        List<Document> documents = documentShareService.filterSharedDocuments(caseId,
                documentService.getLatestVersions(caseId), User.UserRole.LAWYER_SECONDARY);
        model.addAttribute("documents", documents);
        model.addAttribute("case", caseEntity);
        model.addAttribute("user", user);
        return "collaborator-portal/documents";
    }

    @PostMapping("/{caseId}/upload")
    public String uploadDocument(@PathVariable String caseId,
                                 @RequestParam("file") MultipartFile file,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            if (!caseService.hasCollaboratorAccess(caseId, user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé à ce dossier.");
                return "redirect:/my-cases-collab";
            }
            MultipartFile fileToStore = applyWatermarkIfPdf(file, WatermarkService.WATERMARK_COPIE);
            documentService.uploadDocument(caseId, fileToStore, user.getId(), "COLLABORATOR");
            redirectAttributes.addFlashAttribute("message", "Document uploadé avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload : " + e.getMessage());
        }
        return "redirect:/my-cases-collab/" + caseId;
    }

    /**
     * Téléchargement interdit pour les collaborateurs.
     */
    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String documentId, Authentication authentication) {
        return ResponseEntity.status(403).build();
    }

    /**
     * Prévisualisation interdite pour les collaborateurs.
     */
    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<Resource> previewDocument(@PathVariable String documentId, Authentication authentication) {
        return ResponseEntity.status(403).build();
    }

    private MultipartFile applyWatermarkIfPdf(MultipartFile file, String watermarkText) {
        try {
            byte[] bytes = file.getBytes();
            if (watermarkService.isPdf(bytes)) {
                byte[] watermarked = watermarkService.addWatermark(
                        new ByteArrayInputStream(bytes), watermarkText);
                if (watermarked != null) {
                    return new ByteArrayMultipartFile(
                            file.getName(), file.getOriginalFilename(),
                            file.getContentType(), watermarked);
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, on stocke le fichier original
        }
        return file;
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    /**
     * Export ZIP interdit pour les collaborateurs.
     */
    @GetMapping("/{caseId}/export-zip")
    public ResponseEntity<Resource> exportCaseDocumentsZip(@PathVariable String caseId, Authentication authentication) {
        return ResponseEntity.status(403).build();
    }

    // =====================
    // Calendrier collaborateur
    // =====================

    /**
     * Vue calendrier du collaborateur : tous les rendez-vous de ses dossiers partagés.
     */
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
        // Force-init proxies
        for (Appointment a : allAppointments) {
            if (a.getClient() != null) a.getClient().getName();
            if (a.getRelatedCase() != null) a.getRelatedCase().getName();
            if (a.getLawyer() != null) a.getLawyer().getFirstName();
        }

        // Upcoming
        List<Appointment> upcoming = allAppointments.stream()
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().isAfter(java.time.LocalDateTime.now()))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate))
                .limit(10)
                .toList();

        // Today
        LocalDate today = LocalDate.now();
        List<Appointment> todayAppointments = allAppointments.stream()
                .filter(a -> a.getAppointmentDate() != null && a.getAppointmentDate().toLocalDate().equals(today))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate))
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("appointments", allAppointments);
        model.addAttribute("upcomingAppointments", upcoming);
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("currentMonth", YearMonth.now());
        model.addAttribute("currentDate", today);
        return "collaborator-portal/calendar";
    }

    /**
     * API JSON pour FullCalendar du collaborateur.
     */
    @GetMapping("/calendar/api/events")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> calendarEvents(Authentication authentication,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate end) {
        User user = getCurrentUser(authentication);
        List<Case> myCases = caseService.getCasesByCollaborator(user.getId());

        List<java.util.Map<String, Object>> events = new ArrayList<>();
        for (Case c : myCases) {
            try {
                List<Appointment> caseAppointments = appointmentService.getAppointmentsByCase(c.getId());
                for (Appointment a : caseAppointments) {
                    if (a.getAppointmentDate() == null) continue;
                    LocalDate aptDate = a.getAppointmentDate().toLocalDate();
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
    // Profil collaborateur
    // =====================
    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        model.addAttribute("user", user);
        return "collaborator-portal/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "barNumber", required = false) String barNumber,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        user.setPhone(phone);
        user.setBarNumber(barNumber);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("message", "Vos informations ont été mises à jour.");
        return "redirect:/my-cases-collab/profile";
    }
}
