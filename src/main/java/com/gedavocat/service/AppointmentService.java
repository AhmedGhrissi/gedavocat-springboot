package com.gedavocat.service;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.User;
import com.gedavocat.repository.AppointmentRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service pour la gestion des rendez-vous
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final AppointmentReminderService appointmentReminderService;

    /**
     * Crée un nouveau rendez-vous
     */
    @Transactional
    public Appointment createAppointment(Appointment appointment, String lawyerId) {
        log.info("Création d'un rendez-vous pour l'avocat: {}", lawyerId);

        // Générer un ID si nécessaire
        if (appointment.getId() == null || appointment.getId().isEmpty()) {
            appointment.setId(UUID.randomUUID().toString());
        }

        // Associer l'avocat
        User lawyer = userRepository.findById(lawyerId)
            .orElseThrow(() -> new RuntimeException("Avocat non trouvé"));
        appointment.setLawyer(lawyer);

        // Vérifier les conflits d'horaires
        if (hasScheduleConflict(lawyerId, appointment.getAppointmentDate(), appointment.getId())) {
            throw new RuntimeException("Conflit d'horaire: un autre rendez-vous existe déjà à cette heure");
        }

        Appointment saved = appointmentRepository.save(appointment);

        // Envoyer les notifications (email) de manière asynchrone
        try {
            appointmentReminderService.sendAppointmentCreatedNotification(saved);
        } catch (Exception e) {
            log.error("Erreur notification création rendez-vous: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Met à jour un rendez-vous existant
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public Appointment updateAppointment(String appointmentId, Appointment updatedAppointment, String lawyerId) {
        log.info("Mise à jour du rendez-vous: {}", appointmentId);

        Appointment existing = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

        // SÉCURITÉ : vérifier que le rendez-vous appartient à l'avocat connecté
        if (existing.getLawyer() == null || !existing.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce rendez-vous");
        }

        // Vérifier les conflits si la date a changé
        if (!existing.getAppointmentDate().equals(updatedAppointment.getAppointmentDate())) {
            if (hasScheduleConflict(existing.getLawyer().getId(), 
                                   updatedAppointment.getAppointmentDate(), 
                                   appointmentId)) {
                throw new RuntimeException("Conflit d'horaire: un autre rendez-vous existe déjà à cette heure");
            }
        }

        // Mettre à jour les champs
        existing.setTitle(updatedAppointment.getTitle());
        existing.setDescription(updatedAppointment.getDescription());
        existing.setAppointmentDate(updatedAppointment.getAppointmentDate());
        existing.setEndDate(updatedAppointment.getEndDate());
        existing.setType(updatedAppointment.getType());
        existing.setStatus(updatedAppointment.getStatus());
        existing.setLocation(updatedAppointment.getLocation());
        existing.setCourtName(updatedAppointment.getCourtName());
        existing.setCourtRoom(updatedAppointment.getCourtRoom());
        existing.setJudgeName(updatedAppointment.getJudgeName());
        existing.setNotes(updatedAppointment.getNotes());
        existing.setVideoConferenceLink(updatedAppointment.getVideoConferenceLink());
        existing.setColor(updatedAppointment.getColor());

        return appointmentRepository.save(existing);
    }

    /**
     * Vérifie s'il y a un conflit d'horaire
     */
    private boolean hasScheduleConflict(String lawyerId, LocalDateTime appointmentDate, String excludeId) {
        LocalDateTime startWindow = appointmentDate.minusMinutes(30);
        LocalDateTime endWindow = appointmentDate.plusMinutes(30);

        List<Appointment> conflicts = appointmentRepository.findByLawyerIdAndDateRange(
            lawyerId, startWindow, endWindow
        );

        return conflicts.stream()
            .filter(a -> !a.getId().equals(excludeId))
            .filter(a -> a.getStatus() != Appointment.AppointmentStatus.CANCELLED)
            .anyMatch(a -> true);
    }

    /**
     * Récupère tous les rendez-vous d'un avocat
     */
    public List<Appointment> getAppointmentsByLawyer(String lawyerId) {
        return appointmentRepository.findByLawyerIdOrderByAppointmentDateDesc(lawyerId);
    }

    /**
     * Récupère les rendez-vous d'un avocat pour une période donnée
     */
    public List<Appointment> getAppointmentsByLawyerAndDateRange(String lawyerId, 
                                                                  LocalDate startDate, 
                                                                  LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return appointmentRepository.findByLawyerIdAndDateRangeWithRelations(lawyerId, start, end);
    }

    /**
     * Récupère les rendez-vous à venir d'un avocat
     */
    public List<Appointment> getUpcomingAppointments(String lawyerId) {
        return appointmentRepository.findUpcomingAppointmentsByLawyerWithRelations(lawyerId, LocalDateTime.now());
    }

    /**
     * Récupère les rendez-vous du jour
     */
    public List<Appointment> getTodayAppointments(String lawyerId) {
        return appointmentRepository.findTodayAppointmentsByLawyerWithRelations(lawyerId, LocalDateTime.now());
    }

    /**
     * Récupère les audiences au tribunal à venir
     */
    public List<Appointment> getUpcomingCourtHearings(String lawyerId) {
        return appointmentRepository.findUpcomingCourtHearings(lawyerId, LocalDateTime.now());
    }

    /**
     * Récupère les rendez-vous d'un client
     */
    public List<Appointment> getAppointmentsByClient(String clientId) {
        return appointmentRepository.findByClientIdWithRelationsOrderByAppointmentDateDesc(clientId);
    }

    /**
     * Récupère les rendez-vous en attente de report pour un avocat
     */
    public List<Appointment> getRescheduledAppointments(String lawyerId) {
        return appointmentRepository.findByLawyerIdAndStatusOrderByAppointmentDateDesc(
            lawyerId, Appointment.AppointmentStatus.RESCHEDULED);
    }

    /**
     * Récupère les rendez-vous liés à un dossier
     */
    public List<Appointment> getAppointmentsByCase(String caseId) {
        return appointmentRepository.findByRelatedCaseIdWithRelationsOrderByAppointmentDateDesc(caseId);
    }

    /**
     * Récupère un rendez-vous par son ID
     */
    public Optional<Appointment> getAppointmentById(String appointmentId) {
        return appointmentRepository.findById(appointmentId);
    }

    /**
     * Annule un rendez-vous
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public Appointment cancelAppointment(String appointmentId, String lawyerId) {
        log.info("Annulation du rendez-vous: {}", appointmentId);

        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

        // SÉCURITÉ : vérifier ownership
        if (appointment.getLawyer() == null || !appointment.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce rendez-vous");
        }

        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        return appointmentRepository.save(appointment);
    }

    /**
     * Confirme un rendez-vous
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public Appointment confirmAppointment(String appointmentId, String lawyerId) {
        log.info("Confirmation du rendez-vous: {}", appointmentId);

        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

        // SÉCURITÉ : vérifier ownership
        if (appointment.getLawyer() == null || !appointment.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce rendez-vous");
        }

        appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    /**
     * Marque un rendez-vous comme terminé
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public Appointment completeAppointment(String appointmentId, String lawyerId) {
        log.info("Marquage du rendez-vous comme terminé: {}", appointmentId);

        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));

        // SÉCURITÉ : vérifier ownership
        if (appointment.getLawyer() == null || !appointment.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce rendez-vous");
        }

        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        return appointmentRepository.save(appointment);
    }

    /**
     * Sauvegarde directe d'un rendez-vous (pour mises à jour simples)
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public Appointment saveAppointment(Appointment appointment, String lawyerId) {
        if (appointment.getLawyer() == null || !appointment.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce rendez-vous");
        }
        return appointmentRepository.save(appointment);
    }

    /**
     * Sauvegarde directe d'un rendez-vous — usage interne uniquement
     * (quand l'ownership est déjà vérifiée par le controller appelant)
     */
    @Transactional
    public Appointment saveAppointment(Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    /**
     * Supprime un rendez-vous
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public void deleteAppointment(String appointmentId, String lawyerId) {
        log.info("Suppression du rendez-vous: {}", appointmentId);
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
        // SÉCURITÉ : vérifier ownership
        if (appointment.getLawyer() == null || !appointment.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce rendez-vous");
        }
        appointmentRepository.deleteById(appointmentId);
    }

    /**
     * Récupère les rendez-vous nécessitant un rappel
     */
    public List<Appointment> getAppointmentsNeedingReminder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderWindow = now.plusHours(2); // Fenêtre de 2 heures
        return appointmentRepository.findAppointmentsNeedingReminder(now, reminderWindow);
    }

    /**
     * Marque le rappel comme envoyé
     */
    @Transactional
    public void markReminderSent(String appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Rendez-vous non trouvé"));
        appointment.setReminderSent(true);
        appointmentRepository.save(appointment);
    }

    /**
     * Statistiques des rendez-vous pour un avocat
     */
    public AppointmentStats getStatistics(String lawyerId) {
        long scheduled = appointmentRepository.countByLawyerIdAndStatus(
            lawyerId, Appointment.AppointmentStatus.SCHEDULED);
        long confirmed = appointmentRepository.countByLawyerIdAndStatus(
            lawyerId, Appointment.AppointmentStatus.CONFIRMED);
        long completed = appointmentRepository.countByLawyerIdAndStatus(
            lawyerId, Appointment.AppointmentStatus.COMPLETED);
        long cancelled = appointmentRepository.countByLawyerIdAndStatus(
            lawyerId, Appointment.AppointmentStatus.CANCELLED);
        long rescheduled = appointmentRepository.countByLawyerIdAndStatus(
            lawyerId, Appointment.AppointmentStatus.RESCHEDULED);

        return new AppointmentStats(scheduled, confirmed, completed, cancelled, rescheduled);
    }

    /**
     * Classe pour les statistiques
     */
    public record AppointmentStats(
        long scheduled,
        long confirmed,
        long completed,
        long cancelled,
        long rescheduled
    ) {}
}
