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
import java.util.Optional;
import java.util.UUID;

/**
 * Service de réinitialisation de mot de passe par lien tokenisé.
 * Les tokens sont persistés en base (résistent aux redémarrages serveur).
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

    private static final int TOKEN_EXPIRY_HOURS = 1;

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Crée un token de réinitialisation (persisté en base) et envoie un email.
     * Ne révèle pas si l'email existe (sécurité).
     */
    @Transactional
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase());
        if (userOpt.isEmpty()) {
            log.warn("[PasswordReset] Tentative pour email inexistant (masqué)");
            return;
        }
        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        userRepository.save(user);
        sendResetEmail(email, token);
        log.info("[PasswordReset] Token créé (DB) pour utilisateur ID: {}", user.getId());
    }

    /**
     * Valide un token de réinitialisation (recherche en base).
     * @return l'email associé, ou null si invalide/expiré
     */
    public String validateToken(String token) {
        if (token == null) return null;
        return userRepository.findByResetToken(token)
                .filter(u -> u.getResetTokenExpiry() != null
                          && LocalDateTime.now().isBefore(u.getResetTokenExpiry()))
                .map(User::getEmail)
                .orElse(null);
    }

    /**
     * Réinitialise le mot de passe, invalide le token, marque l'email comme vérifié.
     * @return true si succès, false sinon
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<User> userOpt = userRepository.findByResetToken(token);
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        if (user.getResetTokenExpiry() == null || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            // Token expiré — nettoyer
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.save(user);
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("[PasswordReset] Mot de passe réinitialisé pour utilisateur ID: {}", user.getId());
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
            msg.setSubject("Réinitialisation de votre mot de passe — DocAvocat");
            msg.setText(
                "Bonjour,\n\n" +
                "Vous avez demandé à réinitialiser votre mot de passe sur DocAvocat.\n\n" +
                "Cliquez sur ce lien pour créer un nouveau mot de passe :\n\n" +
                "    " + link + "\n\n" +
                "Ce lien est valable " + TOKEN_EXPIRY_HOURS + " heure(s).\n\n" +
                "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.\n\n" +
                "L'équipe DocAvocat\n" + baseUrl
            );
            mailSender.send(msg);
            log.info("[PasswordReset] Email de réinitialisation envoyé");
        } catch (Exception e) {
            log.warn("[PasswordReset] Impossible d'envoyer l'email de réinitialisation : {}", e.getMessage());
        }
    }
}
