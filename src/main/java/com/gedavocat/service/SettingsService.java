package com.gedavocat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des paramètres
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SettingsService {

    // Stockage temporaire des paramètres (en production, utiliser une base de données)
    private final Map<String, String> userSettings = new ConcurrentHashMap<>();
    private final Map<String, Boolean> yousignSandbox = new ConcurrentHashMap<>();

    /**
     * Récupère la clé API Yousign d'un utilisateur
     */
    public String getYousignApiKey(String userId) { // gitleaks:allow
        return userSettings.get("yousign_api_key_" + userId); // gitleaks:allow
    }

    /**
     * Vérifie si Yousign est configuré pour un utilisateur
     */
    public boolean isYousignConfigured(String userId) {
        String apiKey = getYousignApiKey(userId); // gitleaks:allow
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Sauvegarde les paramètres Yousign
     */
    public void saveYousignSettings(String userId, String apiKey, boolean sandbox) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            userSettings.put("yousign_api_key_" + userId, apiKey.trim()); // gitleaks:allow
            yousignSandbox.put("yousign_sandbox_" + userId, sandbox);
        } else {
            // Supprimer la configuration si la clé est vide
            userSettings.remove("yousign_api_key_" + userId); // gitleaks:allow
            yousignSandbox.remove("yousign_sandbox_" + userId);
        }
    }

    /**
     * Teste la connexion Yousign
     */
    public boolean testYousignConnection(String userId) {
        String apiKey = getYousignApiKey(userId);
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        try {
            // Simulation d'un test de connexion Yousign
            // En production, faire un appel réel à l'API Yousign
            
            // Test de format de la clé API (simulation)
            if (apiKey.length() < 10) {
                return false;
            }
            
            // Si la clé commence par "test_" ou "prod_", considérer comme valide
            return apiKey.startsWith("test_") || apiKey.startsWith("prod_") || apiKey.length() >= 20;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Vérifie si l'utilisateur utilise le mode sandbox
     */
    public boolean isYousignSandbox(String userId) {
        return yousignSandbox.getOrDefault("yousign_sandbox_" + userId, true); // Par défaut sandbox
    }

    /**
     * Récupère tous les paramètres d'un utilisateur
     */
    public Map<String, Object> getUserSettings(String userId) {
        Map<String, Object> settings = new ConcurrentHashMap<>();
        
        String apiKey = getYousignApiKey(userId);
        if (apiKey != null) {
            // Masquer une partie de la clé pour la sécurité
            String maskedKey = apiKey.substring(0, Math.min(4, apiKey.length())) + 
                             "*".repeat(Math.max(0, apiKey.length() - 8)) + 
                             (apiKey.length() > 4 ? apiKey.substring(Math.max(4, apiKey.length() - 4)) : "");
            settings.put("yousignApiKeyMasked", maskedKey);
        }
        
        settings.put("yousignConfigured", isYousignConfigured(userId));
        settings.put("yousignSandbox", isYousignSandbox(userId));
        
        return settings;
    }
}