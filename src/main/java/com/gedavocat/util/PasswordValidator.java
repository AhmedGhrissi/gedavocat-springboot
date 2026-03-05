package com.gedavocat.util;

import java.util.regex.Pattern;

/**
 * SEC-15 FIX : Validateur de mot de passe centralisé.
 * Mêmes règles que RegisterRequest DTO :
 * - Minimum 12 caractères
 * - Au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial
 */
public final class PasswordValidator {

    private PasswordValidator() {} // Utility class

    // UX-01 FIX : harmonisé avec RegisterRequest — accepte tout caractère non-alphanumérique
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,}$"
    );

    public static final String PASSWORD_REQUIREMENTS_MESSAGE =
        "Le mot de passe doit contenir au moins 12 caractères, dont 1 majuscule, 1 minuscule, 1 chiffre et 1 caractère spécial.";

    /**
     * Vérifie si le mot de passe respecte les exigences de sécurité.
     */
    public static boolean isValid(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
}
