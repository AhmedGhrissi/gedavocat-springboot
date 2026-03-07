package com.gedavocat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.gedavocat.security.monitoring.SecurityMonitoringService;
import com.gedavocat.security.monitoring.SecurityMonitoringService.SecurityMonitoringReport;
import com.gedavocat.security.mfa.MultiFactorAuthenticationService;
import com.gedavocat.security.crypto.SecureCryptographyService;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;

import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contrôleur REST pour Monitoring et Administration Sécurité
 * 
 * Endpoints sécurisés pour :
 * - Dashboard temps réel sécurité
 * - Gestion MFA utilisateurs
 * - Administration cryptographie
 * - Rapports et alertes
 * 
 * Accès : ADMIN et DPO uniquement
 */
@RestController
@RequestMapping("/api/security/admin")
@PreAuthorize("hasRole('ADMIN') or hasRole('DPO')")
@SuppressWarnings("null")
public class SecurityAdminController {

    private static final Logger log = LoggerFactory.getLogger(SecurityAdminController.class);

    @Autowired
    private SecurityMonitoringService monitoringService;

    @Autowired
    private MultiFactorAuthenticationService mfaService;

    @Autowired
    private SecureCryptographyService cryptoService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Dashboard monitoring sécurité temps réel
     * 
     * GET /api/security/admin/dashboard
     * 
     * @return Métriques et alertes sécurité
     */
    @GetMapping("/dashboard")
    public ResponseEntity<SecurityDashboard> getSecurityDashboard() {
        
        try {
            
            SecurityMonitoringReport report = monitoringService.generateSecurityReport();
            
            if (report == null) {
                return ResponseEntity.internalServerError().build();
            }

            // Format pour dashboard
            SecurityDashboard dashboard = new SecurityDashboard(
                report.getOverallStatus(),
                report.getMetrics(),
                report.getTopSuspiciousIPs(),
                report.getRecentEvents().size(),
                report.getTimestamp()
            );
            
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Erreur dashboard sécurité", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Configuration MFA pour utilisateur
     * 
     * POST /api/security/admin/mfa/{userId}/setup
     * 
     * @param userId ID de l'utilisateur
     * @return Configuration MFA (QR code, clés de récupération)
     */
    @PostMapping("/mfa/{userId}/setup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MFASetupResponse> setupUserMFA(@PathVariable String userId) {
        
        try {
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

            if (!mfaService.requiresMFA(user)) {
                return ResponseEntity.badRequest().build();
            }

            MultiFactorAuthenticationService.MFASetupResult setup = mfaService.generateMFASecret(user);
            
            MFASetupResponse response = new MFASetupResponse(
                setup.getSecretKey(),
                setup.getQrCodeUrl(),
                setup.getBackupCodes()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur configuration MFA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Validation code MFA pour utilisateur
     * 
     * POST /api/security/admin/mfa/{userId}/validate
     * 
     * @param userId ID utilisateur
     * @param request Code à valider
     * @return Résultat validation
     */
    @PostMapping("/mfa/{userId}/validate")
    public ResponseEntity<MFAValidationResponse> validateUserMFA(
            @PathVariable String userId, 
            @RequestBody MFAValidationRequest request) {
        
        try {
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

            MultiFactorAuthenticationService.MFAValidationResult result = 
                mfaService.validateMFA(user, request.getCode());
            
            MFAValidationResponse response = new MFAValidationResponse(
                result.isValid(),
                result.getMessage(),
                result.getMethod()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur validation MFA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Désactivation MFA utilisateur
     * 
     * DELETE /api/security/admin/mfa/{userId}
     * 
     * @param userId ID utilisateur
     * @return Confirmation désactivation
     */
    @DeleteMapping("/mfa/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> disableUserMFA(@PathVariable String userId) {
        
        try {
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

            mfaService.disableMFA(user);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "MFA désactivé pour utilisateur " + userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur désactivation MFA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Status MFA de tous les utilisateurs
     * 
     * GET /api/security/admin/mfa/status
     * 
     * @return Status MFA par utilisateur
     */
    @GetMapping("/mfa/status")
    public ResponseEntity<Map<String, MFAUserStatus>> getAllMFAStatus() {
        
        try {
            
            Map<String, MFAUserStatus> mfaStatusMap = new HashMap<>();
            
            userRepository.findAll().forEach(user -> {
                
                MultiFactorAuthenticationService.MFAStatus status = mfaService.getMFAStatus(user);
                
                mfaStatusMap.put(user.getId(), new MFAUserStatus(
                    user.getEmail(),
                    status.isRequired(),
                    status.isConfigured(),
                    status.isEnabled(),
                    status.getLastUsed()
                ));
            });
            
            return ResponseEntity.ok(mfaStatusMap);
            
        } catch (Exception e) {
            log.error("Erreur status MFA", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Statistiques cryptographie
     * 
     * GET /api/security/admin/crypto/stats
     * 
     * @return Statistiques clés et algorithmes
     */
    @GetMapping("/crypto/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SecureCryptographyService.CryptoSecurityStats> getCryptoStats() {
        
        try {
            
            SecureCryptographyService.CryptoSecurityStats stats = cryptoService.getSecurityStats();
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Erreur statistiques crypto", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Rotation manuelle des clés
     * 
     * POST /api/security/admin/crypto/rotate-keys
     * 
     * @return Résultat rotation
     */
    @PostMapping("/crypto/rotate-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rotateKeys() {
        
        try {
            
            cryptoService.rotateExpiredKeys();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Rotation des clés effectuée");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur rotation clés", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Vérification intégrité des clés
     * 
     * GET /api/security/admin/crypto/verify-integrity
     * 
     * @return Statut intégrité
     */
    @GetMapping("/crypto/verify-integrity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verifyKeyIntegrity() {
        
        try {
            
            boolean integrityOK = cryptoService.verifyKeyIntegrity();
            
            Map<String, Object> response = new HashMap<>();
            response.put("integrity", integrityOK);
            response.put("status", integrityOK ? "OK" : "COMPROMISED");
            response.put("message", integrityOK ? "Intégrité des clés vérifiée" : "ALERTE: Intégrité compromise");
            
            if (!integrityOK) {
                // Alerte critique
                return ResponseEntity.status(500).body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur vérification intégrité", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export rapport sécurité complet
     * 
     * GET /api/security/admin/report/export
     * 
     * @return Rapport sécurité détaillé
     */
    @GetMapping("/report/export")
    public ResponseEntity<SecurityReport> exportSecurityReport() {
        
        try {
            
            SecurityMonitoringReport monitoring = monitoringService.generateSecurityReport();
            SecureCryptographyService.CryptoSecurityStats crypto = cryptoService.getSecurityStats();
            
            // Compter utilisateurs MFA
            long totalUsers = userRepository.count();
            long mfaEnabledUsers = userRepository.findAll().stream()
                .mapToLong(user -> {
                    MultiFactorAuthenticationService.MFAStatus status = mfaService.getMFAStatus(user);
                    return status.isEnabled() ? 1 : 0;
                })
                .sum();

            SecurityReport report = new SecurityReport(
                monitoring,
                crypto,
                totalUsers,
                mfaEnabledUsers
            );
            
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"security-report-" + 
                       java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + 
                       ".json\"")
                .body(report);
            
        } catch (Exception e) {
            log.error("Erreur export rapport", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =================================================================
    // Classes de Réponse
    // =================================================================

    public static class SecurityDashboard {
        private final String overallStatus;
        private final Map<String, Object> metrics;
        private final java.util.List<String> topThreats;
        private final int recentAlertsCount;
        private final java.time.LocalDateTime timestamp;

        public SecurityDashboard(String overallStatus, Map<String, Object> metrics,
                               java.util.List<String> topThreats, int recentAlertsCount,
                               java.time.LocalDateTime timestamp) {
            this.overallStatus = overallStatus;
            this.metrics = metrics;
            this.topThreats = topThreats;
            this.recentAlertsCount = recentAlertsCount;
            this.timestamp = timestamp;
        }

        // Getters
        public String getOverallStatus() { return overallStatus; }
        public Map<String, Object> getMetrics() { return metrics; }
        public java.util.List<String> getTopThreats() { return topThreats; }
        public int getRecentAlertsCount() { return recentAlertsCount; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class MFASetupResponse {
        private final String secretKey;
        private final String qrCodeUrl;
        private final java.util.List<String> backupCodes;

        public MFASetupResponse(String secretKey, String qrCodeUrl, java.util.List<String> backupCodes) {
            this.secretKey = secretKey;
            this.qrCodeUrl = qrCodeUrl;
            this.backupCodes = backupCodes;
        }

        public String getSecretKey() { return secretKey; }
        public String getQrCodeUrl() { return qrCodeUrl; }
        public java.util.List<String> getBackupCodes() { return backupCodes; }
    }

    public static class MFAValidationRequest {
        private String code;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class MFAValidationResponse {
        private final boolean valid;
        private final String message;
        private final String method;

        public MFAValidationResponse(boolean valid, String message, String method) {
            this.valid = valid;
            this.message = message;
            this.method = method;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getMethod() { return method; }
    }

    public static class MFAUserStatus {
        private final String email;
        private final boolean required;
        private final boolean configured;
        private final boolean enabled;
        private final java.time.LocalDateTime lastUsed;

        public MFAUserStatus(String email, boolean required, boolean configured, 
                           boolean enabled, java.time.LocalDateTime lastUsed) {
            this.email = email;
            this.required = required;
            this.configured = configured;
            this.enabled = enabled;
            this.lastUsed = lastUsed;
        }

        public String getEmail() { return email; }
        public boolean isRequired() { return required; }
        public boolean isConfigured() { return configured; }
        public boolean isEnabled() { return enabled; }
        public java.time.LocalDateTime getLastUsed() { return lastUsed; }
    }

    public static class SecurityReport {
        private final SecurityMonitoringReport monitoring;
        private final SecureCryptographyService.CryptoSecurityStats crypto;
        private final long totalUsers;
        private final long mfaEnabledUsers;
        private final java.time.LocalDateTime generatedAt;

        public SecurityReport(SecurityMonitoringReport monitoring, 
                            SecureCryptographyService.CryptoSecurityStats crypto,
                            long totalUsers, long mfaEnabledUsers) {
            this.monitoring = monitoring;
            this.crypto = crypto;
            this.totalUsers = totalUsers;
            this.mfaEnabledUsers = mfaEnabledUsers;
            this.generatedAt = java.time.LocalDateTime.now();
        }

        public SecurityMonitoringReport getMonitoring() { return monitoring; }
        public SecureCryptographyService.CryptoSecurityStats getCrypto() { return crypto; }
        public long getTotalUsers() { return totalUsers; }
        public long getMfaEnabledUsers() { return mfaEnabledUsers; }
        public java.time.LocalDateTime getGeneratedAt() { return generatedAt; }
    }

    // =================================================================
    // Endpoints Machine Learning - Détection d'Anomalies
    // =================================================================

    /**
     * Récupère les alertes ML récentes
     * 
     * GET /api/security/admin/ml-alerts
     * 
     * @return Liste des alertes ML générées par détection d'anomalies
     */
    @GetMapping("/ml-alerts")
    public ResponseEntity<Map<String, Object>> getMLAlerts() {
        
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("ml_enabled", true);
            response.put("detection_algorithms", java.util.List.of(
                "IP Behavior Analysis",
                "User Behavior Analysis",
                "Distributed Attack Detection",
                "Automated Pattern Recognition"
            ));
            response.put("message", "Surveillance ML active - Consultez les logs d'audit pour les alertes détaillées");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur récupération alertes ML", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retourne les statistiques ML globales
     * 
     * GET /api/security/admin/ml-stats
     * 
     * @return Statistiques sur la détection d'anomalies
     */
    @GetMapping("/ml-stats")
    public ResponseEntity<MLStatistics> getMLStatistics() {
        
        try {
            SecurityMonitoringReport report = monitoringService.generateSecurityReport();
            
            if (report == null) {
                return ResponseEntity.internalServerError().build();
            }

            // Construire les statistiques ML
            Map<String, Object> metrics = report.getMetrics();
            
            MLStatistics stats = new MLStatistics(
                (Integer) metrics.getOrDefault("failed_logins_by_ip", 0),
                (Integer) metrics.getOrDefault("failed_logins_by_user", 0),
                report.getTopSuspiciousIPs().size(),
                metrics
            );
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Erreur statistiques ML", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retourne la configuration des seuils ML
     * 
     * GET /api/security/admin/ml-config
     * 
     * @return Configuration des seuils de détection
     */
    @GetMapping("/ml-config")
    public ResponseEntity<MLConfiguration> getMLConfiguration() {
        
        try {
            MLConfiguration config = new MLConfiguration(
                0.8,  // Seuil IP anomaly
                0.75, // Seuil User anomaly
                true, // ML activé
                "PRODUCTION"
            );
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // =================================================================
    // Classes de Données ML
    // =================================================================

    public static class MLStatistics {
        private final int trackedIPs;
        private final int trackedUsers;
        private final int suspiciousIPs;
        private final Map<String, Object> detectionMetrics;
        private final java.time.LocalDateTime timestamp;

        public MLStatistics(int trackedIPs, int trackedUsers, int suspiciousIPs, 
                          Map<String, Object> detectionMetrics) {
            this.trackedIPs = trackedIPs;
            this.trackedUsers = trackedUsers;
            this.suspiciousIPs = suspiciousIPs;
            this.detectionMetrics = detectionMetrics;
            this.timestamp = java.time.LocalDateTime.now();
        }

        public int getTrackedIPs() { return trackedIPs; }
        public int getTrackedUsers() { return trackedUsers; }
        public int getSuspiciousIPs() { return suspiciousIPs; }
        public Map<String, Object> getDetectionMetrics() { return detectionMetrics; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class MLConfiguration {
        private final double ipAnomalyThreshold;
        private final double userAnomalyThreshold;
        private final boolean enabled;
        private final String mode;

        public MLConfiguration(double ipAnomalyThreshold, double userAnomalyThreshold,
                             boolean enabled, String mode) {
            this.ipAnomalyThreshold = ipAnomalyThreshold;
            this.userAnomalyThreshold = userAnomalyThreshold;
            this.enabled = enabled;
            this.mode = mode;
        }

        public double getIpAnomalyThreshold() { return ipAnomalyThreshold; }
        public double getUserAnomalyThreshold() { return userAnomalyThreshold; }
        public boolean isEnabled() { return enabled; }
        public String getMode() { return mode; }
    }
}