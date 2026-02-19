package com.gedavocat.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Tests du service JWT")
class JwtServiceTest {

    private JwtService jwtService;

    // Clé secrète de test (min 256 bits pour HS256) - valid base64
    private static final String SECRET =
        "Z2VkYXZvY2F0dGVzdHNlY3JldGtleW1pbmltdW0yNTZiaXRzcG91cmhzMjU2MjAyNHh4eHh4eHh4eHg=";
    private static final long EXPIRATION = 86400000L; // 24h

    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION);

        testUser = User.builder()
            .username("jean.dupont@gedavocat.com")
            .password("$2a$10$hashed")
            .authorities(Collections.emptyList())
            .build();
    }

    @Test
    @DisplayName("✓ Génération d'un token JWT valide")
    void generateTokenReturnsNonBlankString() {
        String token = jwtService.generateToken(testUser);

        assertThat(token)
            .isNotBlank()
            .contains(".");  // Format JWT : header.payload.signature
    }

    @Test
    @DisplayName("✓ Extraction du username depuis le token")
    void extractUsernameFromToken() {
        String token = jwtService.generateToken(testUser);
        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo("jean.dupont@gedavocat.com");
    }

    @Test
    @DisplayName("✓ Token valide pour l'utilisateur correct")
    void tokenIsValidForCorrectUser() {
        String token = jwtService.generateToken(testUser);

        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    @DisplayName("✗ Token invalide pour un autre utilisateur")
    void tokenIsInvalidForDifferentUser() {
        String token = jwtService.generateToken(testUser);

        UserDetails otherUser = User.builder()
            .username("autre@email.com")
            .password("pass")
            .authorities(Collections.emptyList())
            .build();

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("✓ Deux tokens générés sont différents (timestamp différent)")
    void twoGeneratedTokensAreDifferent() throws InterruptedException {
        String token1 = jwtService.generateToken(testUser);
        Thread.sleep(1100); // Délai pour que l'iat soit différent (1 seconde minimum)
        String token2 = jwtService.generateToken(testUser);

        assertThat(token1).isNotEqualTo(token2);
    }
}
