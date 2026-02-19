package com.gedavocat.repository;

import com.gedavocat.model.Case;
import com.gedavocat.model.Case.CaseStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, String> {

    /**
     * Trouve tous les dossiers d'un avocat
     */
    List<Case> findByLawyerId(String lawyerId);

    /**
     * Trouve tous les dossiers d'un client
     */
    List<Case> findByClientId(String clientId);

    /**
     * Trouve les dossiers par avocat et statut
     */
    List<Case> findByLawyerIdAndStatus(String lawyerId, CaseStatus status);

    /**
     * Trouve les dossiers par client et statut
     */
    List<Case> findByClientIdAndStatus(String clientId, CaseStatus status);

    /**
     * Recherche de dossiers par nom ou description
     */
    @Query("SELECT c FROM Case c WHERE c.lawyer.id = :lawyerId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Case> searchByLawyerAndNameOrDescription(
        @Param("lawyerId") String lawyerId,
        @Param("search") String search
    );

    /**
     * Compte le nombre de dossiers par statut pour un avocat
     */
    long countByLawyerIdAndStatus(String lawyerId, CaseStatus status);

    /**
     * Trouve les dossiers accessibles par un avocat (y compris les permissions)
     */
    @Query("SELECT DISTINCT c FROM Case c " +
           "LEFT JOIN Permission p ON p.caseEntity.id = c.id " +
           "WHERE c.lawyer.id = :lawyerId OR " +
           "(p.lawyer.id = :lawyerId AND p.isActive = TRUE AND p.canRead = TRUE)")
    List<Case> findAccessibleCases(@Param("lawyerId") String lawyerId);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.client WHERE c.lawyer.id = :lawyerId ORDER BY c.createdAt DESC")
    List<Case> findTop5ByLawyerIdWithClient(@Param("lawyerId") String lawyerId, Pageable pageable);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.client WHERE c.id = :id")
    Optional<Case> findByIdWithClient(@Param("id") String id);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.client WHERE c.lawyer.id = :lawyerId")
    List<Case> findAllByLawyerIdWithClient(@Param("lawyerId") String lawyerId);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.client WHERE c.lawyer.id = :lawyerId AND c.status = :status")
    List<Case> findByLawyerIdAndStatusWithClient(@Param("lawyerId") String lawyerId, @Param("status") CaseStatus status);

    @Query("SELECT c FROM Case c LEFT JOIN FETCH c.client WHERE c.lawyer.id = :lawyerId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Case> searchByLawyerAndNameOrDescriptionWithClient(@Param("lawyerId") String lawyerId, @Param("search") String search);

    }
