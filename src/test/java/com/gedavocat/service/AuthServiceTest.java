package com.gedavocat.service;

import com.gedavocat.dto.AuthRequest;
import com.gedavocat.dto.AuthResponse;
import com.gedavocat.dto.RegisterRequest;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests du service d'authentification")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserDetailsService userDetailsService;
    @Mock private UserDetails userDetails;
    @Mock private FirmService firmService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-id-001");
        testUser.setName("Maitre Jean Dupont");
        testUser.setFirstName("Jean");
        testUser.setLastName("Dupont");
        testUser.setEmail("jean.dupont@gedavocat.com");
        testUser.setPassword("$2a$10$hashedPassword");
        testUser.setRole(User.UserRole.LAWYER);
        testUser.setEmailVerified(true);
        testUser.setAccountEnabled(true);
    }

    // ===================================================================
    // TESTS LOGIN
    // ===================================================================

    @Test
    @DisplayName("✓ Connexion réussie avec email et mot de passe corrects")
    void loginSuccess() {
        // Given
        AuthRequest request = new AuthRequest();
        request.setEmail("jean.dupont@gedavocat.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("jean.dupont@gedavocat.com"))
            .thenReturn(Optional.of(testUser));
        when(userDetailsService.loadUserByUsername("jean.dupont@gedavocat.com"))
            .thenReturn(userDetails);
        when(jwtService.generateToken(userDetails))
            .thenReturn("jwt.token.valide");

        // When
        AuthResponse response = authService.authenticate(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt.token.valide");
        assertThat(response.getEmail()).isEqualTo("jean.dupont@gedavocat.com");
        assertThat(response.getRole()).isEqualTo("LAWYER");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("✗ Connexion échouée avec mauvais mot de passe")
    void loginFailsWithWrongPassword() {
        // Given
        AuthRequest request = new AuthRequest();
        request.setEmail("jean.dupont@gedavocat.com");
        request.setPassword("mauvaisMotDePasse");

        doThrow(new BadCredentialsException("Mauvais identifiants"))
            .when(authenticationManager)
            .authenticate(any());

        // When / Then
        assertThatThrownBy(() -> authService.authenticate(request))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("✗ Connexion échouée avec email inexistant")
    void loginFailsWithUnknownEmail() {
        // Given
        AuthRequest request = new AuthRequest();
        request.setEmail("inconnu@email.com");
        request.setPassword("password123");

        // When / Then
        // authenticationManager lève une exception pour utilisateur inexistant

        assertThatThrownBy(() -> authService.authenticate(request))
            .isInstanceOf(RuntimeException.class);
    }

    // ===================================================================
    // TESTS INSCRIPTION
    // ===================================================================

    @Test
    @DisplayName("✓ Inscription réussie")
    void registerSuccess() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Nouveau");
        request.setLastName("Utilisateur");
        request.setName("Maitre Nouveau");
        request.setEmail("nouveau@gedavocat.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setRole("LAWYER");
        request.setTermsAccepted(true);
        request.setGdprConsent(true);

        when(userRepository.findByEmail("nouveau@gedavocat.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        AuthResponse response = authService.register(request);

        // Then — après le fix sécurité, le token n'est PAS émis avant vérification email
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNull();
        assertThat(response.getMessage()).isEqualTo("Veuillez vérifier votre email avant de vous connecter");
        assertThat(response.getEmail()).isEqualTo("jean.dupont@gedavocat.com");
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("password123");
        // Le JWT ne doit PAS être généré à l'inscription
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("✗ Inscription échouée - email déjà utilisé")
    void registerFailsEmailAlreadyExists() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("jean.dupont@gedavocat.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");

        // Simuler un utilisateur existant avec email vérifié
        User verifiedUser = new User();
        verifiedUser.setEmail("jean.dupont@gedavocat.com");
        verifiedUser.setEmailVerified(true);
        when(userRepository.findByEmail("jean.dupont@gedavocat.com")).thenReturn(Optional.of(verifiedUser));

        // When / Then — SEC-08 FIX : message générique pour éviter l'énumération
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Erreur lors de l'inscription");
    }

    @Test
    @DisplayName("✗ Inscription échouée - mots de passe différents")
    void registerFailsPasswordMismatch() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@email.com");
        request.setPassword("password123");
        request.setConfirmPassword("autreMotDePasse");

        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("correspondent pas");
    }
}
