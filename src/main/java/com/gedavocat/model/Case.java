package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité représentant un dossier juridique (affaire)
 * MULTI-TENANT: Isolation automatique par firmId
 */
@Entity
@Table(name = "cases", indexes = {
    @Index(name = "idx_case_lawyer_id", columnList = "lawyer_id"),
    @Index(name = "idx_case_client_id", columnList = "client_id"),
    @Index(name = "idx_case_status", columnList = "status"),
    @Index(name = "idx_case_firm_id", columnList = "firm_id")
})
@Filter(name = "firmFilter", condition = "firm_id = :firmId")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"lawyer", "client", "documents", "permissions"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Case {
    
    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id;

    @Version
    @Column(name = "entity_version")
    private Long entityVersion;
    
    // MULTI-TENANT: Lien vers le cabinet
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id")
    private Firm firm;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    @JsonIgnore
    private User lawyer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;
    
    @NotBlank(message = "Le nom du dossier est obligatoire")
    @Column(name = "title", length = 255)
    private String title;
    
    // Keep 'name' column synchronized with 'title' for database compatibility
    // Getter/Setter provided manually to ensure synchronization
    @lombok.Getter(AccessLevel.NONE)
    @lombok.Setter(AccessLevel.NONE)
    @Column(name = "name", length = 255)
    private String name;
    
    @Column(length = 50)
    private String reference;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", length = 50)
    private CaseType caseType;

    // Legacy sync: 'type' column (NOT NULL in BDD.sql)
    @lombok.Getter(AccessLevel.NONE)
    @lombok.Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private CaseType legacyType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status = CaseStatus.OPEN;
    
    @Column(name = "opened_date")
    private LocalDateTime openedDate;
    
    @Column(name = "closed_date")
    private LocalDateTime closedDate;

    // Legacy sync: opened_at/closed_at columns (BDD.sql compatibility)
    @lombok.Getter(AccessLevel.NONE)
    @lombok.Setter(AccessLevel.NONE)
    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @lombok.Getter(AccessLevel.NONE)
    @lombok.Setter(AccessLevel.NONE)
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    @Column(name = "deadline")
    private LocalDateTime deadline;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relations
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Document> documents = new HashSet<>();
    
    @OneToMany(mappedBy = "caseEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Permission> permissions = new HashSet<>();
    
    // Énumération du statut
    public enum CaseStatus {
        OPEN("Ouvert"),
        IN_PROGRESS("En cours"),
        CLOSED("Fermé"),
        ARCHIVED("Archivé");
        
        private final String displayName;
        
        CaseStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Énumération des types de dossiers juridiques
    public enum CaseType {
        CIVIL("Droit civil"),
        PENAL("Droit pénal"),
        COMMERCIAL("Droit commercial"),
        TRAVAIL("Droit du travail"),
        FAMILLE("Droit de la famille"),
        IMMOBILIER("Droit immobilier"),
        ADMINISTRATIF("Droit administratif"),
        FISCAL("Droit fiscal"),
        SOCIAL("Droit social"),
        AUTRE("Autre");
        
        private final String displayName;
        
        CaseType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Méthodes utilitaires
    
    /**
     * Getter pour le nom du dossier (utilise 'title' comme source principale)
     * Pour compatibilité avec le code existant qui pourrait utiliser getName()
     */
    public String getName() {
        return title != null ? title : name;
    }
    
    /**
     * Setter pour le nom du dossier (synchronise title et name)
     */
    public void setName(String name) {
        this.title = name;
        this.name = name;
    }
    
    public boolean isOpen() {
        return status == CaseStatus.OPEN;
    }
    
    public boolean isInProgress() {
        return status == CaseStatus.IN_PROGRESS;
    }
    
    public boolean isClosed() {
        return status == CaseStatus.CLOSED;
    }
    
    public boolean isArchived() {
        return status == CaseStatus.ARCHIVED;
    }
    
    /**
     * Retourne le nombre de documents associés à ce dossier
     * @return Le nombre de documents
     */
    public Integer getDocumentCount() {
        return documents != null ? documents.size() : 0;
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = CaseStatus.OPEN;
        }
        // Ensure caseType is not null to match DB constraint
        if (caseType == null) {
            caseType = CaseType.AUTRE;
        }
        // Synchronize 'name' column with 'title' for database compatibility
        if (title != null && name == null) {
            this.name = this.title;
        } else if (name != null && title == null) {
            this.title = this.name;
        } else if (title != null) {
            this.name = this.title;
        }
        syncLegacyColumns();
    }

    @PreUpdate
    public void preUpdate() {
        // Ensure caseType is not null on update/merge as well
        if (caseType == null) {
            caseType = CaseType.AUTRE;
        }
        // Synchronize 'name' column with 'title' for database compatibility
        if (title != null) {
            this.name = this.title;
        } else if (name != null) {
            this.title = this.name;
        }
        syncLegacyColumns();
    }

    /**
     * Sync legacy columns: type ↔ case_type, opened_at ↔ opened_date, closed_at ↔ closed_date
     */
    private void syncLegacyColumns() {
        // type ↔ case_type
        if (caseType != null) {
            this.legacyType = this.caseType;
        } else if (this.legacyType != null) {
            this.caseType = this.legacyType;
        } else {
            this.caseType = CaseType.AUTRE;
            this.legacyType = CaseType.AUTRE;
        }
        // opened_at ↔ opened_date
        if (openedDate != null) {
            this.openedAt = this.openedDate;
        } else if (this.openedAt != null) {
            this.openedDate = this.openedAt;
        }
        // closed_at ↔ closed_date
        if (closedDate != null) {
            this.closedAt = this.closedDate;
        } else if (this.closedAt != null) {
            this.closedDate = this.closedAt;
        }
    }
}