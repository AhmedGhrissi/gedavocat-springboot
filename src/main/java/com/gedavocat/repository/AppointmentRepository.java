package com.gedavocat.repository;

import com.gedavocat.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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

    // ========== Méthodes avec JOIN FETCH (open-in-view=false) ==========

    @Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.client LEFT JOIN FETCH a.relatedCase LEFT JOIN FETCH a.lawyer " +
           "WHERE a.lawyer.id = :lawyerId AND a.appointmentDate BETWEEN :startDate AND :endDate " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findByLawyerIdAndDateRangeWithRelations(
        @Param("lawyerId") String lawyerId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.client LEFT JOIN FETCH a.relatedCase LEFT JOIN FETCH a.lawyer " +
           "WHERE a.lawyer.id = :lawyerId AND a.appointmentDate > :now " +
           "AND a.status NOT IN ('CANCELLED', 'COMPLETED') " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findUpcomingAppointmentsByLawyerWithRelations(
        @Param("lawyerId") String lawyerId,
        @Param("now") LocalDateTime now
    );

    @Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.client LEFT JOIN FETCH a.relatedCase LEFT JOIN FETCH a.lawyer " +
           "WHERE a.lawyer.id = :lawyerId AND DATE(a.appointmentDate) = DATE(:date) " +
           "ORDER BY a.appointmentDate")
    List<Appointment> findTodayAppointmentsByLawyerWithRelations(
        @Param("lawyerId") String lawyerId,
        @Param("date") LocalDateTime date
    );

    @Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.client LEFT JOIN FETCH a.relatedCase LEFT JOIN FETCH a.lawyer " +
           "WHERE a.client.id = :clientId ORDER BY a.appointmentDate DESC")
    List<Appointment> findByClientIdWithRelationsOrderByAppointmentDateDesc(
        @Param("clientId") String clientId
    );

    @Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.client LEFT JOIN FETCH a.relatedCase LEFT JOIN FETCH a.lawyer " +
           "WHERE a.relatedCase.id = :caseId ORDER BY a.appointmentDate DESC")
    List<Appointment> findByRelatedCaseIdWithRelationsOrderByAppointmentDateDesc(
        @Param("caseId") String caseId
    );

    /**
     * Supprime la référence au dossier dans les rendez-vous (avant suppression du dossier)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Appointment a SET a.relatedCase = null WHERE a.relatedCase.id = :caseId")
    void clearRelatedCaseByCaseId(@Param("caseId") String caseId);

    /**
     * Supprime la référence au client dans les rendez-vous (avant suppression du client)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Appointment a SET a.client = null WHERE a.client.id = :clientId")
    void clearClientByClientId(@Param("clientId") String clientId);

    /**
     * Supprime les références aux dossiers d'un client donné (avant suppression du client)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Appointment a SET a.relatedCase = null WHERE a.relatedCase.id IN (SELECT c.id FROM Case c WHERE c.client.id = :clientId)")
    void clearRelatedCaseByClientId(@Param("clientId") String clientId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Appointment a WHERE a.lawyer.id = :lawyerId")
    void deleteByLawyerId(@Param("lawyerId") String lawyerId);
}
