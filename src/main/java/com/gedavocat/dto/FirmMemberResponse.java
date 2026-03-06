package com.gedavocat.dto;

import com.gedavocat.model.FirmMember.FirmRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de réponse pour un membre du cabinet
 * 
 * Sécurité :
 * - Ne renvoie pas les données sensibles (mot de passe)
 * - Informations limitées au nécessaire
 * - Compatible avec la sérialisation JSON
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirmMemberResponse {

    private String id;
    private String userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private String userPhone;
    private FirmRole role;
    private Boolean isActive;
    private String title;
    private String specialty;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private String addedByEmail;
    private Long caseCount; // Nombre de dossiers affectés
}
