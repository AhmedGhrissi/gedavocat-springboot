package com.gedavocat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Test de démarrage de l'application")
class GedAvocatApplicationTest {

    @Test
    @DisplayName("✓ Le contexte Spring démarre sans erreur")
    void contextLoads() {
        // Si ce test passe, toute la config Spring est valide :
        // - SecurityConfig
        // - JwtService (clé Base64 valide)
        // - UserDetailsService
        // - Tous les Beans injectés correctement
    }
}
