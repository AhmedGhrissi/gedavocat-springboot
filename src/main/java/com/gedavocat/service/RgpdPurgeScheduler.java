package com.gedavocat.service;

import com.gedavocat.repository.AuditLogRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SEC RGPD F-11 — Purge automatique des données personnelles (Art. 5 RGPD)
 *
 * Deux tâches planifiées :
 * 1. Anonymisation des adresses IP dans les logs d'audit antérieurs à 90 jours
 *    (minimisation des données, Art. 5.1.c RGPD)
 * 2. Suppression des tokens de réinitialisation de mot de passe expirés
 *    (données sensibles non nécessaires, Art. 5.1.e RGPD)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RgpdPurgeScheduler {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Anonymise les adresses IP dans les logs d'audit de plus de 90 jours.
     * Exécuté chaque nuit à 02h00 (heure serveur).
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void anonymizeOldAuditLogIps() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int updated = auditLogRepository.anonymizeOldIpAddresses(cutoff);
        if (updated > 0) {
            log.info("[RGPD] Purge automatique : {} adresse(s) IP anonymisée(s) (logs > 90j)", updated);
        }
    }

    /**
     * Supprime les tokens de réinitialisation de mot de passe expirés.
     * Exécuté toutes les heures pour minimiser la fenêtre d'exposition.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void clearExpiredPasswordResetTokens() {
        LocalDateTime cutoff = LocalDateTime.now();
        int cleared = userRepository.clearExpiredResetTokens(cutoff);
        if (cleared > 0) {
            log.info("[RGPD] Purge automatique : {} token(s) de réinitialisation expiré(s) supprimé(s)", cleared);
        }
    }

    /**
     * SEC FIX N-13 : Désactive les comptes inactifs depuis plus de 24 mois (Art. 5.1.e RGPD).
     * Exécuté chaque nuit à 03h00 (heure serveur).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void disableInactiveAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(720); // 24 mois
        int disabled = userRepository.disableInactiveAccounts(cutoff);
        if (disabled > 0) {
            log.warn("[RGPD] Comptes inactifs désactivés : {} compte(s) sans connexion depuis >24 mois", disabled);
        }
    }
}
