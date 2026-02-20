package com.gedavocat.service;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de réinitialisation de mot de passe par lien tokenisé.
 * Les tokens expirent après 1 heure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@docavocat.fr}")
    private String fromEmail;

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    /** Map token → ResetEntry */
    private final Map<String, ResetEntry> pendingTokens = new ConcurrentHashMap<>();

    private static final int TOKEN_EXPIRY_HOURS = 1;

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Crée un token de réinitialisation et envoie un email avec le lien.
     * Ne révèle pas si l'email existe (sécurité).
     */
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase());
        if (userOpt.isEmpty()) {
            // Ne pas révéler si l'email existe
            log.warn("[PasswordReset] Tentative pour email inexistant : {}", email);
            return;
        }
        String token = UUID.randomUUID().toString();
        pendingTokens.put(token, new ResetEntry(email.toLowerCase(), LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS)));
        sendResetEmail(email, token);
        log.info("[PasswordReset] Token créé pour {}", email);
    }

    /**
     * Valide un token de réinitialisation.
     * @return l'email associé, ou null si invalide/expiré
     */
    public String validateToken(String token) {
        if (token == null) return null;
        ResetEntry entry = pendingTokens.get(token);
        if (entry == null) return null;
        if (LocalDateTime.now().isAfter(entry.expiry())) {
            pendingTokens.remove(token);
            return null;
        }
        return entry.email();
    }

    /**
     * Réinitialise le mot de passe, invalide le token, marque l'email comme vérifié.
     * @return true si succès, false sinon
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        String email = validateToken(token);
        if (email == null) return false;

        userRepository.findByEmail(email).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setEmailVerified(true); // Réinitialiser le mot de passe implique la vérification de l'email
            userRepository.save(user);
            log.info("[PasswordReset] Mot de passe réinitialisé pour {}", email);
        });

        pendingTokens.remove(token);
        return true;
    }

    // =========================================================================
    // Envoi email
    // =========================================================================

    private void sendResetEmail(String to, String token) {
        try {
            String link = baseUrl + "/reset-password?token=" + token;
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject("Réinitialisation de votre mot de passe GedAvocat");
            msg.setText(
                "Bonjour,\n\n" +
                "Vous avez demandé à réinitialiser votre mot de passe sur GedAvocat.\n\n" +
                "Cliquez sur ce lien pour créer un nouveau mot de passe :\n\n" +
                "    " + link + "\n\n" +
                "Ce lien est valable " + TOKEN_EXPIRY_HOURS + " heure(s).\n\n" +
                "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n" +
                "L'équipe GedAvocat\n" + baseUrl
            );
            mailSender.send(msg);
            log.info("[PasswordReset] Email envoyé à {}", to);
        } catch (Exception e) {
            log.warn("[PasswordReset] Impossible d'envoyer l'email à {} : {}", to, e.getMessage());
        }
    }

    // =========================================================================
    // Classe interne
    // =========================================================================

    private record ResetEntry(String email, LocalDateTime expiry) {}
}
