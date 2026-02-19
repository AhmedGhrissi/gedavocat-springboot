package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Contrôleur de gestion des paramètres
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserRepository userRepository;
    private final SettingsService settingsService;

    /**
     * Page des paramètres (garde pour compatibilité)
     */
    @GetMapping
    public String settings(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        
        model.addAttribute("user", user);
        model.addAttribute("yousignApiKey", settingsService.getYousignApiKey(user.getId()));
        model.addAttribute("yousignConfigured", settingsService.isYousignConfigured(user.getId()));
        
        return "settings/index";
    }

    /**
     * Sauvegarder les paramètres Yousign (API pour modal)
     */
    @PostMapping("/yousign")
    @ResponseBody
    public ResponseEntity<?> saveYousignSettings(
            @RequestParam("apiKey") String apiKey,
            @RequestParam(value = "sandbox", required = false, defaultValue = "false") boolean sandbox,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            settingsService.saveYousignSettings(user.getId(), apiKey, sandbox);
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "Configuration Yousign sauvegardée avec succès"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "Erreur lors de la sauvegarde: " + e.getMessage()
            ));
        }
    }

    /**
     * Tester la configuration Yousign (API pour modal)
     */
    @PostMapping("/yousign/test")
    @ResponseBody
    public ResponseEntity<?> testYousignConfig(Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            boolean isValid = settingsService.testYousignConnection(user.getId());
            
            if (isValid) {
                return ResponseEntity.ok(Map.of(
                    "success", true, 
                    "message", "✅ Connexion Yousign réussie !"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false, 
                    "message", "❌ Échec de la connexion Yousign. Vérifiez votre clé API."
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "Erreur lors du test: " + e.getMessage()
            ));
        }
    }

    /**
     * Mise à jour du profil utilisateur (API pour modal)
     */
    @PostMapping("/profile")
    @ResponseBody
    public ResponseEntity<?> updateProfile(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "barNumber", required = false) String barNumber,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setPhone(phone);
            user.setBarNumber(barNumber);
            
            // Mettre à jour le nom complet pour compatibilité
            user.setName(firstName + " " + lastName);
            
            userRepository.save(user);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "Profil mis à jour avec succès"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "Erreur lors de la mise à jour: " + e.getMessage()
            ));
        }
    }

    /**
     * Récupérer les données utilisateur pour le modal
     */
    @GetMapping("/user-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserData(Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            Map<String, Object> userData = Map.of(
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : "",
                "email", user.getEmail(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "barNumber", user.getBarNumber() != null ? user.getBarNumber() : "",
                "subscriptionPlan", user.getSubscriptionPlan() != null ? user.getSubscriptionPlan().getDisplayName() : "FREE",
                "maxClients", user.getMaxClients(),
                "yousignConfigured", settingsService.isYousignConfigured(user.getId()),
                "yousignApiKey", settingsService.getYousignApiKey(user.getId())
            );
            return ResponseEntity.ok(userData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}