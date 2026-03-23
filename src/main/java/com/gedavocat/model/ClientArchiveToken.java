package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Archive légale générée lors de la suppression d'un client.
 * Contient le ZIP du dossier (métadonnées + documents) stocké dans MinIO.
 * Le lien de téléchargement (token) est valable pendant la durée légale de conservation.
 */
@Entity
@Table(name = "client_archive_tokens", indexes = {
    @Index(name = "idx_archive_token",     columnList = "token"),
    @Index(name = "idx_archive_lawyer_id", columnList = "lawyer_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClientArchiveToken {

    @Id
    @Column(length = 36)
    private String id;

    /** UUID aléatoire — clé de l'URL de téléchargement. */
    @Column(length = 64, nullable = false, unique = true)
    private String token;

    // ── Snapshots client (pour référence post-suppression) ──────────────────

    @Column(name = "client_id", length = 36, nullable = false)
    private String clientId;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "client_email", length = 255)
    private String clientEmail;

    // ── Avocat responsable ────────────────────────────────────────────────────

    @Column(name = "lawyer_id", length = 36, nullable = false)
    private String lawyerId;

    @Column(name = "lawyer_email", length = 255)
    private String lawyerEmail;

    // ── Stockage ──────────────────────────────────────────────────────────────

    /** Clé objet MinIO (ex : "client-archives/archive_<clientId>_<uuid>.zip"). */
    @Column(name = "storage_key", length = 500, nullable = false)
    private String storageKey;

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Date d'expiration — 5 ans par défaut (délai de conservation avocat, art. 11 RIN). */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;
}
