package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
import com.gedavocat.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Contrôleur pour la gestion du calendrier et des rendez-vous
 */
@Slf4j
@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final CaseRepository caseRepository;
    private final EmailService emailService;

    /**
     * Page principale du calendrier
     */
    @GetMapping
    @Transactional(readOnly = true)
    public String calendar(Authentication authentication, Model model,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month) {
        User user = getCurrentUser(authentication);
        
        // Date actuelle ou date spécifiée
        YearMonth currentMonth = (year != null && month != null) 
            ? YearMonth.of(year, month) 
            : YearMonth.now();
        
        // Récupérer les rendez-vous du mois
        LocalDate startDate = currentMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();
        List<Appointment> appointments = appointmentService.getAppointmentsByLawyerAndDateRange(
            user.getId(), startDate, endDate);
        
        // Statistiques
        var stats = appointmentService.getStatistics(user.getId());
        List<Appointment> upcomingAppointments = appointmentService.getUpcomingAppointments(user.getId());
        List<Appointment> todayAppointments = appointmentService.getTodayAppointments(user.getId());
        
        // Force-initialiser les proxies lazy (open-in-view=false)
        initializeAppointmentProxies(appointments);
        initializeAppointmentProxies(upcomingAppointments);
        initializeAppointmentProxies(todayAppointments);
        
        model.addAttribute("user", user);
        model.addAttribute("appointments", appointments);
        model.addAttribute("upcomingAppointments", upcomingAppointments);
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("stats", stats);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("currentDate", LocalDate.now());
        model.addAttribute("clients", clientRepository.findByLawyerId(user.getId()));
        model.addAttribute("cases", caseRepository.findByLawyerId(user.getId()));
        
        return "appointments/calendar";
    }

    /**
     * Liste des rendez-vous
     */
    @GetMapping("/list")
    @Transactional(readOnly = true)
    public String list(Authentication authentication, Model model,
                      @RequestParam(required = false) String filter) {
        User user = getCurrentUser(authentication);
        
        List<Appointment> appointments;
        if ("upcoming".equals(filter)) {
            appointments = appointmentService.getUpcomingAppointments(user.getId());
        } else if ("today".equals(filter)) {
            appointments = appointmentService.getTodayAppointments(user.getId());
        } else if ("court".equals(filter)) {
            appointments = appointmentService.getUpcomingCourtHearings(user.getId());
        } else {
            appointments = appointmentService.getAppointmentsByLawyer(user.getId());
        }
        
        // Force-initialiser les proxies lazy (open-in-view=false)
        initializeAppointmentProxies(appointments);
        
        model.addAttribute("user", user);
        model.addAttribute("appointments", appointments);
        model.addAttribute("filter", filter);
        
        return "appointments/list";
    }

    /**
     * Formulaire de création d'un rendez-vous
     */
    @GetMapping("/new")
    public String newAppointment(Authentication authentication, Model model,
                                @RequestParam(required = false) String clientId,
                                @RequestParam(required = false) String caseId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date) {
        User user = getCurrentUser(authentication);
        
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(date != null ? date : LocalDateTime.now().plusHours(1));
        appointment.setType(Appointment.AppointmentType.CLIENT_MEETING);
        appointment.setStatus(Appointment.AppointmentStatus.SCHEDULED);
        
        model.addAttribute("user", user);
        model.addAttribute("appointment", appointment);
        model.addAttribute("clients", clientRepository.findByLawyerId(user.getId()));
        model.addAttribute("cases", caseRepository.findByLawyerId(user.getId()));
        model.addAttribute("appointmentTypes", Appointment.AppointmentType.values());
        model.addAttribute("appointmentStatuses", Appointment.AppointmentStatus.values());
        
        return "appointments/form";
    }

    /**
     * Création d'un rendez-vous
     */
    @PostMapping("/create")
    public String create(Authentication authentication,
                        @ModelAttribute Appointment appointment,
                        @RequestParam(required = false) String clientId,
                        @RequestParam(required = false) String caseId,
                        RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            
            // Associer le client si spécifié
            if (clientId != null && !clientId.isEmpty()) {
                clientRepository.findById(clientId).ifPresent(c -> {
                    // Ownership check: verify client belongs to this lawyer
                    if (c.getLawyer() != null && c.getLawyer().getId().equals(user.getId())) {
                        appointment.setClient(c);
                    } else {
                        throw new RuntimeException("Client non autorisé");
                    }
                });
            }
            
            // Associer le dossier si spécifié
            if (caseId != null && !caseId.isEmpty()) {
                caseRepository.findById(caseId).ifPresent(cs -> {
                    // Ownership check: verify case belongs to this lawyer
                    if (cs.getLawyer() != null && cs.getLawyer().getId().equals(user.getId())) {
                        appointment.setRelatedCase(cs);
                    } else {
                        throw new RuntimeException("Dossier non autorisé");
                    }
                });
            }
            
            Appointment created = appointmentService.createAppointment(appointment, user.getId());
            
            redirectAttributes.addFlashAttribute("success", 
                "Rendez-vous créé avec succès: " + created.getTitle());
            return "redirect:/appointments";
            
        } catch (Exception e) {
            log.error("Erreur lors de la création du rendez-vous", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/appointments/new";
        }
    }

    /**
     * Formulaire d'édition d'un rendez-vous
     */
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Authentication authentication, Model model,
                      RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        
        return appointmentService.getAppointmentById(id)
            .map(appointment -> {
                // Vérifier que le rendez-vous appartient à l'avocat
                if (!appointment.getLawyer().getId().equals(user.getId())) {
                    redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                    return "redirect:/appointments";
                }
                
                model.addAttribute("user", user);
                model.addAttribute("appointment", appointment);
                model.addAttribute("clients", clientRepository.findByLawyerId(user.getId()));
                model.addAttribute("cases", caseRepository.findByLawyerId(user.getId()));
                model.addAttribute("appointmentTypes", Appointment.AppointmentType.values());
                model.addAttribute("appointmentStatuses", Appointment.AppointmentStatus.values());
                
                return "appointments/form";
            })
            .orElseGet(() -> {
                redirectAttributes.addFlashAttribute("error", "Rendez-vous non trouvé");
                return "redirect:/appointments";
            });
    }

    /**
     * Mise à jour d'un rendez-vous
     */
    @PostMapping("/{id}/update")
    public String update(@PathVariable String id,
                        @ModelAttribute Appointment appointment,
                        @RequestParam(required = false) String clientId,
                        @RequestParam(required = false) String caseId,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            
            // Vérifier l'appartenance
            Appointment existing = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
            
            if (!existing.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }
            
            // Associer le client si spécifié
            if (clientId != null && !clientId.isEmpty()) {
                clientRepository.findById(clientId).ifPresent(c -> {
                    if (c.getLawyer() != null && c.getLawyer().getId().equals(user.getId())) {
                        appointment.setClient(c);
                    } else {
                        throw new RuntimeException("Client non autorisé");
                    }
                });
            }
            
            // Associer le dossier si spécifié
            if (caseId != null && !caseId.isEmpty()) {
                caseRepository.findById(caseId).ifPresent(cs -> {
                    if (cs.getLawyer() != null && cs.getLawyer().getId().equals(user.getId())) {
                        appointment.setRelatedCase(cs);
                    } else {
                        throw new RuntimeException("Dossier non autorisé");
                    }
                });
            }
            
            appointmentService.updateAppointment(id, appointment);
            
            redirectAttributes.addFlashAttribute("success", "Rendez-vous mis à jour avec succès");
            return "redirect:/appointments";
            
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour du rendez-vous", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
            return "redirect:/appointments/" + id + "/edit";
        }
    }

    /**
     * Suppression d'un rendez-vous
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            
            // Vérifier l'appartenance
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
            
            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }
            
            appointmentService.deleteAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Rendez-vous supprimé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur lors de la suppression du rendez-vous", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        
        return "redirect:/appointments";
    }

    /**
     * Annulation d'un rendez-vous
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable String id, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
            
            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }
            
            appointmentService.cancelAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Rendez-vous annulé");
            
        } catch (Exception e) {
            log.error("Erreur lors de l'annulation du rendez-vous", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        
        return "redirect:/appointments";
    }

    /**
     * Confirmation d'un rendez-vous
     */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable String id, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
            
            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }
            
            appointmentService.confirmAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Rendez-vous confirmé");
            
        } catch (Exception e) {
            log.error("Erreur lors de la confirmation du rendez-vous", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        
        return "redirect:/appointments";
    }

    /**
     * Marquer comme terminé
     */
    @PostMapping("/{id}/complete")
    public String complete(@PathVariable String id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
            
            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }
            
            appointmentService.completeAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Rendez-vous marqué comme terminé");
            
        } catch (Exception e) {
            log.error("Erreur lors de la finalisation du rendez-vous", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        
        return "redirect:/appointments";
    }

    /**
     * Accepter la demande de report (avocat accepte la date proposée par le client)
     */
    @PostMapping("/{id}/accept-reschedule")
    @Transactional
    public String acceptReschedule(@PathVariable String id, Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }

            // Si le client a proposé une date, on l'applique
            if (appointment.getProposedDate() != null) {
                appointment.setAppointmentDate(appointment.getProposedDate());
            }
            appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
            appointment.setRescheduleRequestedBy(null);
            appointment.setProposedDate(null);
            appointment.setRescheduleMessage(null);
            appointmentService.saveAppointment(appointment);

            // Notifier le client
            if (appointment.getClient() != null && appointment.getClient().getEmail() != null) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
                emailService.sendEmail(appointment.getClient().getEmail(),
                    "Report accepté : " + appointment.getTitle(),
                    "Bonjour,\n\nVotre demande de report a été acceptée.\n\n" +
                    "Nouveau rendez-vous : " + appointment.getAppointmentDate().format(fmt) + "\n\n---\nDocAvocat");
            }

            redirectAttributes.addFlashAttribute("success", "Report accepté et rendez-vous confirmé.");
        } catch (Exception e) {
            log.error("Erreur acceptation report", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    /**
     * Refuser la demande de report (remettre en SCHEDULED)
     */
    @PostMapping("/{id}/refuse-reschedule")
    @Transactional
    public String refuseReschedule(@PathVariable String id, Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }

            appointment.setStatus(Appointment.AppointmentStatus.SCHEDULED);
            appointment.setRescheduleRequestedBy(null);
            appointment.setProposedDate(null);
            appointment.setRescheduleMessage(null);
            appointmentService.saveAppointment(appointment);

            // Notifier le client
            if (appointment.getClient() != null && appointment.getClient().getEmail() != null) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
                emailService.sendEmail(appointment.getClient().getEmail(),
                    "Demande de report refusée : " + appointment.getTitle(),
                    "Bonjour,\n\nVotre demande de report a été refusée.\n\n" +
                    "Le rendez-vous est maintenu le : " + appointment.getAppointmentDate().format(fmt) + "\n\n---\nDocAvocat");
            }

            redirectAttributes.addFlashAttribute("success", "Demande de report refusée. Rendez-vous maintenu.");
        } catch (Exception e) {
            log.error("Erreur refus report", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    /**
     * Proposer une nouvelle date (avocat contre-propose)
     */
    @PostMapping("/{id}/propose-date")
    @Transactional
    public String proposeDate(@PathVariable String id,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime proposedDate,
                              @RequestParam(required = false) String rescheduleMessage,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

            if (!appointment.getLawyer().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/appointments";
            }

            appointment.setStatus(Appointment.AppointmentStatus.RESCHEDULED);
            appointment.setRescheduleRequestedBy("LAWYER");
            appointment.setProposedDate(proposedDate);
            appointment.setRescheduleMessage(rescheduleMessage);
            appointmentService.saveAppointment(appointment);

            // Notifier le client
            if (appointment.getClient() != null && appointment.getClient().getEmail() != null) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
                String body = "Bonjour,\n\nVotre avocat propose une nouvelle date pour le rendez-vous :\n\n" +
                    appointment.getTitle() + "\n" +
                    "Date proposée : " + proposedDate.format(fmt) + "\n";
                if (rescheduleMessage != null && !rescheduleMessage.isEmpty()) {
                    body += "\nMessage : " + rescheduleMessage + "\n";
                }
                body += "\nConnectez-vous à votre espace client pour accepter ou proposer une autre date.\n\n---\nDocAvocat";
                emailService.sendEmail(appointment.getClient().getEmail(),
                    "Nouvelle date proposée : " + appointment.getTitle(), body);
            }

            redirectAttributes.addFlashAttribute("success", "Nouvelle date proposée au client.");
        } catch (Exception e) {
            log.error("Erreur proposition de date", e);
            redirectAttributes.addFlashAttribute("error", "Erreur: " + e.getMessage());
        }
        return "redirect:/appointments";
    }

    /**
     * API JSON pour FullCalendar
     */
    @GetMapping("/api/events")
    @ResponseBody
    public List<AppointmentEvent> getEvents(Authentication authentication,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        User user = getCurrentUser(authentication);
        List<Appointment> appointments = appointmentService.getAppointmentsByLawyerAndDateRange(
            user.getId(), start, end);
        
        return appointments.stream()
            .map(this::toEvent)
            .toList();
    }

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    /**
     * Force l'initialisation des proxies lazy des rendez-vous
     * (nécessaire car open-in-view=false)
     */
    private void initializeAppointmentProxies(List<Appointment> appointments) {
        if (appointments == null) return;
        for (Appointment a : appointments) {
            if (a.getClient() != null) {
                a.getClient().getName(); // force init
            }
            if (a.getRelatedCase() != null) {
                a.getRelatedCase().getName(); // force init
            }
            if (a.getLawyer() != null) {
                a.getLawyer().getFirstName(); // force init
            }
        }
    }

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    private AppointmentEvent toEvent(Appointment appointment) {
        return new AppointmentEvent(
            appointment.getId(),
            appointment.getTitle(),
            appointment.getAppointmentDate().toString(),
            appointment.getEndDate() != null ? appointment.getEndDate().toString() : null,
            appointment.getColor(),
            appointment.getType().getDisplayName(),
            appointment.getStatus().name()
        );
    }

    /**
     * DTO pour les événements du calendrier
     */
    private record AppointmentEvent(
        String id,
        String title,
        String start,
        String end,
        String color,
        String type,
        String status
    ) {}
}
