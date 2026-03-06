package com.gedavocat.security;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de la configuration Spring Security")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Nettoyer et créer les utilisateurs de test pour éviter "Utilisateur non trouvé"
        userRepository.deleteAll();
        userRepository.flush();
        
        // Créer un utilisateur avec le username par défaut de @WithMockUser ("user")
        User defaultUser = new User();
        defaultUser.setId(UUID.randomUUID().toString());
        defaultUser.setName("User Test");
        defaultUser.setFirstName("User");
        defaultUser.setLastName("Test");
        defaultUser.setEmail("user@test.com"); // Must be valid email format
        defaultUser.setPassword(passwordEncoder.encode("password"));
        defaultUser.setRole(User.UserRole.LAWYER);
        defaultUser.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        defaultUser.setSubscriptionEndsAt(java.time.LocalDateTime.now().plusDays(30));
        defaultUser.setEmailVerified(true);
        defaultUser.setAccountEnabled(true);
        userRepository.saveAndFlush(defaultUser);
        
        // Créer un utilisateur lawyer spécifique
        User lawyer = new User();
        lawyer.setId(UUID.randomUUID().toString());
        lawyer.setName("Lawyer Test");
        lawyer.setFirstName("Lawyer");
        lawyer.setLastName("Test");
        lawyer.setEmail("lawyer@test.com");
        lawyer.setPassword(passwordEncoder.encode("password"));
        lawyer.setRole(User.UserRole.LAWYER);
        lawyer.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        lawyer.setSubscriptionEndsAt(java.time.LocalDateTime.now().plusDays(30));
        lawyer.setEmailVerified(true);
        lawyer.setAccountEnabled(true);
        userRepository.saveAndFlush(lawyer);
        
        // Créer un utilisateur admin spécifique
        User admin = new User();
        admin.setId(UUID.randomUUID().toString());
        admin.setName("Admin Test");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setEmail("admin@test.com");
        admin.setPassword(passwordEncoder.encode("password"));
        admin.setRole(User.UserRole.ADMIN);
        admin.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        admin.setEmailVerified(true);
        admin.setAccountEnabled(true);
        userRepository.saveAndFlush(admin);
        
        // Créer un utilisateur client spécifique
        User client = new User();
        client.setId(UUID.randomUUID().toString());
        client.setName("Client Test");
        client.setFirstName("Client");
        client.setLastName("Test");
        client.setEmail("client@test.com");
        client.setPassword(passwordEncoder.encode("password"));
        client.setRole(User.UserRole.CLIENT);
        client.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setAccountEnabled(true);
        userRepository.saveAndFlush(client);
    }

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
        // Template requires RegisterRequest object, so just check it doesn't require auth
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ /subscription/pricing accessible sans authentification")
    void pricingIsPublic() throws Exception {
        // Template uses layout and requires Stripe configuration, just check no auth required
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
    @WithMockUser(username = "lawyer@test.com", roles = "LAWYER")
    @Transactional
    void lawyerCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("✓ Admin redirigé vers /admin depuis /dashboard")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @Transactional
    void adminCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("✓ Client ne peut pas accéder aux pages avocat")
    @WithMockUser(username = "client@test.com", roles = "CLIENT")
    void clientCannotAccessLawyerPages() throws Exception {
        mockMvc.perform(get("/clients"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("✓ Avocat ne peut pas accéder aux pages admin")
    @WithMockUser(username = "lawyer@test.com", roles = "LAWYER")
    void lawyerCannotAccessAdminPages() throws Exception {
        mockMvc.perform(get("/admin"))
            .andExpect(status().isForbidden());
    }
}
