package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.SettingsService;
import com.gedavocat.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Contrôleur de gestion des paramètres
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
public class SettingsController {

    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Page des paramètres (garde pour compatibilité)
     */
    @GetMapping
    public String settings(Model model, Authentication authentication,
                           @ModelAttribute("message") String message,
                           @ModelAttribute("error") String error) {
        User user = getCurrentUser(authentication);
        
        model.addAttribute("user", user);
        // Masquer la clé API dans la vue
        String apiKey = settingsService.getYousignApiKey(user.getId());
        String maskedKey = "";
        if (apiKey != null && !apiKey.isEmpty()) {
            maskedKey = apiKey.length() > 4
                ? "••••" + apiKey.substring(apiKey.length() - 4)
                : "••••";
        }
        model.addAttribute("yousignApiKey", maskedKey);
        model.addAttribute("yousignConfigured", settingsService.isYousignConfigured(user.getId()));
        
        return "settings/index";
    }

    /**
     * Sauvegarder les paramètres Yousign (API pour modal)
     */
    /** Sauvegarde Yousign via formulaire HTML standard → redirect */
    @PostMapping("/yousign")
    public String saveYousignSettingsForm(
            @RequestParam("apiKey") String apiKey,
            @RequestParam(value = "sandbox", required = false, defaultValue = "false") boolean sandbox,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            settingsService.saveYousignSettings(user.getId(), apiKey, sandbox);
            redirectAttributes.addFlashAttribute("message", "Configuration Yousign sauvegardée avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde : " + e.getMessage());
        }
        return "redirect:/settings";
    }

    /** Sauvegarde Yousign via AJAX (compatibilité JSON) */
    @PostMapping(value = "/yousign", headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<?> saveYousignSettingsAjax(
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
    /** Mise à jour profil via formulaire HTML standard → redirect */
    @PostMapping("/profile")
    public String updateProfileForm(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "barNumber", required = false) String barNumber,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        // B23 FIX : Validation des entrées
        String validationError = validateProfileFields(firstName, lastName, phone, barNumber);
        if (validationError != null) {
            redirectAttributes.addFlashAttribute("error", validationError);
            return "redirect:/settings";
        }
        try {
            User user = getCurrentUser(authentication);
            user.setFirstName(firstName.trim());
            user.setLastName(lastName.trim());
            user.setPhone(phone != null ? phone.trim() : null);
            user.setBarNumber(barNumber != null ? barNumber.trim() : null);
            user.setName(firstName.trim() + " " + lastName.trim());
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("message", "Profil mis à jour avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la mise à jour : " + e.getMessage());
        }
        return "redirect:/settings";
    }

    /** Mise à jour profil via AJAX */
    @PostMapping(value = "/profile", headers = "Accept=application/json")
    @ResponseBody
    public ResponseEntity<?> updateProfileAjax(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "barNumber", required = false) String barNumber,
            Authentication authentication
    ) {
        // B23 FIX : Validation des entrées (AJAX)
        String validationError = validateProfileFields(firstName, lastName, phone, barNumber);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", validationError));
        }
        try {
            User user = getCurrentUser(authentication);
            user.setFirstName(firstName.trim());
            user.setLastName(lastName.trim());
            user.setPhone(phone != null ? phone.trim() : null);
            user.setBarNumber(barNumber != null ? barNumber.trim() : null);
            user.setName(firstName.trim() + " " + lastName.trim());
            
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
     * Changement de mot de passe
     */
    @PostMapping("/password")
    @Transactional
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                redirectAttributes.addFlashAttribute("error", "Mot de passe actuel incorrect.");
                return "redirect:/settings";
            }
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Les nouveaux mots de passe ne correspondent pas.");
                return "redirect:/settings";
            }
            if (newPassword.length() < 8) {
                redirectAttributes.addFlashAttribute("error", "Le nouveau mot de passe doit comporter au moins 8 caractères.");
                return "redirect:/settings";
            }
            // SEC-15 FIX : Mêmes exigences de complexité que l'inscription
            if (!PasswordValidator.isValid(newPassword)) {
                redirectAttributes.addFlashAttribute("error", PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE);
                return "redirect:/settings";
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("message", "Mot de passe modifié avec succès.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur : " + e.getMessage());
        }
        return "redirect:/settings";
    }

    /**
     * Récupérer les données utilisateur pour le modal
     */
    @GetMapping("/user-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserData(Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            Map<String, Object> userData = new java.util.LinkedHashMap<>();
            userData.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
            userData.put("lastName", user.getLastName() != null ? user.getLastName() : "");
            userData.put("email", user.getEmail() != null ? user.getEmail() : "");
            userData.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userData.put("barNumber", user.getBarNumber() != null ? user.getBarNumber() : "");
            userData.put("subscriptionPlan", user.getSubscriptionPlan() != null ? user.getSubscriptionPlan().getDisplayName() : "FREE");
            userData.put("maxClients", user.getMaxClients());
            userData.put("yousignConfigured", settingsService.isYousignConfigured(user.getId()));
            String apiKey = settingsService.getYousignApiKey(user.getId());
            // Masquer la clé API — n'envoyer que les 4 derniers caractères
            String maskedKey = "";
            if (apiKey != null && !apiKey.isEmpty()) {
                maskedKey = apiKey.length() > 4
                    ? "••••" + apiKey.substring(apiKey.length() - 4)
                    : "••••";
            }
            userData.put("yousignApiKey", maskedKey);
            return ResponseEntity.ok(userData);
        } catch (Exception e) {
            Map<String, Object> errMap = new java.util.LinkedHashMap<>();
            errMap.put("error", e.getMessage() != null ? e.getMessage() : "Erreur inconnue");
            return ResponseEntity.badRequest().body(errMap);
        }
    }

    /**
     * Changement de mot de passe via AJAX (depuis le modal Paramètres)
     */
    @PostMapping("/change-password")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> changePasswordAjax(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Mot de passe actuel incorrect."
                ));
            }
            if (newPassword.length() < 8) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Le nouveau mot de passe doit comporter au moins 8 caractères."
                ));
            }
            // SEC-15 FIX : Mêmes exigences de complexité que l'inscription
            if (!PasswordValidator.isValid(newPassword)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE
                ));
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mot de passe modifié avec succès !"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Erreur : " + e.getMessage()
            ));
        }
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    // B23 FIX : Validation des champs profil
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}\\s'-]{1,100}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s.-]{0,20}$");
    private static final Pattern BAR_PATTERN = Pattern.compile("^[A-Za-z0-9\\s-]{0,50}$");

    private String validateProfileFields(String firstName, String lastName, String phone, String barNumber) {
        if (firstName == null || firstName.isBlank() || firstName.length() > 100) {
            return "Le prénom est obligatoire (max 100 caractères).";
        }
        if (!NAME_PATTERN.matcher(firstName.trim()).matches()) {
            return "Le prénom contient des caractères invalides.";
        }
        if (lastName == null || lastName.isBlank() || lastName.length() > 100) {
            return "Le nom est obligatoire (max 100 caractères).";
        }
        if (!NAME_PATTERN.matcher(lastName.trim()).matches()) {
            return "Le nom contient des caractères invalides.";
        }
        if (phone != null && !phone.isBlank() && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            return "Numéro de téléphone invalide.";
        }
        if (barNumber != null && !barNumber.isBlank() && !BAR_PATTERN.matcher(barNumber.trim()).matches()) {
            return "Numéro de barreau invalide.";
        }
        return null; // OK
    }
}