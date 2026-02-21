package com.gedavocat.repository;

import com.gedavocat.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    
    /**
     * Trouve tous les logs d'un utilisateur
     */
    Page<AuditLog> findByUserId(String userId, Pageable pageable);
    
    /**
     * Trouve les logs par action
     */
    List<AuditLog> findByAction(String action);
    
    /**
     * Trouve les logs par type d'entité
     */
    List<AuditLog> findByEntityType(String entityType);
    
    /**
     * Trouve les logs d'une entité spécifique
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Trouve les logs dans une période donnée
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Trouve les logs récents (paginés)
     */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Recherche dans les logs
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.details) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> searchAuditLogs(@Param("search") String search, Pageable pageable);

    void deleteByCreatedAtBefore(LocalDateTime date);

    long countByCreatedAtBefore(LocalDateTime date);

    long countByActionAndCreatedAtAfter(String action, LocalDateTime date);
}
