package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.User;
import com.gedavocat.model.Client;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
import com.gedavocat.service.EmailService;
import com.gedavocat.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Slf4j
@Controller
@RequestMapping("/client/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CLIENT')")
public class AppointmentClientController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @GetMapping
    @Transactional(readOnly = true)
    public String list(Authentication authentication, Model model) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            model.addAttribute("errorMessage", "Votre profil client n'a pas encore été activé.");
            return "client-portal/pending";
        }
        Client client = clientOpt.get();

        var appointments = appointmentService.getAppointmentsByClient(client.getId());
        for (var a : appointments) {
            if (a.getLawyer() != null) a.getLawyer().getFirstName();
            if (a.getRelatedCase() != null) a.getRelatedCase().getName();
        }

        model.addAttribute("user", user);
        model.addAttribute("client", client);
        model.addAttribute("appointments", appointments);
        return "client-portal/appointments";
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    public String confirm(@PathVariable String id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Appointment appointment = appointmentService.getAppointmentById(id)
                    .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

            if (!isClientOwner(appointment, user)) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/client/appointments";
            }

            appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
            appointment.setClientConfirmedAt(LocalDateTime.now());
            appointment.setRescheduleRequestedBy(null);
            appointment.setProposedDate(null);
            appointment.setRescheduleMessage(null);
            appointmentService.saveAppointment(appointment);

            if (appointment.getLawyer() != null) {
                String dateStr = appointment.getAppointmentDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"));
                notificationService.create(appointment.getLawyer(), "APPOINTMENT_CONFIRMED_BY_CLIENT",
                        "Rendez-vous confirmé par le client",
                        "Le client a confirmé le rendez-vous le " + dateStr,
                        "/appointments", "fa-check", "success");
                if (appointment.getLawyer().getEmail() != null) {
                    String dateStr2 = appointment.getAppointmentDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"));
                    emailService.sendEmail(appointment.getLawyer().getEmail(),
                            "Rendez-vous confirmé par le client",
                            "Votre client a confirmé le rendez-vous :\n" + appointment.getTitle() + "\nDate : " + dateStr2);
                }
            }

            redirectAttributes.addFlashAttribute("success", "Rendez-vous confirmé");
        } catch (Exception e) {
            log.error("Erreur confirmation client", e);
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue lors de la confirmation du rendez-vous.");
        }
        return "redirect:/client/appointments";
    }

    @PostMapping("/{id}/refuse")
    @Transactional
    public String refuse(@PathVariable String id,
                         @RequestParam(required = false) String reason,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            if (reason == null || reason.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vous devez fournir un motif de refus.");
                return "redirect:/client/appointments";
            }

            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Appointment appointment = appointmentService.getAppointmentById(id)
                    .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

            if (!isClientOwner(appointment, user)) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/client/appointments";
            }

            appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
            appointment.setRescheduleRequestedBy("CLIENT");
            appointment.setRescheduleMessage(reason);
            appointment.setProposedDate(null);
            appointmentService.saveAppointment(appointment);

            if (appointment.getLawyer() != null) {
                notificationService.create(appointment.getLawyer(), "APPOINTMENT_REFUSED_BY_CLIENT",
                        "Rendez-vous refusé par le client",
                        "Le client a refusé le rendez-vous. Motif: " + reason,
                        "/appointments", "fa-times", "danger");
                if (appointment.getLawyer().getEmail() != null) {
                    emailService.sendEmail(appointment.getLawyer().getEmail(),
                            "Rendez-vous refusé par le client",
                            "Le client a refusé le rendez-vous : " + appointment.getTitle() + "\nMotif: " + reason);
                }
            }

            redirectAttributes.addFlashAttribute("success", "Refus envoyé à l'avocat");
        } catch (Exception e) {
            log.error("Erreur refus client", e);
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue lors du refus du rendez-vous.");
        }
        return "redirect:/client/appointments";
    }

    @PostMapping("/{id}/propose-date")
    @Transactional
    public String proposeDate(@PathVariable String id,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime proposedDate,
                              @RequestParam(required = false) String message,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            if (proposedDate == null) {
                redirectAttributes.addFlashAttribute("error", "Date proposée invalide");
                return "redirect:/client/appointments";
            }

            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Appointment appointment = appointmentService.getAppointmentById(id)
                    .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

            if (!isClientOwner(appointment, user)) {
                redirectAttributes.addFlashAttribute("error", "Accès non autorisé");
                return "redirect:/client/appointments";
            }

            appointment.setStatus(Appointment.AppointmentStatus.RESCHEDULED);
            appointment.setRescheduleRequestedBy("CLIENT");
            appointment.setProposedDate(proposedDate);
            appointment.setRescheduleMessage(message);
            appointmentService.saveAppointment(appointment);

            if (appointment.getLawyer() != null) {
                String dateStr = proposedDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"));
                notificationService.create(appointment.getLawyer(), "APPOINTMENT_RESCHEDULE_PROPOSED_BY_CLIENT",
                        "Proposition de report du client",
                        "Le client propose une nouvelle date: " + dateStr,
                        "/appointments", "fa-calendar-alt", "warning");
                if (appointment.getLawyer().getEmail() != null) {
                    emailService.sendEmail(appointment.getLawyer().getEmail(),
                            "Proposition de report du client",
                            "Le client propose une nouvelle date pour le rendez-vous : " + appointment.getTitle()
                                    + "\nDate proposée: " + dateStr
                                    + (message != null ? "\nMessage: " + message : ""));
                }
            }

            redirectAttributes.addFlashAttribute("success", "Proposition envoyée à l'avocat");
        } catch (Exception e) {
            log.error("Erreur proposition date client", e);
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue lors de la proposition de date.");
        }
        return "redirect:/client/appointments";
    }

    private boolean isClientOwner(Appointment appointment, User user) {
        return appointment.getClient() != null
                && appointment.getClient().getClientUser() != null
                && appointment.getClient().getClientUser().getId().equals(user.getId());
    }
}
