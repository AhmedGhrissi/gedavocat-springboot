package com.gedavocat.repository;

import com.gedavocat.model.DocumentShare;
import com.gedavocat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentShareRepository extends JpaRepository<DocumentShare, String> {

    /**
     * Tous les partages d'un dossier
     */
    @Query("SELECT ds FROM DocumentShare ds WHERE ds.caseEntity.id = :caseId")
    List<DocumentShare> findByCaseId(@Param("caseId") String caseId);

    /**
     * Tous les partages d'un dossier pour un rôle donné
     */
    @Query("SELECT ds FROM DocumentShare ds WHERE ds.caseEntity.id = :caseId AND ds.targetRole = :role")
    List<DocumentShare> findByCaseIdAndRole(@Param("caseId") String caseId,
                                            @Param("role") User.UserRole role);

    /**
     * Les IDs de documents partagés pour un dossier et un rôle
     */
    @Query("SELECT ds.document.id FROM DocumentShare ds WHERE ds.caseEntity.id = :caseId AND ds.targetRole = :role")
    List<String> findDocumentIdsByCaseIdAndRole(@Param("caseId") String caseId,
                                                @Param("role") User.UserRole role);

    /**
     * Cherche un partage pour un document et un rôle
     */
    @Query("SELECT ds FROM DocumentShare ds WHERE ds.document.id = :documentId AND ds.targetRole = :role")
    Optional<DocumentShare> findByDocumentIdAndRole(@Param("documentId") String documentId,
                                                     @Param("role") User.UserRole role);

    /**
     * Vérifie si un document est partagé avec un rôle
     */
    @Query("SELECT CASE WHEN COUNT(ds) > 0 THEN TRUE ELSE FALSE END FROM DocumentShare ds " +
           "WHERE ds.document.id = :documentId AND ds.targetRole = :role")
    boolean isSharedWithRole(@Param("documentId") String documentId,
                             @Param("role") User.UserRole role);

    /**
     * Supprime le partage d'un document pour un rôle
     */
    @Modifying
    @Query("DELETE FROM DocumentShare ds WHERE ds.document.id = :documentId AND ds.targetRole = :role")
    void deleteByDocumentIdAndRole(@Param("documentId") String documentId,
                                   @Param("role") User.UserRole role);

    /**
     * Supprime tous les partages d'un document
     */
    @Modifying
    @Query("DELETE FROM DocumentShare ds WHERE ds.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") String documentId);

    /**
     * Supprime tous les partages d'un dossier
     */
    @Modifying
    @Query("DELETE FROM DocumentShare ds WHERE ds.caseEntity.id = :caseId")
    void deleteByCaseId(@Param("caseId") String caseId);

    /**
     * Partage/départage en masse d'un dossier complet pour un rôle
     */
    @Query("SELECT ds FROM DocumentShare ds WHERE ds.caseEntity.id = :caseId AND ds.targetRole = :role AND ds.document.id IN :documentIds")
    List<DocumentShare> findByCaseIdAndRoleAndDocumentIds(@Param("caseId") String caseId,
                                                          @Param("role") User.UserRole role,
                                                          @Param("documentIds") List<String> documentIds);
}
