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
 * Entité représentant l'affectation d'un dossier à un collaborateur
 * MULTI-TENANT: Isolation automatique par firmId
 * 
 * Architecture :
 * - Gère l'affectation des dossiers aux membres du cabinet
 * - Droits granulaires (lecture, écriture, suppression)
 * - Audit complet : qui a affecté, quand, statut
 * - Expire automatiquement selon date de fin
 * 
 * Différence avec Permission :
 * - Permission = partage ponctuel entre avocats externes
 * - CaseAssignment = affectation permanente dans le cabinet
 * 
 * Sécurité :
 * - Filtre Hibernate automatique sur firm_id
 * - Verrouillage optimiste avec @Version
 * - Validation des droits avant toute action
 * - Traçabilité RGPD complète
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Entity
@Table(name = "case_assignments", 
    uniqueConstraints = @UniqueConstraint(
        name = "uk_case_assignment", 
        columnNames = {"case_id", "member_id"}
    ),
    indexes = {
        @Index(name = "idx_case_assignment_case_id", columnList = "case_id"),
        @Index(name = "idx_case_assignment_member_id", columnList = "member_id"),
        @Index(name = "idx_case_assignment_firm_id", columnList = "firm_id"),
        @Index(name = "idx_case_assignment_is_active", columnList = "is_active"),
        @Index(name = "idx_case_assignment_assigned_by", columnList = "assigned_by")
    }
)
@Filter(name = "firmFilter", condition = "firm_id = :firmId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"firm", "caseEntity", "member", "assignedByUser"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CaseAssignment {

    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id = UUID.randomUUID().toString();

    /**
     * Version pour verrouillage optimiste
     * SEC-HARDENED: Conformité ANSSI - Intégrité des données
     */
    @Version
    @Column(name = "entity_version")
    private Long entityVersion;

    /**
     * Cabinet - Clé d'isolation multi-tenant
     * MULTI-TENANT: CRITIQUE pour sécurité
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "firm_id", nullable = false)
    @NotNull(message = "Le cabinet est obligatoire")
    private Firm firm;

    /**
     * Dossier affecté
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    @NotNull(message = "Le dossier est obligatoire")
    private Case caseEntity;

    /**
     * Membre du cabinet affecté au dossier
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @NotNull(message = "Le membre est obligatoire")
    private FirmMember member;

    /**
     * Rôle dans le dossier
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 30)
    @NotNull(message = "Le rôle est obligatoire")
    private AssignmentRole assignmentRole = AssignmentRole.COLLABORATEUR;

    /**
     * Droit de lecture
     */
    @Column(name = "can_read", nullable = false)
    private Boolean canRead = true;

    /**
     * Droit d'écriture (modifier le dossier)
     */
    @Column(name = "can_write", nullable = false)
    private Boolean canWrite = false;

    /**
     * Droit d'upload de documents
     */
    @Column(name = "can_upload", nullable = false)
    private Boolean canUpload = false;

    /**
     * Droit de suppression
     */
    @Column(name = "can_delete", nullable = false)
    private Boolean canDelete = false;

    /**
     * Droit de gérer les permissions (affecter d'autres collaborateurs)
     */
    @Column(name = "can_manage_permissions", nullable = false)
    private Boolean canManagePermissions = false;

    /**
     * Statut actif/inactif de l'affectation
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Date d'affectation
     * RGPD: Traçabilité
     */
    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /**
     * Date de dernière modification
     * RGPD: Traçabilité
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Date de fin d'affectation (optionnelle)
     * Permet de gérer des affectations temporaires
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Date de révocation
     * RGPD: Traçabilité
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Utilisateur ayant effectué l'affectation
     * AUDIT: Traçabilité des actions
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedByUser;

    /**
     * Notes sur cette affectation (optionnel)
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Enum des rôles dans une affectation
     */
    public enum AssignmentRole {
        /**
         * Responsable principal du dossier
         */
        RESPONSABLE,
        
        /**
         * Collaborateur actif sur le dossier
         */
        COLLABORATEUR,
        
        /**
         * Superviseur (lecture + conseil)
         */
        SUPERVISEUR,
        
        /**
         * Observateur (lecture seule)
         */
        OBSERVATEUR
    }

    // ==========================================
    // MÉTHODES UTILITAIRES
    // ==========================================

    /**
     * Vérifie si l'affectation est active et non expirée
     */
    public boolean isActiveAndValid() {
        if (!isActive || revokedAt != null) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Vérifie si ce membre peut modifier le dossier
     */
    public boolean canModify() {
        return isActiveAndValid() && canWrite;
    }

    /**
     * Vérifie si ce membre est responsable du dossier
     */
    public boolean isResponsable() {
        return isActiveAndValid() && assignmentRole == AssignmentRole.RESPONSABLE;
    }

    /**
     * Révoque l'affectation
     */
    public void revoke() {
        this.isActive = false;
        this.revokedAt = LocalDateTime.now();
    }

    /**
     * Active l'affectation
     */
    public void activate() {
        this.isActive = true;
        this.revokedAt = null;
    }

    /**
     * Définit les droits complets (responsable)
     */
    public void setFullAccess() {
        this.canRead = true;
        this.canWrite = true;
        this.canUpload = true;
        this.canDelete = true;
        this.canManagePermissions = true;
        this.assignmentRole = AssignmentRole.RESPONSABLE;
    }

    /**
     * Définit les droits en lecture seule
     */
    public void setReadOnlyAccess() {
        this.canRead = true;
        this.canWrite = false;
        this.canUpload = false;
        this.canDelete = false;
        this.canManagePermissions = false;
        this.assignmentRole = AssignmentRole.OBSERVATEUR;
    }

    /**
     * Définit les droits de collaborateur standard
     */
    public void setCollaboratorAccess() {
        this.canRead = true;
        this.canWrite = true;
        this.canUpload = true;
        this.canDelete = false;
        this.canManagePermissions = false;
        this.assignmentRole = AssignmentRole.COLLABORATEUR;
    }
}
