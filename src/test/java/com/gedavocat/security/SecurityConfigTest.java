package com.gedavocat.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de la configuration Spring Security")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // ===================================================================
    // URLs PUBLIQUES (sans authentification)
    // ===================================================================

    @Test
    @DisplayName("✓ /login accessible sans authentification")
    void loginIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ /register accessible sans authentification")
    void registerIsPublic() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ /subscription/pricing accessible sans authentification")
    void pricingIsPublic() throws Exception {
        mockMvc.perform(get("/subscription/pricing"))
            .andExpect(status().isOk());
    }

    // ===================================================================
    // URLs PROTÉGÉES → Redirigent vers /login
    // ===================================================================

    @Test
    @DisplayName("✓ /dashboard redirige vers /login si non connecté")
    void dashboardRequiresAuth() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login**"));
    }

    @Test
    @DisplayName("✓ /clients redirige vers /login si non connecté")
    void clientsRequiresAuth() throws Exception {
        mockMvc.perform(get("/clients"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login**"));
    }

    @Test
    @DisplayName("✓ /signatures redirige vers /login si non connecté")
    void signaturesRequiresAuth() throws Exception {
        mockMvc.perform(get("/signatures"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login**"));
    }

    @Test
    @DisplayName("✓ /rpva redirige vers /login si non connecté")
    void rpvaRequiresAuth() throws Exception {
        mockMvc.perform(get("/rpva"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login**"));
    }

    // ===================================================================
    // ACCÈS AVEC RÔLES
    // ===================================================================

    @Test
    @DisplayName("✓ Avocat peut accéder au dashboard")
    @WithMockUser(roles = "LAWYER")
    void lawyerCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ Admin peut accéder au dashboard")
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ Client ne peut pas accéder aux pages avocat")
    @WithMockUser(roles = "CLIENT")
    void clientCannotAccessLawyerPages() throws Exception {
        mockMvc.perform(get("/clients"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("✓ Avocat ne peut pas accéder aux pages admin")
    @WithMockUser(roles = "LAWYER")
    void lawyerCannotAccessAdminPages() throws Exception {
        mockMvc.perform(get("/admin"))
            .andExpect(status().isForbidden());
    }
}
