package com.gedavocat.controller;

import com.gedavocat.model.Appointment;
import com.gedavocat.model.Client;
import com.gedavocat.model.Signature;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.SignatureRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur pour les fonctionnalités client hors /my-cases :
 * rendez-vous, signatures, changement de mot de passe.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class ClientFeaturesController {

    private final AppointmentService appointmentService;
    private final SignatureRepository signatureRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================================================================
    // Rendez-vous
    // =========================================================================

    @GetMapping("/my-appointments")
    @Transactional(readOnly = true)
    public String myAppointments(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            model.addAttribute("errorMessage",
                    "Votre profil client n'a pas encore été activé.");
            return "client-portal/pending";
        }
        Client client = clientOpt.get();

        List<Appointment> appointments;
        try {
            appointments = appointmentService.getAppointmentsByClient(client.getId());
            // Force-initialiser les proxies lazy (open-in-view=false)
            for (Appointment a : appointments) {
                if (a.getClient() != null) a.getClient().getName();
                if (a.getRelatedCase() != null) a.getRelatedCase().getName();
                if (a.getLawyer() != null) a.getLawyer().getFirstName();
            }
        } catch (Exception e) {
            appointments = Collections.emptyList();
        }

        model.addAttribute("appointments", appointments);
        model.addAttribute("client", client);
        model.addAttribute("user", user);
        return "client-portal/appointments";
    }

    // =========================================================================
    // Signatures
    // =========================================================================

    @GetMapping("/my-signatures")
    public String mySignatures(Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        var clientOpt = clientRepository.findByClientUserId(user.getId());
        if (clientOpt.isEmpty()) {
            model.addAttribute("errorMessage",
                    "Votre profil client n'a pas encore été activé.");
            return "client-portal/pending";
        }

        // Les signatures adressées au client par email
        List<Signature> signatures;
        try {
            signatures = signatureRepository.findBySignerEmail(user.getEmail());
        } catch (Exception e) {
            signatures = Collections.emptyList();
        }

        model.addAttribute("signatures", signatures);
        model.addAttribute("user", user);
        return "client-portal/signatures";
    }

    // =========================================================================
    // Changement de mot de passe (profil client)
    // =========================================================================

    @PostMapping("/my-cases/profile/change-password")
    @ResponseBody
    public Map<String, Object> changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmNewPassword,
            Authentication authentication
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            User user = getCurrentUser(authentication);

            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                result.put("success", false);
                result.put("message", "Le mot de passe actuel est incorrect.");
                return result;
            }
            if (newPassword.length() < 8) {
                result.put("success", false);
                result.put("message", "Le nouveau mot de passe doit contenir au moins 8 caractères.");
                return result;
            }
            if (!newPassword.equals(confirmNewPassword)) {
                result.put("success", false);
                result.put("message", "Les mots de passe ne correspondent pas.");
                return result;
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            result.put("success", true);
            result.put("message", "Votre mot de passe a été modifié avec succès.");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Erreur : " + e.getMessage());
        }
        return result;
    }

    // =========================================================================

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
