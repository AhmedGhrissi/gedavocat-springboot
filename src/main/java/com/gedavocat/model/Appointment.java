package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Entité Appointment - Rendez-vous avec les clients ou audiences au tribunal
 */
@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appointment_lawyer", columnList = "lawyer_id"),
    @Index(name = "idx_appointment_client", columnList = "client_id"),
    @Index(name = "idx_appointment_case", columnList = "case_id"),
    @Index(name = "idx_appointment_date", columnList = "appointment_date"),
    @Index(name = "idx_appointment_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"lawyer", "client", "relatedCase"})
@EqualsAndHashCode(exclude = {"lawyer", "client", "relatedCase"})
public class Appointment {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "Le titre est obligatoire")
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "La date du rendez-vous est obligatoire")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Column(name = "appointment_date", nullable = false)
    private LocalDateTime appointmentDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    // Relation avec l'avocat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private User lawyer;

    // Relation avec le client (optionnel pour les audiences)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    // Relation avec le dossier (optionnel)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private Case relatedCase;

    // Lieu du rendez-vous
    @Column(length = 200)
    private String location;

    // Pour les audiences au tribunal
    @Column(name = "court_name", length = 200)
    private String courtName;

    @Column(name = "court_room", length = 50)
    private String courtRoom;

    @Column(name = "judge_name", length = 100)
    private String judgeName;

    // Notifications et rappels
    @Column(name = "send_reminder")
    private Boolean sendReminder = true;

    @Column(name = "reminder_sent")
    private Boolean reminderSent = false;

    @Column(name = "reminder_minutes_before")
    private Integer reminderMinutesBefore = 60; // 1 heure avant par défaut

    // Notes de l'avocat
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Lien visioconférence (optionnel)
    @Column(name = "video_conference_link", length = 500)
    private String videoConferenceLink;

    // Confirmation par le client
    @Column(name = "client_confirmed_at")
    private LocalDateTime clientConfirmedAt;

    // Report / négociation de date
    @Column(name = "reschedule_requested_by", length = 20)
    private String rescheduleRequestedBy; // "CLIENT" ou "LAWYER"

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Column(name = "proposed_date")
    private LocalDateTime proposedDate;

    @Column(name = "reschedule_message", length = 500)
    private String rescheduleMessage;

    // Couleur pour l'affichage dans le calendrier
    @Column(length = 7)
    private String color = "#3788d8";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==========================================
    // ÉNUMÉRATIONS
    // ==========================================

    public enum AppointmentType {
        CLIENT_MEETING("Rendez-vous client"),
        COURT_HEARING("Audience tribunal"),
        INTERNAL_MEETING("Réunion interne"),
        PHONE_CALL("Appel téléphonique"),
        VIDEO_CONFERENCE("Visioconférence"),
        SITE_VISIT("Visite sur site"),
        OTHER("Autre");

        private final String displayName;

        AppointmentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum AppointmentStatus {
        SCHEDULED("Planifié"),
        CONFIRMED("Confirmé"),
        IN_PROGRESS("En cours"),
        COMPLETED("Terminé"),
        CANCELLED("Annulé"),
        RESCHEDULED("Reporté"),
        NO_SHOW("Absence");

        private final String displayName;

        AppointmentStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    public boolean isUpcoming() {
        return appointmentDate != null && appointmentDate.isAfter(LocalDateTime.now());
    }

    public boolean isPast() {
        return appointmentDate != null && appointmentDate.isBefore(LocalDateTime.now());
    }

    public boolean isToday() {
        if (appointmentDate == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return appointmentDate.toLocalDate().equals(now.toLocalDate());
    }

    public boolean needsReminder() {
        if (!sendReminder || reminderSent || appointmentDate == null) {
            return false;
        }
        LocalDateTime reminderTime = appointmentDate.minusMinutes(reminderMinutesBefore);
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(reminderTime) && now.isBefore(appointmentDate);
    }
}
