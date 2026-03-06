package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité représentant un cabinet d'avocats (Firm) -  MULTI-TENANT
 * 
 * Architecture Multi-Tenant :
 * - Chaque cabinet est isolé (firmId dans toutes les entités)
 * - Filtre Hibernate automatique pour éviter les fuites de données cross-tenant
 * - Un cabinet = plusieurs avocats (un titulaire + collaborateurs)
 * - Les données (dossiers, documents, clients) sont liées au firmId
 * 
 * Référence : docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 (VULN-01)
 */
@Entity
@Table(name = "firms", indexes = {
    @Index(name = "idx_firm_created_at", columnList = "created_at"),
    @Index(name = "idx_firm_subscription_status", columnList = "subscription_status")
})
@FilterDef(name = "firmFilter", parameters = @ParamDef(name = "firmId", type = String.class))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Firm {

    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id;

    @NotBlank(message = "Le nom du cabinet est obligatoire")
    @Size(min = 2, max = 255, message = "Le nom doit contenir entre 2 et 255 caractères")
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    @Email(message = "Email invalide")
    @Column(length = 255)
    private String email;

    @Column(name = "siren", length = 50)
    private String siren;

    @Column(name = "tva_number", length = 20)
    private String tvaNumber;

    // ==========================================
    // ABONNEMENT CABINET
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", length = 20)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.SOLO;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @Column(name = "subscription_starts_at")
    private LocalDateTime subscriptionStartsAt;

    @Column(name = "subscription_ends_at")
    private LocalDateTime subscriptionEndsAt;

    /**
     * Nombre maximum d'avocats autorisés dans ce cabinet
     */
    @Column(name = "max_lawyers", nullable = false)
    private Integer maxLawyers = 1;

    /**
     * Nombre maximum de clients autorisés dans ce cabinet
     */
    @Column(name = "max_clients", nullable = false)
    private Integer maxClients = 10;

    // ========================================== 
    // IDENTIFIANT STRIPE (PAIEMENTS)
    // ==========================================

    @Column(name = "stripe_customer_id", length = 100)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 100)
    private String stripeSubscriptionId;

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
    // RELATIONS
    // ==========================================

    @OneToMany(mappedBy = "firm", cascade = CascadeType.ALL)
    private Set<User> users = new HashSet<>();

    @OneToMany(mappedBy = "firm", cascade = CascadeType.ALL)
    private Set<Case> cases = new HashSet<>();

    @OneToMany(mappedBy = "firm", cascade = CascadeType.ALL)
    private Set<Document> documents = new HashSet<>();

    @OneToMany(mappedBy = "firm", cascade = CascadeType.ALL)
    private Set<Client> clients = new HashSet<>();

    // ==========================================
    // ÉNUMÉRATIONS
    // ==========================================

    public enum SubscriptionPlan {
        SOLO("Solo", 29.99, 1, 10),
        CABINET("Cabinet", 99.99, 5, 75),
        ENTERPRISE("Enterprise", 299.99, Integer.MAX_VALUE, Integer.MAX_VALUE);

        private final String displayName;
        private final double price;
        private final int maxLawyers;
        private final int maxClients;

        SubscriptionPlan(String displayName, double price, int maxLawyers, int maxClients) {
            this.displayName = displayName;
            this.price = price;
            this.maxLawyers = maxLawyers;
            this.maxClients = maxClients;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getPrice() {
            return price;
        }

        public int getMaxLawyers() {
            return maxLawyers;
        }

        public int getMaxClients() {
            return maxClients;
        }
    }

    public enum SubscriptionStatus {
        ACTIVE("Actif"),
        TRIAL("Essai"),
        INACTIVE("Inactif"),
        CANCELLED("Annulé"),
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
        return subscriptionEndsAt != null && subscriptionEndsAt.isBefore(LocalDateTime.now());
    }

    /**
     * Vérifier si le cabinet peut ajouter un nouvel avocat
     */
    public boolean canAddMoreLawyers(int currentLawyersCount) {
        return currentLawyersCount < maxLawyers;
    }

    /**
     * Vérifier si le cabinet peut ajouter un nouveau client
     */
    public boolean canAddMoreClients(int currentClientsCount) {
        return currentClientsCount < maxClients;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (maxLawyers == null) {
            maxLawyers = 1;
        }
        if (maxClients == null) {
            maxClients = 10;
        }
        if (subscriptionPlan == null) {
            subscriptionPlan = SubscriptionPlan.SOLO;
        }
        if (subscriptionStatus == null) {
            subscriptionStatus = SubscriptionStatus.TRIAL;
        }
    }
}
