package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant un document stocké dans le système
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_document_case_id", columnList = "case_id"),
    @Index(name = "idx_document_uploaded_by", columnList = "uploaded_by"),
    @Index(name = "idx_document_deleted_at", columnList = "deleted_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"caseEntity", "uploadedBy", "parentDocument"})
@EqualsAndHashCode(exclude = {"caseEntity", "uploadedBy", "parentDocument"})
public class Document {
    
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case caseEntity;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;
    
    @NotBlank
    @Column(name = "uploader_role", nullable = false, length = 20)
    private String uploaderRole;
    
    @NotBlank(message = "Le nom du fichier est obligatoire")
    @Column(nullable = false, length = 255)
    private String filename;
    
    @NotBlank(message = "Le nom original est obligatoire")
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    
    @Column(length = 100)
    private String mimetype;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @NotBlank(message = "Le chemin du fichier est obligatoire")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;
    
    @Column(nullable = false)
    private Integer version = 1;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id")
    private Document parentDocument;
    
    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = true;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Méthodes utilitaires
    public boolean isDeleted() {
        return deletedAt != null;
    }
    
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
    
    public void restore() {
        this.deletedAt = null;
    }
    
    public String getFormattedFileSize() {
        if (fileSize == null) return "0 B";
        
        long size = fileSize;
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return size + " " + units[unitIndex];
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (version == null) {
            version = 1;
        }
        if (isLatest == null) {
            isLatest = true;
        }
    }
}
