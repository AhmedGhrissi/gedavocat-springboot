package com.gedavocat.repository;

import com.gedavocat.model.RpvaCommunication;
import com.gedavocat.model.RpvaCommunication.CommunicationStatus;
import com.gedavocat.model.RpvaCommunication.CommunicationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface RpvaCommunicationRepository extends JpaRepository<RpvaCommunication, String> {
    
    /**
     * Trouve les communications d'un dossier
     */
    List<RpvaCommunication> findByCaseEntityId(String caseId);
    
    /**
     * Trouve les communications d'un utilisateur
     */
    List<RpvaCommunication> findBySentById(String userId);
    
    /**
     * Trouve les communications par statut
     */
    List<RpvaCommunication> findByStatus(CommunicationStatus status);
    
    /**
     * Trouve les communications par type
     */
    List<RpvaCommunication> findByType(CommunicationType type);
    
    /**
     * Trouve une communication par numéro de référence
     */
    Optional<RpvaCommunication> findByReferenceNumber(String referenceNumber);

    /**
     * Supprime toutes les communications d'un dossier (avant suppression du dossier)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RpvaCommunication r WHERE r.caseEntity.id = :caseId")
    void deleteByCaseId(@Param("caseId") String caseId);

    /**
     * Supprime toutes les communications des dossiers d'un client (avant suppression du client)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RpvaCommunication r WHERE r.caseEntity.client.id = :clientId")
    void deleteAllByCaseEntityClientId(@Param("clientId") String clientId);
    
    /**
     * Trouve les communications d'une juridiction
     */
    List<RpvaCommunication> findByJurisdiction(String jurisdiction);
    
    /**
     * Compte les communications en attente pour un utilisateur
     */
    @Query("SELECT COUNT(r) FROM RpvaCommunication r WHERE r.sentBy.id = :userId AND r.status = 'DRAFT'")
    long countDraftsByUserId(@Param("userId") String userId);
    
    /**
     * Trouve les communications récentes d'un utilisateur
     */
    @Query("SELECT r FROM RpvaCommunication r WHERE r.sentBy.id = :userId ORDER BY r.createdAt DESC")
    List<RpvaCommunication> findRecentByUserId(@Param("userId") String userId);
}