package com.gedavocat.repository;

import com.gedavocat.model.Signature;
import com.gedavocat.model.Signature.SignatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, String> {
    
    /**
     * Trouve les signatures d'un document
     */
    List<Signature> findByDocumentId(String documentId);
    
    /**
     * Trouve les signatures d'un utilisateur
     */
    List<Signature> findByRequestedById(String userId);

    /**
     * Trouve les signatures d'un utilisateur avec le dossier chargé
     */
    @Query("SELECT s FROM Signature s LEFT JOIN FETCH s.caseEntity LEFT JOIN FETCH s.document WHERE s.requestedBy.id = :userId ORDER BY s.createdAt DESC")
    List<Signature> findByRequestedByIdWithCase(@Param("userId") String userId);
    
    /**
     * Trouve une signature par son ID Yousign
     */
    Optional<Signature> findByYousignSignatureRequestId(String yousignId);
    
    /**
     * Trouve les signatures par statut
     */
    List<Signature> findByStatus(SignatureStatus status);
    
    /**
     * Trouve les signatures d'un dossier
     */
    @Query("SELECT s FROM Signature s WHERE s.document.caseEntity.id = :caseId")
    List<Signature> findByCaseId(@Param("caseId") String caseId);

    /**
     * Supprime toutes les signatures d'un dossier (via document)
     */
    @Modifying
    @Query("DELETE FROM Signature s WHERE s.document.caseEntity.id = :caseId")
    void deleteAllByCaseId(@Param("caseId") String caseId);
    
    /**
     * Compte les signatures en attente pour un utilisateur
     */
    @Query("SELECT COUNT(s) FROM Signature s WHERE s.requestedBy.id = :userId AND s.status = 'PENDING'")
    long countPendingByUserId(@Param("userId") String userId);
    
    /**
     * Trouve les signatures par email du signataire
     */
    List<Signature> findBySignerEmail(String signerEmail);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Signature s WHERE s.requestedBy.id = :userId")
    void deleteByRequestedById(@Param("userId") String userId);
}