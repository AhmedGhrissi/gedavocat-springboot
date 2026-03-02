package com.gedavocat.repository;

import com.gedavocat.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {
    
    /**
     * Trouve tous les documents d'un dossier (non supprimés)
     */
    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.caseEntity LEFT JOIN FETCH d.uploadedBy WHERE d.caseEntity.id = :caseId AND d.deletedAt IS NULL")
    List<Document> findByCaseIdAndNotDeleted(@Param("caseId") String caseId);
    
    /**
     * Trouve tous les documents supprimés d'un dossier
     */
    @Query("SELECT d FROM Document d WHERE d.caseEntity.id = :caseId AND d.deletedAt IS NOT NULL")
    List<Document> findDeletedByCaseId(@Param("caseId") String caseId);
    
    /**
     * Trouve les dernières versions des documents
     */
    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.caseEntity LEFT JOIN FETCH d.uploadedBy WHERE d.caseEntity.id = :caseId " +
           "AND d.isLatest = TRUE AND d.deletedAt IS NULL")
    List<Document> findLatestVersionsByCaseId(@Param("caseId") String caseId);
    
    /**
     * Trouve les versions d'un document parent
     */
    List<Document> findByParentDocumentIdOrderByVersionDesc(String parentDocumentId);
    
    /**
     * Compte le nombre de documents d'un dossier
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.caseEntity.id = :caseId AND d.deletedAt IS NULL")
    long countByCaseIdAndNotDeleted(@Param("caseId") String caseId);
    
    /**
     * Calcule la taille totale des documents d'un utilisateur
     */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d " +
           "WHERE d.caseEntity.lawyer.id = :lawyerId AND d.deletedAt IS NULL")
    long calculateTotalSizeByLawyer(@Param("lawyerId") String lawyerId);
    
    /**
     * Trouve tous les documents d'un avocat
     */
    @Query("SELECT d FROM Document d WHERE d.caseEntity.lawyer.id = :lawyerId " +
           "AND d.deletedAt IS NULL ORDER BY d.createdAt DESC")
    List<Document> findByLawyerId(@Param("lawyerId") String lawyerId);
    
    /**
     * Trouve tous les documents d'un avocat avec le Case chargé (pour éviter LazyInitializationException)
     */
    @Query("SELECT d FROM Document d " +
           "LEFT JOIN FETCH d.caseEntity c " +
           "WHERE c.lawyer.id = :lawyerId " +
           "AND d.deletedAt IS NULL " +
           "ORDER BY d.createdAt DESC")
    List<Document> findByLawyerIdWithCase(@Param("lawyerId") String lawyerId);

    long countByCreatedAtAfter(java.time.LocalDateTime date);

    /**
     * Compte uniquement les documents NON supprimés (soft delete)
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.deletedAt IS NULL")
    long countNonDeleted();

    /**
     * SEC-TENANT FIX : Compte les documents NON supprimés d'un avocat (via case.lawyer)
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.deletedAt IS NULL AND d.caseEntity.lawyer.id = :lawyerId")
    long countNonDeletedByLawyerId(@Param("lawyerId") String lawyerId);

    /**
     * Compte les documents créés après une date donnée, non supprimés
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.deletedAt IS NULL AND d.createdAt > :date")
    long countNonDeletedCreatedAfter(@Param("date") java.time.LocalDateTime date);

    /**
     * Calcule la taille totale de stockage des documents non supprimés
     */
    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM Document d WHERE d.deletedAt IS NULL")
    long sumFileSizeNonDeleted();

    /**
     * Trouve un document par ID avec le Case et le Client chargés (évite LazyInitializationException)
     */
    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.caseEntity c LEFT JOIN FETCH c.client LEFT JOIN FETCH d.uploadedBy WHERE d.id = :id")
    java.util.Optional<Document> findByIdWithCaseAndClient(@Param("id") String id);

    /**
     * Efface les références parent_document_id pour les documents d'un dossier
     */
    @Modifying
    @Query("UPDATE Document d SET d.parentDocument = NULL WHERE d.caseEntity.id = :caseId")
    void clearParentReferencesByCaseId(@Param("caseId") String caseId);

    /**
     * Supprime tous les documents d'un dossier
     */
    @Modifying
    @Query("DELETE FROM Document d WHERE d.caseEntity.id = :caseId")
    void deleteAllByCaseEntityId(@Param("caseId") String caseId);
}