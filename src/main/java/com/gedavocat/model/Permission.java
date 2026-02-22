package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant les droits d'accès partagés entre avocats
 */
@Entity
@Table(name = "permissions", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "lawyer_id"}),
    indexes = {
        @Index(name = "idx_permission_case_id", columnList = "case_id"),
        @Index(name = "idx_permission_lawyer_id", columnList = "lawyer_id"),
        @Index(name = "idx_permission_granted_by", columnList = "granted_by")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"caseEntity", "grantedBy", "lawyer"})
@EqualsAndHashCode(exclude = {"caseEntity", "grantedBy", "lawyer"})
public class Permission {
    
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by", nullable = false)
    private User grantedBy;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private User lawyer;
    
    @Column(name = "can_read", nullable = false)
    private Boolean canRead = false;
    
    @Column(name = "can_write", nullable = false)
    private Boolean canWrite = false;
    
    @Column(name = "can_upload", nullable = false)
    private Boolean canUpload = false;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    // Méthodes utilitaires
    public boolean isValid() {
        if (!isActive || revokedAt != null) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }
    
    public void revoke() {
        this.isActive = false;
        this.revokedAt = LocalDateTime.now();
    }
    
    public boolean hasReadAccess() {
        return isValid() && canRead;
    }
    
    public boolean hasWriteAccess() {
        return isValid() && canWrite;
    }
    
    public boolean hasUploadAccess() {
        return isValid() && canUpload;
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (canRead == null) canRead = false;
        if (canWrite == null) canWrite = false;
        if (canUpload == null) canUpload = false;
        if (isActive == null) isActive = true;
    }
}
