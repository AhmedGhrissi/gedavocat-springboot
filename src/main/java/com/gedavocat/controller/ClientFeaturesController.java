package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Client;
import com.gedavocat.model.Signature;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.SignatureRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
import com.gedavocat.service.EmailService;
import com.gedavocat.service.NotificationService;
import com.gedavocat.service.YousignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur pour les fonctionnalités client hors /my-cases :
 * rendez-vous, signatures, changement de mot de passe.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
@Slf4j
public class ClientFeaturesController {

    private final AppointmentService appointmentService;
    private final SignatureRepository signatureRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final YousignService yousignService;
    private final NotificationService notificationService;

    // =========================================================================
    // Rendez-vous
    // =========================================================================

    @GetMapping("/my-appointments")
    @Transactional(readOnly = true)
    public String myAppointments(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            model.addAttribute("errorMessage",
                    "Votre profil client n'a pas encore été activé.");
            return "client-portal/pending";
        }
        Client client = clientOpt.get();

        List<Appointment> appointments;
        try {
            appointments = appointmentService.getAppointmentsByClient(client.getId());
            // Force-initialiser les proxies lazy (open-in-view=false)
            for (Appointment a : appointments) {
                if (a.getClient() != null) a.getClient().getName();
                if (a.getRelatedCase() != null) a.getRelatedCase().getName();
                if (a.getLawyer() != null) a.getLawyer().getFirstName();
            }
        } catch (Exception e) {
            appointments = Collections.emptyList();
        }

