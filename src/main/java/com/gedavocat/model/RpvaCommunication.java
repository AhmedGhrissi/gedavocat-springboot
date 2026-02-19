package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant une communication RPVA (e-Barreau)
 */
@Entity
@Table(name = "rpva_communications", indexes = {
    @Index(name = "idx_rpva_case", columnList = "case_id"),
    @Index(name = "idx_rpva_status", columnList = "status"),
    @Index(name = "idx_rpva_reference", columnList = "reference_number")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpvaCommunication {
    
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationType type;
    
    @NotBlank(message = "La juridiction est obligatoire")
    @Column(nullable = false, length = 255)
    private String jurisdiction;
    
    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationStatus status = CommunicationStatus.DRAFT;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by", nullable = false)
    private User sentBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    // Énumération des types de communication RPVA
    public enum CommunicationType {
        ASSIGNATION("Assignation"),
        CONCLUSIONS("Conclusions"),
        MEMOIRE("Mémoire"),
        PIECE("Pièce"),
        NOTIFICATION("Notification");
        
        private final String displayName;
        
        CommunicationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Énumération des statuts de communication
    public enum CommunicationStatus {
        DRAFT("Brouillon"),
        SENT("Envoyé"),
        DELIVERED("Délivré"),
        READ("Lu"),
        FAILED("Échec");
        
        private final String displayName;
        
        CommunicationStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Méthodes utilitaires
    public boolean isDraft() {
        return status == CommunicationStatus.DRAFT;
    }
    
    public boolean isSent() {
        return status == CommunicationStatus.SENT;
    }
    
    public boolean isDelivered() {
        return status == CommunicationStatus.DELIVERED;
    }
    
    public boolean isRead() {
        return status == CommunicationStatus.READ;
    }
    
    public boolean hasFailed() {
        return status == CommunicationStatus.FAILED;
    }
    
    public void markAsSent() {
        this.status = CommunicationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }
    
    public void markAsDelivered() {
        this.status = CommunicationStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }
    
    public void markAsRead() {
        this.status = CommunicationStatus.READ;
    }
    
    public void markAsFailed() {
        this.status = CommunicationStatus.FAILED;
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}