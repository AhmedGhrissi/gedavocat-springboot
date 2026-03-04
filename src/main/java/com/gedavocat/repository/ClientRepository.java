package com.gedavocat.repository;

import com.gedavocat.model.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, String> {
    
    /**
     * Trouve tous les clients d'un avocat avec les cases chargés
     */
    @EntityGraph(attributePaths = {"cases", "lawyer"})
    List<Client> findByLawyerId(String lawyerId);
    
    /**
     * Trouve un client par email pour un avocat spécifique
     */
    Optional<Client> findByLawyerIdAndEmail(String lawyerId, String email);
    
    /**
     * Recherche de clients par nom ou email pour un avocat
     */
    @Query("SELECT c FROM Client c WHERE c.lawyer.id = :lawyerId AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Client> searchByLawyerAndNameOrEmail(
        @Param("lawyerId") String lawyerId,
        @Param("search") String search
    );
    
    /**
     * Compte le nombre de clients d'un avocat
     */
    long countByLawyerId(String lawyerId);
    
    /**
     * Vérifie si un client existe pour un avocat
     */
    Optional<Client> findByEmail(String email);
    
    boolean existsByLawyerIdAndEmail(String lawyerId, String email);
    
    /**
     * Trouve un client par son userId (compte utilisateur lié)
     * Utilisé pour le portail client
     */
    Optional<Client> findByClientUserId(String userId);

    long countByCreatedAtAfter(java.time.LocalDateTime date);

    /**
     * Clients sans compte utilisateur lié (client_user_id IS NULL)
     * Avec chargement eager du lawyer pour éviter LazyInitializationException
     */
    @EntityGraph(attributePaths = {"lawyer"})
    List<Client> findByClientUserIsNull();

    /**
     * Trouve un client par son token d'invitation
     */
    Optional<Client> findByInvitationId(String invitationId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE Client c SET c.clientUser = null WHERE c.clientUser.id = :userId")
    void clearClientUserById(@org.springframework.data.repository.query.Param("userId") String userId);

    // ==========================================
    // REQUÊTES MULTI-TENANT (firmId)
    // ==========================================

    /**
     * Compte le nombre de clients dans un cabinet
     */
    long countByFirmId(String firmId);

    /**
     * Trouve tous les clients d'un cabinet
     */
    List<Client> findByFirmId(String firmId);
}
