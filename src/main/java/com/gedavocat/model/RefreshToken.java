package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant un Refresh Token
 * 
 * Architecture:
 * - Stockage sécurisé des refresh tokens en base
 * - Révocation possible (logout, changement mot de passe)
 * - Durée de vie longue (7 jours par défaut)
 * - Lié à l'utilisateur et au device (user-agent)
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.3
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token", columnList = "token"),
    @Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
    @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Device fingerprint (user-agent, IP hash, etc.)
     * Permet d'identifier le device et de révoquer des sessions spécifiques
     */
    @Column(name = "device_fingerprint", length = 500)
    private String deviceFingerprint;

    @Column(name = "ip_address", length = 45) // IPv6 max length
    private String ipAddress;

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    /**
     * Vérifie si le token est expiré
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Vérifie si le token est révoqué
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Vérifie si le token est valide (non expiré et non révoqué)
     */
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    /**
     * Révoque le token
     */
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}
