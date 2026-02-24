package com.gedavocat.service;

import com.gedavocat.model.Appointment;
import com.gedavocat.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service pour les notifications et rappels automatiques des rendez-vous
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unused") // Méthodes préparées pour EmailService (TODO)
public class AppointmentReminderService {

    private final AppointmentRepository appointmentRepository;
    // TODO: Injecter EmailService quand il sera disponible
    // private final EmailService emailService;

    /**
     * Tâche planifiée pour envoyer les rappels toutes les 15 minutes
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    @Transactional
    public void sendReminders() {
        log.info("Vérification des rendez-vous nécessitant un rappel...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderWindow = now.plusHours(2); // Fenêtre de 2 heures

        List<Appointment> appointmentsNeedingReminder = 
            appointmentRepository.findAppointmentsNeedingReminder(now, reminderWindow);

        log.info("Trouvé {} rendez-vous nécessitant un rappel", appointmentsNeedingReminder.size());

        for (Appointment appointment : appointmentsNeedingReminder) {
            try {
                sendReminderNotification(appointment);
                
                // Marquer le rappel comme envoyé
                appointment.setReminderSent(true);
                appointmentRepository.save(appointment);
                
                log.info("Rappel envoyé pour le rendez-vous: {} - {}", 
                    appointment.getId(), appointment.getTitle());
            } catch (Exception e) {
                log.error("Erreur lors de l'envoi du rappel pour le rendez-vous: {}", 
                    appointment.getId(), e);
            }
        }
    }

    /**
     * Envoie une notification de rappel pour un rendez-vous
     */
    private void sendReminderNotification(Appointment appointment) {
        String lawyerEmail = appointment.getLawyer().getEmail();
        String lawyerName = appointment.getLawyer().getName();
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
        String formattedDate = appointment.getAppointmentDate().format(formatter);
        
        // Préparer le sujet et message (utilisés quand EmailService sera implémenté)
        // String subject = "Rappel: " + appointment.getTitle();
        // String message = buildReminderMessage(appointment, formattedDate);
        
        // TODO: Utiliser EmailService pour envoyer l'email
        // emailService.sendEmail(lawyerEmail, subject, message);
        
        log.info("Rappel à envoyer à {} ({}) pour le rendez-vous du {}", 
            lawyerName, lawyerEmail, formattedDate);
        
        // Si un client est associé, lui envoyer aussi un rappel
        if (appointment.getClient() != null && appointment.getClient().getEmail() != null) {
            String clientEmail = appointment.getClient().getEmail();
            // String clientMessage = buildClientReminderMessage(appointment, formattedDate);
            
            // TODO: emailService.sendEmail(clientEmail, subject, clientMessage);
            log.info("Rappel client à envoyer à {}", clientEmail);
        }
    }

    /**
     * Construit le message de rappel pour l'avocat
     */
    private String buildReminderMessage(Appointment appointment, String formattedDate) {
        StringBuilder message = new StringBuilder();
        message.append("Bonjour,\n\n");
        message.append("Rappel de votre rendez-vous:\n\n");
        message.append("📅 ").append(appointment.getTitle()).append("\n");
        message.append("🕒 ").append(formattedDate).append("\n");
        
        if (appointment.getType() != null) {
            message.append("📋 Type: ").append(appointment.getType().getDisplayName()).append("\n");
        }
        
        if (appointment.getClient() != null) {
            message.append("👤 Client: ").append(appointment.getClient().getName()).append("\n");
        }
        
        if (appointment.getLocation() != null) {
            message.append("📍 Lieu: ").append(appointment.getLocation()).append("\n");
        }
        
        if (appointment.getCourtName() != null) {
            message.append("⚖️ Tribunal: ").append(appointment.getCourtName()).append("\n");
            if (appointment.getCourtRoom() != null) {
                message.append("   Salle: ").append(appointment.getCourtRoom()).append("\n");
            }
            if (appointment.getJudgeName() != null) {
                message.append("   Juge: ").append(appointment.getJudgeName()).append("\n");
            }
        }
        
        if (appointment.getVideoConferenceLink() != null) {
            message.append("💻 Lien visio: ").append(appointment.getVideoConferenceLink()).append("\n");
        }
        
        if (appointment.getDescription() != null) {
            message.append("\nDescription:\n").append(appointment.getDescription()).append("\n");
        }
        
        if (appointment.getNotes() != null) {
            message.append("\nNotes:\n").append(appointment.getNotes()).append("\n");
        }
        
        message.append("\n---\n");
        message.append("GED Avocat - Gestion de cabinet\n");
        
        return message.toString();
    }

    /**
     * Construit le message de rappel pour le client
     */
    private String buildClientReminderMessage(Appointment appointment, String formattedDate) {
        StringBuilder message = new StringBuilder();
        message.append("Bonjour,\n\n");
        message.append("Rappel de votre rendez-vous avec Maître ").append(appointment.getLawyer().getName()).append(":\n\n");
        message.append("📅 ").append(appointment.getTitle()).append("\n");
        message.append("🕒 ").append(formattedDate).append("\n");
        
        if (appointment.getLocation() != null) {
            message.append("📍 Lieu: ").append(appointment.getLocation()).append("\n");
        }
        
        if (appointment.getVideoConferenceLink() != null) {
            message.append("💻 Lien visio: ").append(appointment.getVideoConferenceLink()).append("\n");
        }
        
        if (appointment.getDescription() != null) {
            message.append("\nDétails:\n").append(appointment.getDescription()).append("\n");
        }
        
        message.append("\nEn cas d'empêchement, merci de contacter votre avocat.\n");
        message.append("\n---\n");
        message.append("GED Avocat - Gestion de cabinet\n");
        
        return message.toString();
    }

    /**
     * Envoie une notification immédiate pour un nouveau rendez-vous
     */
    public void sendAppointmentCreatedNotification(Appointment appointment) {
        log.info("Envoi de notification pour nouveau rendez-vous: {}", appointment.getId());
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
            // formattedDate sera utilisé avec EmailService
            appointment.getAppointmentDate().format(formatter);
            
            // Préparés pour usage avec EmailService (TODO)
            // String subject = "Nouveau rendez-vous: " + appointment.getTitle();
            // String message = buildAppointmentCreatedMessage(appointment, formattedDate);
            
            // TODO: emailService.sendEmail(appointment.getLawyer().getEmail(), subject, message);
            
            if (appointment.getClient() != null && appointment.getClient().getEmail() != null) {
                // String clientMessage = buildClientAppointmentCreatedMessage(appointment, formattedDate);
                // TODO: emailService.sendEmail(appointment.getClient().getEmail(), subject, clientMessage);
            }
            
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la notification de création", e);
        }
    }

    private String buildAppointmentCreatedMessage(Appointment appointment, String formattedDate) {
        return "Bonjour,\n\nVotre rendez-vous a été créé avec succès:\n\n" +
               "📅 " + appointment.getTitle() + "\n" +
               "🕒 " + formattedDate + "\n\n" +
               "Vous recevrez un rappel avant le rendez-vous.\n\n" +
               "---\nGED Avocat";
    }

    private String buildClientAppointmentCreatedMessage(Appointment appointment, String formattedDate) {
        return "Bonjour,\n\nVotre rendez-vous avec Maître " + appointment.getLawyer().getName() + 
               " a été confirmé:\n\n" +
               "📅 " + appointment.getTitle() + "\n" +
               "🕒 " + formattedDate + "\n\n" +
               "Vous recevrez un rappel avant le rendez-vous.\n\n" +
               "---\nGED Avocat";
    }
}
