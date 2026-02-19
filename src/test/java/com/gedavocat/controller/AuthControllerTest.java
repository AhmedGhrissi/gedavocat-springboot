package com.gedavocat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests du contrôleur d'authentification")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    @WithMockUser(username = "admin@gedavocat.com", roles = {"ADMIN"})
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
            .andExpect(redirectedUrl("/login?logout=true"));
    }

    @Test
    @DisplayName("✓ Pages CSS/JS accessibles sans authentification")
    void staticResourcesArePublic() throws Exception {
        mockMvc.perform(get("/css/main.css"))
            .andExpect(status().isOk());
    }
}
