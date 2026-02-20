package com.gedavocat.repository;

import com.gedavocat.model.User;
import com.gedavocat.model.User.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité User
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    /**
     * Trouve un utilisateur par email
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Vérifie si un email existe déjà
     */
    boolean existsByEmail(String email);
    
    /**
     * Trouve tous les utilisateurs par rôle
     */
    List<User> findByRole(UserRole role);
    
    /**
     * Trouve tous les avocats
     */
    @Query("SELECT u FROM User u WHERE u.role = 'LAWYER' OR u.role = 'LAWYER_SECONDARY'")
    List<User> findAllLawyers();
    
    /**
     * Trouve les utilisateurs avec un abonnement actif
     */
    @Query("SELECT u FROM User u WHERE u.subscriptionStatus = 'ACTIVE' " +
           "AND u.subscriptionEndsAt > :now")
    List<User> findUsersWithActiveSubscription(@Param("now") LocalDateTime now);
    
    /**
     * Trouve les utilisateurs dont l'abonnement expire bientôt
     */
    @Query("SELECT u FROM User u WHERE u.subscriptionStatus = 'ACTIVE' " +
           "AND u.subscriptionEndsAt BETWEEN :now AND :expiryDate")
    List<User> findUsersWithExpiringSubscription(
        @Param("now") LocalDateTime now,
        @Param("expiryDate") LocalDateTime expiryDate
    );
    
    /**
     * Compte le nombre d'utilisateurs par rôle
     */
    long countByRole(UserRole role);
    
    /**
     * Recherche d'utilisateurs par nom ou email
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> searchByNameOrEmail(@Param("search") String search);
    
    /**
     * Recherche d'utilisateurs par prénom, nom ou email (pour l'admin)
     */
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> findByFirstNameContainingOrLastNameContainingOrEmailContaining(@Param("keyword") String keyword);

    long countByCreatedAtAfter(LocalDateTime date);
}
