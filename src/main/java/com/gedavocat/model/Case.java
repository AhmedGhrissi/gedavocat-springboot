package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JoinColumn(name = "firm_id", nullable = false)
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
    @Column(name = "title", nullable = false, length = 255)
    private String name;

    // Legacy column present in some schemas — keep synchronized with `name`
    @Column(name = "name", nullable = false, length = 255)
    private String legacyName;
    
    @Column(length = 50, nullable = false)
    private String reference;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private CaseType caseType;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status = CaseStatus.OPEN;
    
    @Column(name = "opened_at")
    private LocalDateTime openedDate;
    
    @Column(name = "closed_at")
    private LocalDateTime closedDate;
    
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
        // Synchronize legacy column
        this.legacyName = this.name;
    }

    @PreUpdate
    public void preUpdate() {
        this.legacyName = this.name;
    }
}
