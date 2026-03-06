package com.gedavocat.repository;

import com.gedavocat.model.CaseAssignment;
import com.gedavocat.model.CaseAssignment.AssignmentRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité CaseAssignment
 * MULTI-TENANT: Isolation automatique via filtre Hibernate
 * 
 * Sécurité :
 * - Toutes les requêtes sont automatiquement filtrées par firm_id
 * - Utilisation du filtre "firmFilter" activé par MultiTenantFilter
 * - Prévention des fuites de données cross-tenant
 * - Vérification des droits avant toute action
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Repository
public interface CaseAssignmentRepository extends JpaRepository<CaseAssignment, String> {

    /**
     * Trouve toutes les affectations actives d'un dossier
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca " +
           "WHERE ca.caseEntity.id = :caseId AND ca.isActive = true AND ca.revokedAt IS NULL " +
           "AND (ca.expiresAt IS NULL OR ca.expiresAt > :now)")
    List<CaseAssignment> findActiveByCaseId(@Param("caseId") String caseId, @Param("now") LocalDateTime now);

    /**
     * Trouve toutes les affectations d'un dossier (actives et inactives)
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca WHERE ca.caseEntity.id = :caseId")
    List<CaseAssignment> findAllByCaseId(@Param("caseId") String caseId);

    /**
     * Trouve toutes les affectations actives d'un membre
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca " +
           "WHERE ca.member.id = :memberId AND ca.isActive = true AND ca.revokedAt IS NULL " +
           "AND (ca.expiresAt IS NULL OR ca.expiresAt > :now)")
    List<CaseAssignment> findActiveByMemberId(@Param("memberId") String memberId, @Param("now") LocalDateTime now);

    /**
     * Trouve une affectation spécifique
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca WHERE ca.caseEntity.id = :caseId AND ca.member.id = :memberId")
    Optional<CaseAssignment> findByCaseIdAndMemberId(@Param("caseId") String caseId, @Param("memberId") String memberId);

    /**
     * Trouve les responsables d'un dossier
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca " +
           "WHERE ca.caseEntity.id = :caseId AND ca.assignmentRole = 'RESPONSABLE' " +
           "AND ca.isActive = true AND ca.revokedAt IS NULL")
    List<CaseAssignment> findResponsablesByCaseId(@Param("caseId") String caseId);

    /**
     * Compte le nombre d'affectations actives pour un dossier
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT COUNT(ca) FROM CaseAssignment ca " +
           "WHERE ca.caseEntity.id = :caseId AND ca.isActive = true AND ca.revokedAt IS NULL " +
           "AND (ca.expiresAt IS NULL OR ca.expiresAt > :now)")
    long countActiveByCaseId(@Param("caseId") String caseId, @Param("now") LocalDateTime now);

    /**
     * Compte le nombre de dossiers affectés à un membre
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT COUNT(ca) FROM CaseAssignment ca " +
           "WHERE ca.member.id = :memberId AND ca.isActive = true AND ca.revokedAt IS NULL " +
           "AND (ca.expiresAt IS NULL OR ca.expiresAt > :now)")
    long countActiveByMemberId(@Param("memberId") String memberId, @Param("now") LocalDateTime now);

    /**
     * Vérifie si un membre a accès à un dossier
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT CASE WHEN COUNT(ca) > 0 THEN true ELSE false END FROM CaseAssignment ca " +
           "WHERE ca.caseEntity.id = :caseId AND ca.member.id = :memberId " +
           "AND ca.isActive = true AND ca.revokedAt IS NULL " +
           "AND (ca.expiresAt IS NULL OR ca.expiresAt > :now)")
    boolean hasAccess(@Param("caseId") String caseId, @Param("memberId") String memberId, @Param("now") LocalDateTime now);

    /**
     * Vérifie si un membre peut écrire sur un dossier
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT CASE WHEN COUNT(ca) > 0 THEN true ELSE false END FROM CaseAssignment ca " +
           "WHERE ca.caseEntity.id = :caseId AND ca.member.id = :memberId " +
           "AND ca.isActive = true AND ca.canWrite = true AND ca.revokedAt IS NULL " +
           "AND (ca.expiresAt IS NULL OR ca.expiresAt > :now)")
    boolean canWrite(@Param("caseId") String caseId, @Param("memberId") String memberId, @Param("now") LocalDateTime now);

    /**
     * Trouve toutes les affectations d'un cabinet
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca WHERE ca.firm.id = :firmId AND ca.isActive = true")
    List<CaseAssignment> findAllByFirmId(@Param("firmId") String firmId);

    /**
     * Trouve les affectations expirées à nettoyer
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca " +
           "WHERE ca.firm.id = :firmId AND ca.isActive = true " +
           "AND ca.expiresAt IS NOT NULL AND ca.expiresAt < :now")
    List<CaseAssignment> findExpired(@Param("firmId") String firmId, @Param("now") LocalDateTime now);

    /**
     * Trouve les affectations créées par un utilisateur
     * AUDIT: Traçabilité des actions
     */
    @Query("SELECT ca FROM CaseAssignment ca WHERE ca.firm.id = :firmId AND ca.assignedByUser.id = :userId")
    List<CaseAssignment> findAssignedBy(@Param("firmId") String firmId, @Param("userId") String userId);

    /**
     * Trouve les affectations par rôle
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT ca FROM CaseAssignment ca " +
           "WHERE ca.caseEntity.id = :caseId AND ca.assignmentRole = :role AND ca.isActive = true")
    List<CaseAssignment> findByCaseIdAndRole(@Param("caseId") String caseId, @Param("role") AssignmentRole role);
}
