package com.gedavocat.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilitaire pour générer les hash BCrypt avec strength=12
 * Utilisé pour le schema SQL
 */
class GeneratePasswordHash {

    @Test
    void generateHashesForSQL() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        
        System.out.println("=== HASH BCRYPT (strength=12) ===");
        
        String adminHash = encoder.encode("admin");
        System.out.println("admin : " + adminHash);
        
        String avocatHash = encoder.encode("avocat");
        System.out.println("avocat : " + avocatHash);
        
        // Vérification
        System.out.println("\n=== VERIFICATION ===");
        System.out.println("admin matches : " + encoder.matches("admin", adminHash));
        System.out.println("avocat matches : " + encoder.matches("avocat", avocatHash));
    }
}
