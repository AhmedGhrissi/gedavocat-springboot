package com.gedavocat.repository;

import com.gedavocat.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, String> {
    
    /**
     * Trouve tous les clients d'un avocat
     */
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
}
