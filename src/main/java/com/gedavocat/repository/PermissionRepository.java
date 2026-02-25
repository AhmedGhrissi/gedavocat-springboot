package com.gedavocat.repository;

import com.gedavocat.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    
    /**
     * Trouve toutes les permissions d'un dossier
     */
    List<Permission> findByCaseEntityId(String caseId);
    
    /**
     * Trouve les permissions actives d'un dossier
     */
    @Query("SELECT p FROM Permission p WHERE p.caseEntity.id = :caseId " +
           "AND p.isActive = TRUE AND p.revokedAt IS NULL")
    List<Permission> findActiveByCaseId(@Param("caseId") String caseId);
    
    /**
     * Trouve une permission spécifique
     */
    Optional<Permission> findByCaseEntityIdAndLawyerId(String caseId, String lawyerId);
    
    /**
     * Trouve toutes les permissions d'un avocat
     */
    List<Permission> findByLawyerId(String lawyerId);
    
    /**
     * Trouve les permissions actives d'un avocat
     */
    @Query("SELECT p FROM Permission p WHERE p.lawyer.id = :lawyerId " +
           "AND p.isActive = TRUE AND p.revokedAt IS NULL " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    List<Permission> findActiveByLawyerId(@Param("lawyerId") String lawyerId);
    
    /**
     * Vérifie si un avocat a accès en lecture à un dossier
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM Permission p " +
           "WHERE p.caseEntity.id = :caseId AND p.lawyer.id = :lawyerId " +
           "AND p.isActive = TRUE AND p.canRead = TRUE AND p.revokedAt IS NULL " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    boolean hasReadAccess(@Param("caseId") String caseId, @Param("lawyerId") String lawyerId);
    
    /**
     * Vérifie si un avocat a accès en écriture à un dossier
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM Permission p " +
           "WHERE p.caseEntity.id = :caseId AND p.lawyer.id = :lawyerId " +
           "AND p.isActive = TRUE AND p.canWrite = TRUE AND p.revokedAt IS NULL " +
           "AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP)")
    boolean hasWriteAccess(@Param("caseId") String caseId, @Param("lawyerId") String lawyerId);
    
    /**
     * Récupère les permissions actives pour une liste d'IDs de dossiers
     */
    @Query("SELECT p FROM Permission p WHERE p.caseEntity.id IN :caseIds " +
           "AND p.isActive = TRUE AND p.revokedAt IS NULL")
    java.util.List<Permission> findActiveByCaseEntityIdIn(@Param("caseIds") java.util.List<String> caseIds);

    /**
     * Trouve les permissions actives d'un dossier avec les avocats associés
     */
    @Query("SELECT p FROM Permission p LEFT JOIN FETCH p.lawyer l WHERE p.caseEntity.id = :caseId " +
           "AND p.isActive = TRUE AND p.revokedAt IS NULL")
    java.util.List<Permission> findActiveWithLawyerByCaseId(@Param("caseId") String caseId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Permission p WHERE p.lawyer.id = :userId")
    void deleteByLawyerId(@Param("userId") String userId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Permission p WHERE p.grantedBy.id = :userId")
    void deleteByGrantedById(@Param("userId") String userId);
}