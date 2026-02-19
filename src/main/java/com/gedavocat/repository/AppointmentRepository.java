package com.gedavocat.repository;

import com.gedavocat.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour les rendez-vous
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    /**
     * Trouve tous les rendez-vous d'un avocat
     */
    List<Appointment> findByLawyerIdOrderByAppointmentDateDesc(String lawyerId);

    /**
     * Trouve les rendez-vous d'un avocat entre deux dates
     */
    @Query("SELECT a FROM Appointment a WHERE a.lawyer.id = :lawyerId " +
           "AND a.appointmentDate BETWEEN :startDate AND :endDate " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findByLawyerIdAndDateRange(
        @Param("lawyerId") String lawyerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Trouve les rendez-vous d'un client
     */
    List<Appointment> findByClientIdOrderByAppointmentDateDesc(String clientId);

    /**
     * Trouve les rendez-vous liés à un dossier
     */
    List<Appointment> findByRelatedCaseIdOrderByAppointmentDateDesc(String caseId);

    /**
     * Trouve les rendez-vous à venir d'un avocat
     */
    @Query("SELECT a FROM Appointment a WHERE a.lawyer.id = :lawyerId " +
           "AND a.appointmentDate > :now " +
           "AND a.status NOT IN ('CANCELLED', 'COMPLETED') " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findUpcomingAppointmentsByLawyer(
        @Param("lawyerId") String lawyerId,
        @Param("now") LocalDateTime now
    );

    /**
     * Trouve les rendez-vous du jour pour un avocat
     */
    @Query("SELECT a FROM Appointment a WHERE a.lawyer.id = :lawyerId " +
           "AND DATE(a.appointmentDate) = DATE(:date) " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findTodayAppointmentsByLawyer(
        @Param("lawyerId") String lawyerId,
        @Param("date") LocalDateTime date
    );

    /**
     * Trouve les rendez-vous nécessitant un rappel
     */
    @Query("SELECT a FROM Appointment a WHERE a.sendReminder = true " +
           "AND a.reminderSent = false " +
           "AND a.appointmentDate > :now " +
           "AND a.appointmentDate <= :reminderTime " +
           "AND a.status NOT IN ('CANCELLED', 'COMPLETED')")
    List<Appointment> findAppointmentsNeedingReminder(
        @Param("now") LocalDateTime now,
        @Param("reminderTime") LocalDateTime reminderTime
    );

    /**
     * Compte les rendez-vous d'un avocat par statut
     */
    long countByLawyerIdAndStatus(String lawyerId, Appointment.AppointmentStatus status);

    /**
     * Trouve les audiences au tribunal pour un avocat
     */
    @Query("SELECT a FROM Appointment a WHERE a.lawyer.id = :lawyerId " +
           "AND a.type = 'COURT_HEARING' " +
           "AND a.appointmentDate > :now " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findUpcomingCourtHearings(
        @Param("lawyerId") String lawyerId,
        @Param("now") LocalDateTime now
    );
}
