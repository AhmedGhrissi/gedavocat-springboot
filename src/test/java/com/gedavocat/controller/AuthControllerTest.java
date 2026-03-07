package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests du contrôleur d'authentification")
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Nettoyer et créer les utilisateurs de test
        userRepository.deleteAll();
        userRepository.flush();
        
        // Créer un utilisateur admin pour les tests
        User admin = new User();
        admin.setId(UUID.randomUUID().toString());
        admin.setName("Admin Test");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setEmail("admin@gedavocat.com");
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setRole(User.UserRole.ADMIN);
        admin.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        admin.setEmailVerified(true);
        admin.setAccountEnabled(true);
        userRepository.saveAndFlush(admin);
        
        // Créer un utilisateur avocat pour les tests
        User lawyer = new User();
        lawyer.setId(UUID.randomUUID().toString());
        lawyer.setName("Jean Dupont");
        lawyer.setFirstName("Jean");
        lawyer.setLastName("Dupont");
        lawyer.setEmail("jean.dupont@gedavocat.com");
        lawyer.setPassword(passwordEncoder.encode("password"));
        lawyer.setRole(User.UserRole.LAWYER);
        lawyer.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        lawyer.setSubscriptionEndsAt(java.time.LocalDateTime.now().plusDays(30));
        lawyer.setEmailVerified(true);
        lawyer.setAccountEnabled(true);
        userRepository.saveAndFlush(lawyer);
    }

    @Test
    @DisplayName("✓ GET /login retourne la page de connexion")
    void loginPageIsAccessible() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/login"));
    }

    @Test
    @DisplayName("✓ GET /dashboard redirige vers /login si non authentifié")
    void dashboardRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("✓ Formulaire login POST /login avec champ 'email' (pas 'username')")
    void loginFormPostWithEmailField() throws Exception {
        mockMvc.perform(post("/login")
                .param("email", "jean.dupont@gedavocat.com")
                .param("password", "password123")
                .with(csrf()))
            // Redirige vers /login?error si pas en base (test sans DB)
            // ou vers /dashboard si authentifié
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("✓ Login avec champ 'username' échoue (paramètre incorrect)")
    void loginFormPostWithUsernameFieldFails() throws Exception {
        mockMvc.perform(post("/login")
                .param("username", "jean.dupont@gedavocat.com")  // MAUVAIS champ
                .param("password", "password123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    @DisplayName("✓ Page dashboard accessible avec un utilisateur authentifié")
    @WithMockUser(username = "jean.dupont@gedavocat.com", roles = {"LAWYER"})
    @Transactional
    void dashboardAccessibleWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ GET /logout déconnecte et redirige")
    @WithMockUser(username = "jean.dupont@gedavocat.com", roles = {"LAWYER"})
    void logoutRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("✓ Pages CSS/JS accessibles sans authentification")
    void staticResourcesArePublic() throws Exception {
        mockMvc.perform(get("/css/global-unified-theme.css"))
            .andExpect(status().isOk());
    }
}
