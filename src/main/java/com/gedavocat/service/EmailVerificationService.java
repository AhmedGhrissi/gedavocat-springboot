package com.gedavocat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de vérification d'email par code à 6 chiffres.
 * Les codes expirent après 15 minutes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@docavocat.fr}")
    private String fromEmail;

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    /** Map email → VerificationEntry */
    private final Map<String, VerificationEntry> pendingCodes = new ConcurrentHashMap<>();

    private static final int CODE_EXPIRY_MINUTES = 15;

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Génère un code à 6 chiffres, le stocke et envoie un email.
     * @return le code généré (utile pour les tests / logs)
     */
    public String generateAndSend(String email) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        pendingCodes.put(email.toLowerCase(), new VerificationEntry(code, LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES)));
        sendEmail(email, code);
        log.info("[EmailVerification] Code généré pour {} (expire dans {} min)", email, CODE_EXPIRY_MINUTES);
        return code;
    }

    /**
     * Vérifie le code fourni par l'utilisateur.
     * @return true si valide et non expiré, false sinon
     */
    public boolean verifyCode(String email, String code) {
        VerificationEntry entry = pendingCodes.get(email.toLowerCase());
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiry())) {
            pendingCodes.remove(email.toLowerCase());
            return false;
        }
        if (!entry.code().equals(code)) return false;
        pendingCodes.remove(email.toLowerCase());
        return true;
    }

    /** Indique si un code est en attente pour cet email. */
    public boolean hasPendingCode(String email) {
        VerificationEntry entry = pendingCodes.get(email.toLowerCase());
        return entry != null && LocalDateTime.now().isBefore(entry.expiry());
    }

    // =========================================================================
    // Envoi email
    // =========================================================================

    private void sendEmail(String to, String code) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject("Confirmation de votre compte GedAvocat - Code " + code);
            msg.setText(
                "Bonjour,\n\n" +
                "Votre code de vérification pour GedAvocat est :\n\n" +
                "    " + code + "\n\n" +
                "Ce code est valable " + CODE_EXPIRY_MINUTES + " minutes.\n\n" +
                "Si vous n'avez pas créé de compte sur docavocat.fr, ignorez cet email.\n\n" +
                "L'équipe GedAvocat\n" + baseUrl
            );
            mailSender.send(msg);
            log.info("[EmailVerification] Email envoyé à {}", to);
        } catch (Exception e) {
            log.warn("[EmailVerification] Impossible d'envoyer l'email à {} : {}", to, e.getMessage());
        }
    }

    // =========================================================================
    // Classe interne
    // =========================================================================

    private record VerificationEntry(String code, LocalDateTime expiry) {}
}