        model.addAttribute("appointments", appointments);
        model.addAttribute("client", client);
        model.addAttribute("user", user);
        return "client-portal/appointments";
    }

    // =========================================================================
    // Signatures
    // =========================================================================

    @GetMapping("/my-signatures")
    @Transactional
    public String mySignatures(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            model.addAttribute("errorMessage",
                    "Votre profil client n'a pas encore été activé.");
            return "client-portal/pending";
        }

        // Les signatures adressées au client par email
        List<Signature> signatures;
        try {
            signatures = signatureRepository.findBySignerEmail(user.getEmail());
            // Synchroniser le statut des signatures PENDING depuis Yousign
            for (Signature sig : signatures) {
                if (sig.getStatus() == Signature.SignatureStatus.PENDING) {
                    syncSignatureStatus(sig);
                }
            }
        } catch (Exception e) {
            signatures = Collections.emptyList();
        }

        model.addAttribute("signatures", signatures);
        model.addAttribute("user", user);
        return "client-portal/signatures";
    }

    // =========================================================================
    // Confirmation de rendez-vous par le client
    // =========================================================================

    @PostMapping("/my-appointments/{id}/confirm")
    @Transactional
    public String confirmAppointment(@PathVariable String id,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Profil client non activé.");
            return "redirect:/my-appointments";
        }
        Client client = clientOpt.get();

        var aptOpt = appointmentService.getAppointmentById(id);
        if (aptOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Rendez-vous non trouvé.");
            return "redirect:/my-appointments";
        }
        Appointment apt = aptOpt.get();

        // Vérifier que le rendez-vous appartient à ce client
        if (apt.getClient() == null || !apt.getClient().getId().equals(client.getId())) {
            redirectAttributes.addFlashAttribute("error", "Accès non autorisé.");
            return "redirect:/my-appointments";
        }

        apt.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        apt.setClientConfirmedAt(LocalDateTime.now());
        apt.setRescheduleRequestedBy(null);
        apt.setProposedDate(null);
        apt.setRescheduleMessage(null);
        appointmentService.saveAppointment(apt);

        // Notifier l'avocat par email
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
            emailService.sendEmail(apt.getLawyer().getEmail(),
                "Rendez-vous confirmé par " + client.getName(),
                "Bonjour,\n\nLe client " + client.getName() + " a confirmé le rendez-vous :\n\n" +
                apt.getTitle() + "\n" + apt.getAppointmentDate().format(fmt) + "\n\n---\nDocAvocat");
        } catch (Exception ignored) {}

        redirectAttributes.addFlashAttribute("success", "Rendez-vous confirmé avec succès.");
        return "redirect:/my-appointments";
    }

    // =========================================================================
    // Demande de report par le client
    // =========================================================================

    @PostMapping("/my-appointments/{id}/request-reschedule")
    @Transactional
    public String requestReschedule(@PathVariable String id,
                                    @RequestParam(required = false) String proposedDate,
                                    @RequestParam(required = false) String rescheduleMessage,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        User user = getCurrentUser(authentication);
        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Profil client non activé.");
            return "redirect:/my-appointments";
        }
        Client client = clientOpt.get();

        var aptOpt = appointmentService.getAppointmentById(id);
        if (aptOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Rendez-vous non trouvé.");
            return "redirect:/my-appointments";
        }
        Appointment apt = aptOpt.get();

        if (apt.getClient() == null || !apt.getClient().getId().equals(client.getId())) {
            redirectAttributes.addFlashAttribute("error", "Accès non autorisé.");
            return "redirect:/my-appointments";
        }

        apt.setStatus(Appointment.AppointmentStatus.RESCHEDULED);
        apt.setRescheduleRequestedBy("CLIENT");
        apt.setRescheduleMessage(rescheduleMessage);
        if (proposedDate != null && !proposedDate.isEmpty()) {
            apt.setProposedDate(LocalDateTime.parse(proposedDate));
        }
        appointmentService.saveAppointment(apt);

        // Notifier l'avocat
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
            String body = "Bonjour,\n\nLe client " + client.getName() + " demande le report du rendez-vous :\n\n" +
                apt.getTitle() + " - " + apt.getAppointmentDate().format(fmt) + "\n";
            if (rescheduleMessage != null && !rescheduleMessage.isEmpty()) {
                body += "\nMotif : " + rescheduleMessage + "\n";
            }
            if (apt.getProposedDate() != null) {
                body += "\nDate proposée : " + apt.getProposedDate().format(fmt) + "\n";
            }
            body += "\n---\nDocAvocat";
            emailService.sendEmail(apt.getLawyer().getEmail(),
                "Demande de report : " + apt.getTitle(), body);
        } catch (Exception ignored) {}

        redirectAttributes.addFlashAttribute("success", "Votre demande de report a été envoyée à votre avocat.");
        return "redirect:/my-appointments";
    }

    // =========================================================================
    // Changement de mot de passe (profil client)
    // =========================================================================

    @PostMapping("/my-cases/profile/change-password")
    @ResponseBody
    public Map<String, Object> changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmNewPassword,
            Authentication authentication
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            User user = getCurrentUser(authentication);

            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                result.put("success", false);
                result.put("message", "Le mot de passe actuel est incorrect.");
                return result;
            }
            if (newPassword.length() < 8) {
                result.put("success", false);
                result.put("message", "Le nouveau mot de passe doit contenir au moins 8 caractères.");
                return result;
            }
            // SEC-15 FIX : Mêmes exigences de complexité que l'inscription
            if (!com.gedavocat.util.PasswordValidator.isValid(newPassword)) {
                result.put("success", false);
                result.put("message", com.gedavocat.util.PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE);
                return result;
            }
            if (!newPassword.equals(confirmNewPassword)) {
                result.put("success", false);
                result.put("message", "Les mots de passe ne correspondent pas.");
                return result;
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            result.put("success", true);
            result.put("message", "Votre mot de passe a été modifié avec succès.");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Erreur : " + e.getMessage());
        }
        return result;
    }

    // =========================================================================

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    /**
     * Synchroniser le statut d'une signature depuis l'API Yousign
     */
    private void syncSignatureStatus(Signature signature) {
        if (signature.getYousignSignatureRequestId() == null || !yousignService.isConfigured()) {
            return;
        }
        try {
            Map<String, Object> yousignData = yousignService.getSignatureStatus(
                    signature.getYousignSignatureRequestId());
            if (yousignData == null) return;

            String yousignStatus = (String) yousignData.get("status");
            if (yousignStatus == null) return;

            Signature.SignatureStatus newStatus = switch (yousignStatus) {
                case "draft" -> Signature.SignatureStatus.DRAFT;
                case "ongoing", "approval" -> Signature.SignatureStatus.PENDING;
                case "done" -> Signature.SignatureStatus.SIGNED;
                case "declined", "canceled" -> Signature.SignatureStatus.REJECTED;
                case "expired", "deleted" -> Signature.SignatureStatus.EXPIRED;
                default -> null;
            };

            if (newStatus != null && newStatus != signature.getStatus()) {
                signature.setStatus(newStatus);
                if (newStatus == Signature.SignatureStatus.SIGNED) {
                    signature.setSignedAt(LocalDateTime.now());
                }
                signatureRepository.save(signature);
                log.info("Signature client {} : statut mis à jour → {}", signature.getId(), newStatus);
            }
        } catch (Exception e) {
            log.warn("Impossible de synchroniser la signature {} : {}", signature.getId(), e.getMessage());
        }
    }
}
