package com.gedavocat.dto;

import com.gedavocat.model.CaseAssignment.AssignmentRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour l'affectation d'un dossier à un membre
 * 
 * Sécurité :
 * - Validation Jakarta complète
 * - Vérification des IDs
 * - Droits par défaut configurables
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignCaseRequest {

    /**
     * ID du dossier à affecter
     */
    @NotBlank(message = "L'ID du dossier est obligatoire")
    @Size(max = 36, message = "ID invalide")
    private String caseId;

    /**
     * ID du membre à qui affecter le dossier
     */
    @NotBlank(message = "L'ID du membre est obligatoire")
    @Size(max = 36, message = "ID invalide")
    private String memberId;

    /**
     * Rôle dans le dossier
     */
    @NotNull(message = "Le rôle est obligatoire")
    private AssignmentRole assignmentRole;

    /**
     * Droit de lecture (par défaut: true)
     */
    private Boolean canRead = true;

    /**
     * Droit d'écriture (par défaut: false)
     */
    private Boolean canWrite = false;

    /**
     * Droit d'upload (par défaut: false)
     */
    private Boolean canUpload = false;

    /**
     * Droit de suppression (par défaut: false)
     */
    private Boolean canDelete = false;

    /**
     * Droit de gérer les permissions (par défaut: false)
     */
    private Boolean canManagePermissions = false;

    /**
     * Date d'expiration (optionnel)
     * Format ISO 8601: 2026-12-31T23:59:59
     */
    private LocalDateTime expiresAt;

    /**
     * Notes sur cette affectation (optionnel)
     */
    @Size(max = 5000, message = "Les notes ne doivent pas dépasser 5000 caractères")
    private String notes;

    /**
     * Configure les droits pour un responsable (tous les droits)
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
     * Configure les droits pour un observateur (lecture seule)
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
     * Configure les droits pour un collaborateur standard
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
