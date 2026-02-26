package com.gedavocat.service;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.User;
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
public class AppointmentReminderService {

    private final AppointmentRepository appointmentRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

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
        
        // Envoyer le rappel à l'avocat
        String subject = "Rappel: " + appointment.getTitle();
        String message = buildReminderMessage(appointment, formattedDate);
        emailService.sendEmail(lawyerEmail, subject, message);
        
        log.info("Rappel envoyé à {} ({}) pour le rendez-vous du {}", 
            lawyerName, lawyerEmail, formattedDate);
        
        // Créer notification in-app pour l'avocat
        try {
            notificationService.create(appointment.getLawyer(), "APPOINTMENT_REMINDER", "Rappel de rendez-vous",
                    "Rappel: " + appointment.getTitle() + " le " + formattedDate,
                    "/appointments", "fa-calendar-check", "primary");
        } catch (Exception e) {
            log.warn("Impossible de créer notification in-app pour l'avocat: {}", e.getMessage());
        }
        
        // Si un client est associé, lui envoyer aussi un rappel
        if (appointment.getClient() != null) {
            if (appointment.getClient().getEmail() != null) {
                String clientEmail = appointment.getClient().getEmail();
                String clientSubject = "Rappel: Votre rendez-vous du " + formattedDate;
                String clientMessage = buildClientReminderMessage(appointment, formattedDate);
                emailService.sendEmail(clientEmail, clientSubject, clientMessage);
                
                log.info("Rappel client envoyé à {}", clientEmail);
            }

            // Créer notification in-app pour le client utilisateur si lié
            try {
                if (appointment.getClient().getClientUser() != null) {
                    User clientUser = appointment.getClient().getClientUser();
                    notificationService.create(clientUser, "APPOINTMENT_REMINDER", "Rappel de rendez-vous",
                            "Rappel: " + appointment.getTitle() + " le " + formattedDate,
                            "/client/appointments", "fa-calendar-check", "primary");
                }
            } catch (Exception e) {
                log.warn("Impossible de créer notification in-app pour le client: {}", e.getMessage());
            }
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
            String formattedDate = appointment.getAppointmentDate().format(formatter);

            // Notifier l'avocat
            String subject = "Nouveau rendez-vous: " + appointment.getTitle();
            String message = buildAppointmentCreatedMessage(appointment, formattedDate);
            emailService.sendEmail(appointment.getLawyer().getEmail(), subject, message);
            
            // Notification in-app pour l'avocat
            try {
                notificationService.create(appointment.getLawyer(), "APPOINTMENT_CREATED", "Nouveau rendez-vous",
                        "Rendez-vous créé: " + appointment.getTitle() + " le " + formattedDate,
                        "/appointments", "fa-calendar-check", "primary");
            } catch (Exception e) {
                log.warn("Impossible de créer notification in-app avocat: {}", e.getMessage());
            }
                        
            // Notifier le client s'il est associé
            if (appointment.getClient() != null) {
                if (appointment.getClient().getEmail() != null) {
                    String clientSubject = "Nouveau rendez-vous planifié le " + formattedDate;
                    String clientMessage = buildClientAppointmentCreatedMessage(appointment, formattedDate);
                    emailService.sendEmail(appointment.getClient().getEmail(), clientSubject, clientMessage);
                }

                // Notification in-app pour le client si lié à un User
                try {
                    if (appointment.getClient().getClientUser() != null) {
                        User clientUser = appointment.getClient().getClientUser();
                        notificationService.create(clientUser, "APPOINTMENT_CREATED", "Nouveau rendez-vous",
                                "Me " + appointment.getLawyer().getName() + " vous a planifié un rendez-vous le " + formattedDate,
                                "/client/appointments", "fa-calendar-check", "primary");
                    }
                } catch (Exception e) {
                    log.warn("Impossible de créer notification in-app client: {}", e.getMessage());
                }
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
