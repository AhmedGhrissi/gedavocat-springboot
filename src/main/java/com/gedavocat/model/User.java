package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité User - Utilisateur du système
 * Version complète avec tous les champs abonnement
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_role", columnList = "role"),
    @Index(name = "idx_user_firm_id", columnList = "firm_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Version
    @Column(name = "entity_version")
    private Long entityVersion;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    @Column(nullable = false, length = 100)
    private String name;

    // Champs obligatoires pour le profil
    @NotBlank(message = "Le prénom est obligatoire")
    @Size(min = 2, max = 100, message = "Le prénom doit contenir entre 2 et 100 caractères")
    @Getter(AccessLevel.NONE)  // Désactiver le getter Lombok - on fournit notre propre getter safe
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    @Getter(AccessLevel.NONE)  // Désactiver le getter Lombok - on fournit notre propre getter safe
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    // SEC FIX L-06 : validation format téléphone
    @Pattern(regexp = "^(\\+?[0-9\\s\\-\\.()]{0,20})?$", message = "Format de téléphone invalide")
    @Column(name = "phone", length = 20)
    private String phone;

    // SEC FIX L-06 : validation numéro de barreau
    @Pattern(regexp = "^[A-Za-z0-9\\-\\s]{0,50}$", message = "Numéro de barreau invalide")
    @Column(name = "bar_number", length = 50)
    private String barNumber;

    @Column(name = "email_signature", columnDefinition = "TEXT")
    private String emailSignature;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Column(nullable = false, length = 255)
    @JsonIgnore
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // ==========================================
    // MULTI-TENANT : LIEN VERS LE CABINET
    // ==========================================

    /**
     * Identifiant du cabinet auquel appartient cet utilisateur
     * NULL pour les ADMIN (super-administrateurs système)
     * OBLIGATOIRE pour LAWYER, LAWYER_SECONDARY, CLIENT
     * 
     * Isolation des données : chaque utilisateur ne voit que les données de son cabinet
     * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 VULN-01
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id")
    private Firm firm;

    // ==========================================
    // ABONNEMENT (Tous les champs)
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", length = 20)
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "max_clients")
    private Integer maxClients = 10;

    // ✅ NOUVEAU : Date de début d'abonnement
    @Column(name = "subscription_start_date")
    private LocalDateTime subscriptionStartDate;

    // ✅ Date de fin (déjà existant - renommé pour cohérence)
    @Column(name = "subscription_ends_at")
    private LocalDateTime subscriptionEndsAt;

    // ✅ Identifiant client Stripe (pour relier les webhooks au bon utilisateur)
    @Column(name = "stripe_customer_id", length = 100)
    @JsonIgnore
    private String stripeCustomerId;

    // ✅ ALIAS pour compatibilité avec templates
    @Transient
    public LocalDateTime getSubscriptionEndDate() {
        return subscriptionEndsAt;
    }

    @Transient
    public void setSubscriptionEndDate(LocalDateTime date) {
        this.subscriptionEndsAt = date;
    }

    // ==========================================
    // RGPD ET CONSENTEMENTS
    // ==========================================

    @Column(name = "gdpr_consent_at")
    private LocalDateTime gdprConsentAt;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    // ==========================================
    // TIMESTAMPS
    // ==========================================

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==========================================
    // AUTRES CHAMPS
    // ==========================================

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false; // SEC FIX MDL-02 : false par défaut — doit être vérifié par email

    @Column(name = "account_enabled", nullable = false)
    private boolean accountEnabled = true;

    @Column(name = "access_ends_at")
    private LocalDateTime accessEndsAt;

    @Column(name = "invitation_id", length = 36)
    @JsonIgnore
    private String invitationId;

    // Réinitialisation du mot de passe (persisté en base, résiste aux redémarrages)
    @Column(name = "reset_token", length = 36)
    @JsonIgnore
    private String resetToken;

    @Column(name = "reset_token_expiry")
    @JsonIgnore
    private LocalDateTime resetTokenExpiry;

    // ==========================================
    // RELATIONS
    // ==========================================

    @OneToMany(mappedBy = "lawyer", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Client> clients = new HashSet<>();

    @OneToMany(mappedBy = "lawyer", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<Case> cases = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JsonIgnore
    private Set<AuditLog> auditLogs = new HashSet<>();

    // ==========================================
    // ÉNUMÉRATIONS
    // ==========================================

    public enum UserRole {
        ADMIN("Administrateur"),
        LAWYER("Avocat"),
        CLIENT("Client"),
        LAWYER_SECONDARY("Collaborateur"),
        HUISSIER("Huissier");

        private final String displayName;

        UserRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SubscriptionPlan {
        SOLO("Solo", 29.99, 10),
        CABINET("Cabinet", 99.99, 75),
        ENTERPRISE("Enterprise", 299.99, Integer.MAX_VALUE),
        // Legacy values for backward compatibility
        ESSENTIEL("Essentiel", 49.0, 10),
        PROFESSIONNEL("Professionnel", 99.0, 75),
        CABINET_PLUS("Cabinet+", 199.0, Integer.MAX_VALUE);

        private final String displayName;
        private final double price;
        private final int maxClients;

        SubscriptionPlan(String displayName, double price, int maxClients) {
            this.displayName = displayName;
            this.price = price;
            this.maxClients = maxClients;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getPrice() {
            return price;
        }

        public int getMaxClients() {
            return maxClients;
        }
    }

    public enum SubscriptionStatus {
        ACTIVE("Actif"),
        INACTIVE("Inactif"),
        CANCELLED("Annulé"),
        TRIAL("Essai"),
        PAYMENT_FAILED("Paiement échoué");

        private final String displayName;

        SubscriptionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    /**
     * Vérifier si l'utilisateur est admin
     */
    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    /**
     * Vérifier si l'utilisateur est avocat
     */
    public boolean isLawyer() {
        return this.role == UserRole.LAWYER;
    }

    /**
     * Vérifier si l'utilisateur est client
     */
    public boolean isClient() {
        return this.role == UserRole.CLIENT;
    }

    /**
     * Vérifier si l'utilisateur est avocat secondaire / collaborateur
     */
    public boolean isCollaborator() {
        return this.role == UserRole.LAWYER_SECONDARY;
    }

    /**
     * Vérifier si l'utilisateur est huissier
     */
    public boolean isHuissier() {
        return this.role == UserRole.HUISSIER;
    }

    /**
     * Vérifier si l'abonnement est actif
     */
    public boolean hasActiveSubscription() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE
            && subscriptionEndsAt != null
            && subscriptionEndsAt.isAfter(LocalDateTime.now());
    }

    /**
     * Vérifier si l'abonnement a expiré
     */
    public boolean isSubscriptionExpired() {
        return subscriptionEndsAt != null
            && subscriptionEndsAt.isBefore(LocalDateTime.now());
    }

    /**
     * Obtenir le nombre de jours restants
     */
    public long getDaysRemaining() {
        if (subscriptionEndsAt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(
            LocalDateTime.now(),
            subscriptionEndsAt
        );
    }

    /**
     * Vérifier si l'utilisateur peut ajouter des clients
     */
    public boolean canAddMoreClients() {
        if (maxClients == null) return false;
        return clients.size() < maxClients;
    }

    /**
     * Obtenir le nombre de clients restants autorisés
     */
    public int getRemainingClientsSlots() {
        if (maxClients == null) return 0;
        return Math.max(0, maxClients - clients.size());
    }

    // ===== CHAMPS MFA (MULTI-FACTOR AUTHENTICATION) =====
    
    @Column(name = "mfa_secret", length = 32)
    private String mfaSecret;
    
    @Column(name = "mfa_enabled")
    private Boolean mfaEnabled = Boolean.FALSE;
    
    @Column(name = "mfa_backup_codes", length = 1000)
    private String mfaBackupCodes;
    
    @Column(name = "mfa_temp_setup")
    private LocalDateTime mfaTempSetup;
    
    @Column(name = "mfa_last_used")
    private LocalDateTime mfaLastUsed;

    // Lombok generates a Boolean getter named getMfaEnabled(), but much of the
    // codebase expects a conventional isMfaEnabled() returning primitive boolean.
    // Provide a safe helper that returns false for null values coming from DB.
    public boolean isMfaEnabled() {
        return Boolean.TRUE.equals(this.mfaEnabled);
    }

    /**
     * Safe getter for firstName that never returns null
     */
    public String getFirstName() {
        return firstName != null ? firstName : "";
    }
    
    /**
     * Safe getter for lastName that never returns null
     */
    public String getLastName() {
        return lastName != null ? lastName : "";
    }
    
    /**
     * Returns full name (firstName + lastName) with safe null handling.
     * Falls back to 'name' field or email if firstName/lastName are null.
     */
    @Transient
    public String getFullName() {
        if (firstName != null && lastName != null && !firstName.isEmpty() && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        } else if (firstName != null && !firstName.isEmpty()) {
            return firstName;
        } else if (lastName != null && !lastName.isEmpty()) {
            return lastName;
        } else if (name != null && !name.isEmpty()) {
            return name;
        }
        return email != null ? email : "Utilisateur";
    }

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (maxClients == null) {
            maxClients = 10;
        }
    }
}