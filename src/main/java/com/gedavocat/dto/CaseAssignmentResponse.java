package com.gedavocat.dto;

import com.gedavocat.model.CaseAssignment.AssignmentRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de réponse pour une affectation de dossier
 * 
 * Sécurité :
 * - Ne renvoie que les informations nécessaires
 * - Compatible avec la sérialisation JSON
 * - Informations de traçabilité incluses
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaseAssignmentResponse {

    private String id;
    private String caseId;
    private String caseTitle;
    private String caseReference;
    private String memberId;
    private String memberName;
    private String memberEmail;
    private AssignmentRole assignmentRole;
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canUpload;
    private Boolean canDelete;
    private Boolean canManagePermissions;
    private Boolean isActive;
    private LocalDateTime assignedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String assignedByEmail;
    private String notes;
}
