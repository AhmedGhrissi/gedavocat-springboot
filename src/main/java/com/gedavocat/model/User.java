package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
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
    @Index(name = "idx_user_role", columnList = "role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    @Column(nullable = false, length = 100)
    private String name;

    // Nouveaux champs pour les détails du profil
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "bar_number", length = 50)
    private String barNumber;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

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
    private boolean emailVerified = true; // true par défaut pour les utilisateurs existants

    @Column(name = "account_enabled", nullable = false)
    private boolean accountEnabled = true;

    @Column(name = "access_ends_at")
    private LocalDateTime accessEndsAt;

    @Column(name = "invitation_id", length = 36)
    private String invitationId;

    // ==========================================
    // RELATIONS
    // ==========================================

    @OneToMany(mappedBy = "lawyer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Client> clients = new HashSet<>();

    @OneToMany(mappedBy = "lawyer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Case> cases = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<AuditLog> auditLogs = new HashSet<>();

    // ==========================================
    // ÉNUMÉRATIONS
    // ==========================================

    public enum UserRole {
        ADMIN("Administrateur"),
        LAWYER("Avocat"),
        CLIENT("Client"),
        LAWYER_SECONDARY("Collaborateur");

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
        CABINET("Cabinet", 99.99, 100),
        ENTERPRISE("Enterprise", 299.99, Integer.MAX_VALUE);

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