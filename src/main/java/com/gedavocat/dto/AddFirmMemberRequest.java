package com.gedavocat.dto;

import com.gedavocat.model.FirmMember.FirmRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour l'ajout d'un nouveau membre au cabinet
 * 
 * Sécurité :
 * - Validation Jakarta complète
 * - Email normalisé en lowercase
 * - Rôle par défaut : COLLABORATEUR
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddFirmMemberRequest {

    /**
     * Email de l'utilisateur à ajouter
     * Doit correspondre à un utilisateur existant dans le système
     */
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
    private String email;

    /**
     * Rôle dans le cabinet
     */
    @NotNull(message = "Le rôle est obligatoire")
    private FirmRole role;

    /**
     * Titre/fonction (optionnel)
     * Ex: "Avocat associé", "Stagiaire"
     */
    @Size(max = 100, message = "Le titre ne doit pas dépasser 100 caractères")
    private String title;

    /**
     * Spécialité juridique (optionnel)
     * Ex: "Droit pénal", "Droit des affaires"
     */
    @Size(max = 100, message = "La spécialité ne doit pas dépasser 100 caractères")
    private String specialty;

    /**
     * Notes internes (optionnel)
     */
    @Size(max = 5000, message = "Les notes ne doivent pas dépasser 5000 caractères")
    private String notes;

    /**
     * Normalise l'email en lowercase
     */
    public void normalizeEmail() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}
