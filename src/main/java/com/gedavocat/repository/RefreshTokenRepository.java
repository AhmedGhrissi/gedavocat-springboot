package com.gedavocat.repository;

import com.gedavocat.model.RefreshToken;
import com.gedavocat.model.User;
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
 * Repository pour l'entité RefreshToken
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Trouver un refresh token par sa valeur
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Trouver tous les refresh tokens d'un utilisateur
     */
    List<RefreshToken> findByUser(User user);

    /**
     * Trouver tous les refresh tokens valides d'un utilisateur
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user " +
           "AND rt.expiresAt > :now AND rt.revokedAt IS NULL")
    List<RefreshToken> findValidTokensByUser(
        @Param("user") User user, 
        @Param("now") LocalDateTime now
    );

    /**
     * Révoquer tous les tokens d'un utilisateur
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.user = :user AND rt.revokedAt IS NULL")
    void revokeAllUserTokens(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Supprimer les tokens expirés (cleanup)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Supprimer les tokens révoqués depuis plus de X jours
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.revokedAt IS NOT NULL AND rt.revokedAt < :date")
    void deleteRevokedTokensOlderThan(@Param("date") LocalDateTime date);
}
