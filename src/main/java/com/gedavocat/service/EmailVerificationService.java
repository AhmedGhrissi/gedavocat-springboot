package com.gedavocat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
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
    /** SEC-RATELIMIT FIX : compteur de tentatives échouées par email */
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Génère un code à 6 chiffres, le stocke et envoie un email.
     * @return le code généré (utile pour les tests / logs)
     */
    public String generateAndSend(String email) {
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        pendingCodes.put(email.toLowerCase(), new VerificationEntry(code, LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES)));
        sendEmail(email, code);
        log.info("[EmailVerification] Code généré pour {} (expire dans {} min)", email, CODE_EXPIRY_MINUTES);
        return code;
    }

    /**
     * Vérifie le code fourni par l'utilisateur.
     * SEC-RATELIMIT FIX : bloque après MAX_FAILED_ATTEMPTS tentatives échouées
     * @return true si valide et non expiré, false sinon
     */
    public boolean verifyCode(String email, String code) {
        String emailLower = email.toLowerCase();
        
        // Vérifier le nombre de tentatives échouées
        int attempts = failedAttempts.getOrDefault(emailLower, 0);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            log.warn("[EmailVerification] Trop de tentatives échouées pour {} — code invalidé", emailLower);
            pendingCodes.remove(emailLower);
            failedAttempts.remove(emailLower);
            return false;
        }
        
        VerificationEntry entry = pendingCodes.get(emailLower);
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.expiry())) {
            pendingCodes.remove(emailLower);
            failedAttempts.remove(emailLower);
            return false;
        }
        if (!entry.code().equals(code)) {
            failedAttempts.put(emailLower, failedAttempts.getOrDefault(emailLower, 0) + 1);
            log.warn("[EmailVerification] Tentative échouée pour {} (tentative {}/{})", 
                     emailLower, attempts + 1, MAX_FAILED_ATTEMPTS);
            return false;
        }
        pendingCodes.remove(emailLower);
        failedAttempts.remove(emailLower);
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
            // SEC FIX M-05 : ne pas inclure le code dans le sujet de l'email
            msg.setSubject("Confirmation de votre compte DocAvocat");
            msg.setText(
                "Bonjour,\n\n" +
                "Votre code de vérification pour DocAvocat est :\n\n" +
                "    " + code + "\n\n" +
                "Ce code est valable " + CODE_EXPIRY_MINUTES + " minutes.\n\n" +
                "Si vous n'avez pas créé de compte sur docavocat.fr, ignorez cet email.\n\n" +
                "L'équipe DocAvocat\n" + baseUrl
            );
            mailSender.send(msg);
            log.info("[EmailVerification] Email envoyé à {}", to);
        } catch (Exception e) {
            log.error("[EmailVerification] Échec d'envoi de l'email à {} : {}", to, e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de vérification. Veuillez réessayer.", e);
        }
    }

    // =========================================================================
    // Classe interne
    // =========================================================================

    private record VerificationEntry(String code, LocalDateTime expiry) {}
}
