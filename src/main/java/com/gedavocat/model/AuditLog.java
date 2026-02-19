package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant le journal d'audit pour la traçabilité
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_action", columnList = "action"),
    @Index(name = "idx_entity_type", columnList = "entity_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(nullable = false, length = 100)
    private String action;
    
    @Column(name = "entity_type", length = 50)
    private String entityType;
    
    @Column(name = "entity_id", length = 36)
    private String entityId;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Énumération des actions d'audit
    public enum AuditAction {
        USER_LOGIN("Connexion utilisateur"),
        USER_LOGOUT("Déconnexion utilisateur"),
        USER_CREATED("Création utilisateur"),
        USER_UPDATED("Modification utilisateur"),
        USER_DELETED("Suppression utilisateur"),
        
        CLIENT_CREATED("Création client"),
        CLIENT_UPDATED("Modification client"),
        CLIENT_DELETED("Suppression client"),
        
        CASE_CREATED("Création dossier"),
        CASE_UPDATED("Modification dossier"),
        CASE_DELETED("Suppression dossier"),
        CASE_CLOSED("Fermeture dossier"),
        CASE_ARCHIVED("Archivage dossier"),
        
        DOCUMENT_UPLOADED("Upload document"),
        DOCUMENT_DOWNLOADED("Téléchargement document"),
        DOCUMENT_DELETED("Suppression document"),
        DOCUMENT_RESTORED("Restauration document"),
        DOCUMENT_SIGNED("Signature document"),
        
        PERMISSION_GRANTED("Permission accordée"),
        PERMISSION_REVOKED("Permission révoquée"),
        
        SUBSCRIPTION_CREATED("Création abonnement"),
        SUBSCRIPTION_UPDATED("Modification abonnement"),
        SUBSCRIPTION_CANCELLED("Annulation abonnement"),
        
        SETTINGS_UPDATED("Modification paramètres"),
        PASSWORD_CHANGED("Changement mot de passe");
        
        private final String displayName;
        
        AuditAction(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}
