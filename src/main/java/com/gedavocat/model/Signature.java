package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant une demande de signature électronique via Yousign
 */
@Entity
@Table(name = "signatures", indexes = {
    @Index(name = "idx_signature_document", columnList = "document_id"),
    @Index(name = "idx_signature_status", columnList = "status"),
    @Index(name = "idx_signature_yousign", columnList = "yousign_signature_request_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Signature {
    
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    
    @Column(name = "yousign_signature_request_id", length = 255, unique = true)
    private String yousignSignatureRequestId;
    
    @NotBlank(message = "Le nom du document est obligatoire")
    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatureStatus status = SignatureStatus.DRAFT;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;
    
    @NotBlank(message = "Le nom du signataire est obligatoire")
    @Column(name = "signer_name", nullable = false, length = 255)
    private String signerName;
    
    @NotBlank(message = "L'email du signataire est obligatoire")
    @Email(message = "Email invalide")
    @Column(name = "signer_email", nullable = false, length = 255)
    private String signerEmail;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "signed_at")
    private LocalDateTime signedAt;
    
    // Énumération des statuts de signature
    public enum SignatureStatus {
        DRAFT("Brouillon"),
        PENDING("En attente"),
        SIGNED("Signé"),
        REJECTED("Rejeté"),
        EXPIRED("Expiré");
        
        private final String displayName;
        
        SignatureStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Méthodes utilitaires
    public boolean isPending() {
        return status == SignatureStatus.PENDING;
    }
    
    public boolean isSigned() {
        return status == SignatureStatus.SIGNED;
    }
    
    public boolean isExpired() {
        return status == SignatureStatus.EXPIRED;
    }
    
    public boolean isRejected() {
        return status == SignatureStatus.REJECTED;
    }
    
    public void markAsSigned() {
        this.status = SignatureStatus.SIGNED;
        this.signedAt = LocalDateTime.now();
    }
    
    public void markAsRejected() {
        this.status = SignatureStatus.REJECTED;
    }
    
    public void markAsExpired() {
        this.status = SignatureStatus.EXPIRED;
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}