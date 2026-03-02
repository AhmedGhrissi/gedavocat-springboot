package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité représentant un client du cabinet d'avocats
 * MULTI-TENANT: Isolation automatique par firmId
 */
@Entity
@Table(name = "clients", indexes = {
    @Index(name = "idx_client_lawyer_id", columnList = "lawyer_id"),
    @Index(name = "idx_client_email", columnList = "email"),
    @Index(name = "idx_client_firm_id", columnList = "firm_id")
})
@FilterDef(name = "firmFilter", parameters = @ParamDef(name = "firmId", type = String.class))
@Filter(name = "firmFilter", condition = "firm_id = :firmId")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"lawyer", "clientUser", "cases", "invoices"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Client {
    
    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id;
    
    // MULTI-TENANT: Lien vers le cabinet
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    private Firm firm;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private User lawyer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id")
    private User clientUser;
    
    @NotBlank(message = "Le nom est obligatoire")
    @Column(nullable = false, length = 100)
    private String name;
    
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    @Column(nullable = false, length = 255)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "access_ends_at")
    private LocalDateTime accessEndsAt;
    
    @Column(name = "invitation_id", length = 36)
    private String invitationId;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    /**
     * Type de client : particulier ou professionnel
     */
    public enum ClientType {
        INDIVIDUAL("Particulier"),
        PROFESSIONAL("Professionnel");
        
        private final String displayName;
        ClientType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", length = 20)
    private ClientType clientType = ClientType.INDIVIDUAL;

    /** Raison sociale (pour les professionnels) */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** SIRET (pour les professionnels) */
    @Column(name = "siret", length = 20)
    private String siret;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relations
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Case> cases = new HashSet<>();
    
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Invoice> invoices = new HashSet<>();
    
    // Méthodes utilitaires
    public boolean hasActiveAccess() {
        return accessEndsAt == null || accessEndsAt.isAfter(LocalDateTime.now());
    }

    /**
     * Retourne le statut de l'invitation : VALIDATED, PENDING, EXPIRED ou NOT_INVITED.
     */
    public String getInvitationStatus() {
        if (clientUser != null) return "VALIDATED";
        if (invitationId == null) return "NOT_INVITED";
        if (invitedAt != null && LocalDateTime.now().isBefore(invitedAt.plusHours(72))) return "PENDING";
        return "EXPIRED";
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}
