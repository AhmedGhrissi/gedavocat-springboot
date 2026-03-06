package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant un membre du cabinet avec son rôle
 * MULTI-TENANT: Isolation automatique par firmId
 * 
 * Architecture :
 * - Lie un User à un Firm avec un rôle spécifique (ADMIN, AVOCAT, COLLABORATEUR)
 * - L'ADMIN du cabinet peut gérer les membres et affecter des dossiers
 * - Audit complet : qui a ajouté qui, quand, statut actif/inactif
 * - Respect RGPD : traçabilité des accès et modifications
 * 
 * Sécurité :
 * - Filtre Hibernate automatique sur firm_id
 * - Verrouillage optimiste avec @Version
 * - Index pour performances (firm_id, user_id, role, is_active)
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Entity
@Table(name = "firm_members", 
    uniqueConstraints = @UniqueConstraint(
        name = "uk_firm_member_user", 
        columnNames = {"firm_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_firm_member_firm_id", columnList = "firm_id"),
        @Index(name = "idx_firm_member_user_id", columnList = "user_id"),
        @Index(name = "idx_firm_member_role", columnList = "role"),
        @Index(name = "idx_firm_member_is_active", columnList = "is_active"),
        @Index(name = "idx_firm_member_added_by", columnList = "added_by")
    }
)
@Filter(name = "firmFilter", condition = "firm_id = :firmId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"firm", "user", "addedByUser"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FirmMember {

    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id = UUID.randomUUID().toString();

    /**
     * Version pour verrouillage optimiste - prévention des conflits concurrents
     * SEC-HARDENED: Conformité ANSSI - Intégrité des données
     */
    @Version
    @Column(name = "entity_version")
    private Long entityVersion;

    /**
     * Cabinet auquel appartient ce membre
     * MULTI-TENANT: Clé d'isolation - CRITIQUE pour sécurité
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "firm_id", nullable = false)
    @NotNull(message = "Le cabinet est obligatoire")
    private Firm firm;

    /**
     * Utilisateur membre du cabinet
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "L'utilisateur est obligatoire")
    private User user;

    /**
     * Rôle du membre dans le cabinet
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @NotNull(message = "Le rôle est obligatoire")
    private FirmRole role = FirmRole.COLLABORATEUR;

    /**
     * Statut actif/inactif du membre
     * Permet de désactiver temporairement un collaborateur sans supprimer l'historique
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Date d'ajout au cabinet
     * RGPD: Traçabilité des modifications
     */
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /**
     * Date de dernière modification
     * RGPD: Traçabilité des modifications
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Date de départ/désactivation
     * RGPD: Conservation des données - durée de rétention
     */
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    /**
     * Utilisateur ayant ajouté ce membre (traçabilité)
     * AUDIT: Qui a effectué l'action
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedByUser;

    /**
     * Titre/fonction dans le cabinet (optionnel)
     * Ex: "Avocat associé", "Stagiaire", "Collaborateur senior"
     */
    @Column(name = "title", length = 100)
    private String title;

    /**
     * Spécialité juridique (optionnel)
     * Ex: "Droit pénal", "Droit des affaires"
     */
    @Column(name = "specialty", length = 100)
    private String specialty;

    /**
     * Notes internes sur ce membre (optionnel)
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Enum des rôles dans le cabinet
     */
    public enum FirmRole {
        /**
         * Administrateur du cabinet - tous les droits de gestion
         * Peut ajouter/retirer des membres, affecter des dossiers
         */
        ADMIN,
        
        /**
         * Avocat titulaire - peut gérer ses propres dossiers
         */
        AVOCAT,
        
        /**
         * Collaborateur - accès limité selon affectations
         */
        COLLABORATEUR,
        
        /**
         * Stagiaire - accès en lecture seule selon affectations
         */
        STAGIAIRE
    }

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    /**
     * Vérifie si ce membre est administrateur du cabinet
     */
    public boolean isAdmin() {
        return role == FirmRole.ADMIN && isActive;
    }

    /**
     * Vérifie si ce membre peut gérer d'autres membres
     */
    public boolean canManageMembers() {
        return isAdmin();
    }

    /**
     * Vérifie si ce membre peut affecter des dossiers
     */
    public boolean canAssignCases() {
        return (role == FirmRole.ADMIN || role == FirmRole.AVOCAT) && isActive;
    }

    /**
     * Désactive ce membre
     */
    public void deactivate() {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
    }

    /**
     * Réactive ce membre
     */
    public void reactivate() {
        this.isActive = true;
        this.leftAt = null;
    }
}
