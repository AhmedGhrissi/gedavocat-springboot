package com.gedavocat.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEC FIX L-04 : Service de verrouillage de compte après N tentatives de connexion échouées.
 * Verrouille un email pendant 15 minutes après 5 tentatives échouées.
 * Stockage en mémoire (suffisant pour une instance unique).
 */
@Service
public class AccountLockoutService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MILLIS = 15 * 60 * 1000L; // 15 minutes

    private final Map<String, LockoutEntry> lockoutMap = new ConcurrentHashMap<>();

    /**
     * Vérifie si un compte est verrouillé.
     */
    public boolean isLocked(String email) {
        if (email == null) return false;
        LockoutEntry entry = lockoutMap.get(email.toLowerCase());
        if (entry == null) return false;
        if (entry.lockedUntil > 0 && System.currentTimeMillis() < entry.lockedUntil) {
            return true;
        }
        // Lockout expiré : réinitialiser
        if (entry.lockedUntil > 0 && System.currentTimeMillis() >= entry.lockedUntil) {
            lockoutMap.remove(email.toLowerCase());
        }
        return false;
    }

    /**
     * Enregistre un échec de connexion.
     */
    public void recordFailedAttempt(String email) {
        if (email == null) return;
        String key = email.toLowerCase();
        LockoutEntry entry = lockoutMap.computeIfAbsent(key, k -> new LockoutEntry());
        synchronized (entry) {
            entry.failedAttempts++;
            if (entry.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                entry.lockedUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MILLIS;
            }
        }
    }

    /**
     * Réinitialise les tentatives après une connexion réussie.
     */
    public void resetAttempts(String email) {
        if (email == null) return;
        lockoutMap.remove(email.toLowerCase());
    }

    /**
     * Retourne les minutes restantes de verrouillage.
     */
    public long getRemainingLockoutMinutes(String email) {
        if (email == null) return 0;
        LockoutEntry entry = lockoutMap.get(email.toLowerCase());
        if (entry == null || entry.lockedUntil <= 0) return 0;
        long remaining = entry.lockedUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 60000) + 1 : 0;
    }

    private static class LockoutEntry {
        volatile int failedAttempts = 0;
        volatile long lockedUntil = 0;
    }
}
