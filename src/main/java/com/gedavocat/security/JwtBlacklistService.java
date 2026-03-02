package com.gedavocat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEC-01 FIX : Service de blacklist JWT pour permettre la révocation de tokens.
 * Les tokens blacklistés sont stockés en mémoire avec nettoyage automatique
 * des entrées expirées toutes les 15 minutes.
 *
 * En production multi-instance, remplacer par un store partagé (Redis, base de données).
 */
@Slf4j
@Service
public class JwtBlacklistService {

    /**
     * Map token -> date d'expiration du token.
     * On conserve la date d'expiration pour pouvoir nettoyer les entrées obsolètes.
     */
    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Blackliste un token JWT (appelé lors du logout).
     *
     * @param token          le JWT à blacklister
     * @param expirationDate la date d'expiration du token (pour nettoyage automatique)
     */
    public void blacklist(String token, Date expirationDate) {
        if (token != null && !token.isBlank()) {
            blacklistedTokens.put(token, expirationDate != null ? expirationDate : new Date());
            log.debug("Token JWT blacklisté (total: {})", blacklistedTokens.size());
        }
    }

    /**
     * Vérifie si un token est blacklisté.
     */
    public boolean isBlacklisted(String token) {
        return token != null && blacklistedTokens.containsKey(token);
    }

    /**
     * Nettoyage automatique des tokens expirés toutes les 15 minutes.
     * Un token expiré n'a plus besoin d'être dans la blacklist car il serait
     * rejeté de toute façon par la validation JWT.
     */
    @Scheduled(fixedRate = 900_000) // 15 minutes
    public void cleanExpiredTokens() {
        Date now = new Date();
        int before = blacklistedTokens.size();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().before(now));
        int removed = before - blacklistedTokens.size();
        if (removed > 0) {
            log.info("Nettoyage blacklist JWT : {} tokens expirés supprimés, {} restants",
                    removed, blacklistedTokens.size());
        }
    }
}
