package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lien de partage temporaire d'un dossier entre avocats.
 * Permet à l'avocat propriétaire de partager l'accès en lecture
 * à un dossier via un lien unique avec expiration configurable.
 */
@Entity
@Table(name = "case_share_links", indexes = {
    @Index(name = "idx_csl_case", columnList = "case_id"),
    @Index(name = "idx_csl_token", columnList = "token", unique = true),
    @Index(name = "idx_csl_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class CaseShareLink {

    @Id
    @Column(length = 36)
    private String id;

    /** Dossier partagé */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case sharedCase;

    /** Avocat qui a créé le lien */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Token unique inclus dans l'URL */
    @Column(name = "token", nullable = false, unique = true, length = 72)
    private String token;

    /** Email du destinataire de l'invitation */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    /** Date d'envoi de l'invitation */
    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    /**
     * Description ou note explicative pour le destinataire
     * (ex: "Cabinet Martin pour expertise")
     */
    @Column(name = "description", length = 500)
    private String description;

    /** Email du destinataire (optionnel) */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    /** Date/heure où l'invitation a été envoyée (optionnel) */
    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    /** Expiration du lien (peut être null = permanent) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Nombre maximum d'accès (null = illimité) */
    @Column(name = "max_access_count")
    private Integer maxAccessCount;

    /** Compteur d'accès actuel */
    @Column(name = "access_count")
    private int accessCount = 0;

    /** Lien révoqué manuellement */
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (token == null || token.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
    }

    // Explicit getters to avoid depending on Lombok during maven compilation
    public String getRecipientEmail() {
        return this.recipientEmail;
    }

    public LocalDateTime getInvitedAt() {
        return this.invitedAt;
    }

    /** Vérifie si le lien est encore valide */
    public boolean isValid() {
        if (revoked) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        if (maxAccessCount != null && accessCount >= maxAccessCount) return false;
        return true;
    }
}