package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant le partage d'un document spécifique avec un rôle
 * (collaborateur ou huissier). Chaque ligne accorde la visibilité d'un
 * document à un rôle donné.
 */
@Entity
@Table(name = "document_shares",
    uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "target_role"}),
    indexes = {
        @Index(name = "idx_docshare_document", columnList = "document_id"),
        @Index(name = "idx_docshare_case", columnList = "case_id"),
        @Index(name = "idx_docshare_role", columnList = "target_role")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"document", "caseEntity"})
@EqualsAndHashCode(exclude = {"document", "caseEntity"})
public class DocumentShare {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;

    /**
     * Rôle cible : LAWYER_SECONDARY (collaborateur) ou HUISSIER
     */
    @Column(name = "target_role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private User.UserRole targetRole;

    /**
     * Autoriser le téléchargement (false = lecture nom/métadonnées uniquement)
     */
    @Column(name = "can_download", nullable = false)
    private Boolean canDownload = false;

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
