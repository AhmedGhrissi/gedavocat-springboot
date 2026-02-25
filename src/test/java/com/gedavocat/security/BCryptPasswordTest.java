package com.gedavocat.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test du hash BCrypt utilisé dans data.sql
 * Lance ce test EN PREMIER pour vérifier que le hash est correct
 */
@DisplayName("Vérification des mots de passe BCrypt")
class BCryptPasswordTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    // Hash stocké dans data.sql pour tous les comptes
    private static final String HASH_DATA_SQL =
        "$2a$10$Xf1J5kSEhMYjPN1EukEzuuVFxHOfziBArKMbzbcuQFrMsf.vygLhu";

    @Test
    @DisplayName("✓ Le hash data.sql correspond bien à 'password123'")
    void hashDataSqlMatchesPassword123() {
        assertThat(encoder.matches("password123", HASH_DATA_SQL))
            .as("Le hash dans data.sql doit correspondre à 'password123'")
            .isTrue();
    }

    @Test
    @DisplayName("✓ Un mauvais mot de passe ne correspond pas")
    void wrongPasswordDoesNotMatch() {
        assertThat(encoder.matches("mauvaisMotDePasse", HASH_DATA_SQL))
            .isFalse();
    }

    @Test
    @DisplayName("✓ Génération d'un nouveau hash pour 'password123'")
    void generateNewHashForPassword123() {
        String newHash = encoder.encode("password123");
        System.out.println("\n=== NOUVEAU HASH BCRYPT ===");
        System.out.println("Mot de passe : password123");
        System.out.println("Hash généré  : " + newHash);
        System.out.println("==========================\n");

        // Vérifier que le hash généré est valide
        assertThat(encoder.matches("password123", newHash)).isTrue();
    }

    @Test
    @DisplayName("✓ Génération du hash pour les tests (à copier dans data.sql si besoin)")
    void generateHashesForAllTestAccounts() {
        String[] passwords = {"password123", "admin123", "avocat123", "client123"};

        System.out.println("\n=== HASHES POUR data.sql ===");
        for (String pwd : passwords) {
            System.out.printf("%-15s -> %s%n", pwd, encoder.encode(pwd));
        }
        System.out.println("============================\n");

        // Ce test vérifie juste que l'encodage fonctionne
        for (String pwd : passwords) {
            String hash = encoder.encode(pwd);
            assertThat(encoder.matches(pwd, hash)).isTrue();
        }
    }

    @Test
    @DisplayName("✓ Génération du hash pour Admin1234! et test $2b$ compatibilité")
    void testAdmin1234Hash() {
        String password = "Admin1234!";
        String newHash = encoder.encode(password);
        System.out.println("\n=== HASH ADMIN ===");
        System.out.println("Password: " + password);
        System.out.println("New hash: " + newHash);

        // Test if $2b$ hash from production is compatible
        String prodHash = "$2b$10$5U1hpYyYfczdEjw/YJU0fex3S.iLQjE5GRoKjSIA83e/fo4IuZASK";
        boolean matches2b = encoder.matches(password, prodHash);
        System.out.println("$2b$ hash matches Admin1234!: " + matches2b);

        // Test $2a$ version of the same hash
        String hash2a = prodHash.replace("$2b$", "$2a$");
        boolean matches2a = encoder.matches(password, hash2a);
        System.out.println("$2a$ hash matches Admin1234!: " + matches2a);
        System.out.println("==================\n");

        assertThat(encoder.matches(password, newHash)).isTrue();
    }
}
