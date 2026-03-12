package com.gedavocat.repository;

import com.gedavocat.model.User;
import com.gedavocat.model.User.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    Optional<User> findByResetToken(String resetToken);

    /**
     * Trouve un utilisateur par son identifiant client Stripe
     */
    Optional<User> findByStripeCustomerId(String stripeCustomerId);
    
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
    @Query("SELECT u FROM User u WHERE u.role = 'LAWYER' OR u.role = 'LAWYER_SECONDARY' OR u.role = 'AVOCAT_ADMIN'")
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

    /**
     * Recherche filtrée avec critères combinés (admin)
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:accountEnabled IS NULL OR u.accountEnabled = :accountEnabled)")
    List<User> findWithFilters(@Param("search") String search,
                               @Param("role") UserRole role,
                               @Param("accountEnabled") Boolean accountEnabled);

    // ==========================================
    // REQUÊTES MULTI-TENANT (firmId)
    // ==========================================

    /**
     * Compte le nombre d'avocats dans un cabinet
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.firm.id = :firmId AND u.role IN :roles")
    long countByFirmIdAndRoleIn(@Param("firmId") String firmId, @Param("roles") List<UserRole> roles);

    /**
     * Trouve tous les utilisateurs d'un cabinet
     */
    List<User> findByFirmId(String firmId);

    /**
     * Trouve tous les avocats d'un cabinet
     */
    @Query("SELECT u FROM User u WHERE u.firm.id = :firmId AND (u.role = 'LAWYER' OR u.role = 'LAWYER_SECONDARY' OR u.role = 'AVOCAT_ADMIN')")
    List<User> findLawyersByFirmId(@Param("firmId") String firmId);

    /**
     * SEC RGPD F-11 : Nettoie les tokens de réinitialisation expirés.
     * Utilisé par RgpdPurgeScheduler pour minimiser les données sensibles (Art. 5 RGPD).
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.resetToken = NULL, u.resetTokenExpiry = NULL WHERE u.resetTokenExpiry < :cutoff AND u.resetToken IS NOT NULL")
    int clearExpiredResetTokens(@Param("cutoff") LocalDateTime cutoff);

    /**
     * SEC RGPD N-13 : Désactive les comptes inactifs depuis plus de 24 mois (Art. 5.1.e RGPD).
     * Exclut les comptes ADMIN et les comptes déjà désactivés.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.accountEnabled = false WHERE u.accountEnabled = true AND u.role != 'ADMIN' AND (u.lastLogin < :cutoff OR (u.lastLogin IS NULL AND u.createdAt < :cutoff))")
    int disableInactiveAccounts(@Param("cutoff") LocalDateTime cutoff);
}
