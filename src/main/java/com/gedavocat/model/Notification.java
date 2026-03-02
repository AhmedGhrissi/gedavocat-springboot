package com.gedavocat.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification in-app destinée à un utilisateur.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_user", columnList = "user_id"),
    @Index(name = "idx_notif_read", columnList = "is_read"),
    @Index(name = "idx_notif_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user"})
@EqualsAndHashCode(exclude = {"user"})
public class Notification {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /** Type de notification (SIGNATURE_PENDING, SIGNATURE_SIGNED, DOCUMENT_UPLOADED, etc.) */
    @Column(nullable = false, length = 50)
    private String type;

    /** Titre court affiché dans le dropdown */
    @Column(nullable = false, length = 255)
    private String title;

    /** Message détaillé */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** Lien vers la ressource concernée (ex: /signatures/xxx) */
    @Column(length = 500)
    private String link;

    /** Icône FontAwesome (ex: fa-file-signature) */
    @Column(length = 50)
    private String icon;

    /** Couleur du badge (success, warning, danger, primary) */
    @Column(length = 20)
    private String color;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    // SEC FIX MDL-08 : @Version pour le verrouillage optimiste
    @Version
    @Column(name = "entity_version")
    private Long entityVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}
