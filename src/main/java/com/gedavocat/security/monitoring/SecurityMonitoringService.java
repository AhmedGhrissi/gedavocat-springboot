package com.gedavocat.security.monitoring;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import com.gedavocat.service.AuditService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

/**
 * Service de Monitoring et Alertes Sécurité Temps Réel
 * 
 * Surveillance continue des événements de sécurité avec détection
 * d'anomalies et alertes automatiques selon standards SOC/SIEM
 * 
 * Fonctionnalités :
 * - Détection tentatives d'intrusion en temps réel
 * - Analyse comportementale utilisateurs
 * - Corrélation d'événements suspects
 * - Alertes multi-canaux (email, webhook, logs)
 * - Dashboard métriques sécurité
 * - Réponse automatique aux incidents
 * 
 * Standards :
 * - NIST Cybersecurity Framework
 * - ISO 27035 (Incident Management)
 * - SANS TOP 20 Critical Controls
 * - MITRE ATT&CK Framework
 */
@Service
public class SecurityMonitoringService {

    @Autowired
    private AuditService auditService;

    @Value("${security.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${security.alerts.email:admin@gedavocat.com}")
    private String alertEmail;

    @Value("${security.alerts.webhook:}")
    private String alertWebhook;

    @Value("${security.thresholds.failed-logins:5}")
    private int failedLoginThreshold;

    @Value("${security.thresholds.suspicious-requests:20}")
    private int suspiciousRequestThreshold;

    // Compteurs temps réel
    private final Map<String, AtomicInteger> failedLoginsByIP = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failedLoginsByUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestCountByIP = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastActivityByUser = new ConcurrentHashMap<>();

    // Cache des alertes pour éviter spam
    private final Set<String> recentAlerts = ConcurrentHashMap.newKeySet();

    // Machine Learning - Profils comportementaux pour détection d'anomalies
    private final Map<String, UserBehaviorProfile> userBehaviorProfiles = new ConcurrentHashMap<>();
    private final Map<String, IPBehaviorProfile> ipBehaviorProfiles = new ConcurrentHashMap<>();

    private final HttpClient httpClient;

    public SecurityMonitoringService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Surveille les tentatives de connexion échouées
     */
    public void monitorFailedLogin(String ipAddress, String userEmail, String reason) {

        if (!monitoringEnabled) return;

        try {
            
            // Incrémenter compteurs
            failedLoginsByIP.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();
            failedLoginsByUser.computeIfAbsent(userEmail, k -> new AtomicInteger(0)).incrementAndGet();

            int ipFailures = failedLoginsByIP.get(ipAddress).get();
            int userFailures = failedLoginsByUser.get(userEmail).get();

            // Audit événement
            auditService.log(
                "FAILED_LOGIN_DETECTED",
                "SecurityEvent",
                ipAddress + "_" + userEmail,
                String.format("IP: %s, User: %s, Reason: %s, IP failures: %d, User failures: %d",
                         ipAddress, userEmail, reason, ipFailures, userFailures),
                "SECURITY_MONITOR"
            );

            // Détection attaque par force brute
            if (ipFailures >= failedLoginThreshold) {
                triggerBruteForceAlert(ipAddress, ipFailures, "IP");
            }

            if (userFailures >= failedLoginThreshold) {
                triggerBruteForceAlert(userEmail, userFailures, "USER");
            }

            // Analyse patterns d'attaque
            analyzeAttackPatterns(ipAddress, userEmail);

        } catch (Exception e) {
            // Log erreur sans faire échouer l'application
            System.err.println("Erreur monitoring failed login: " + e.getMessage());
        }
    }

    /**
     * Surveille l'activité utilisateur suspecte
     */
    public void monitorUserActivity(String userId, String ipAddress, String action, String details) {

        if (!monitoringEnabled) return;

        try {
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastActivity = lastActivityByUser.get(userId);

            // Détection session anormale
            if (lastActivity != null) {
                
                Duration timeSinceLastActivity = Duration.between(lastActivity, now);
                
                // Activité suspecte si > 24h d'inactivité puis soudaine activité
                if (timeSinceLastActivity.toHours() > 24) {
                    
                    triggerSuspiciousActivityAlert(
                        userId, 
                        ipAddress, 
                        "Session après longue inactivité (" + timeSinceLastActivity.toHours() + "h)",
                        action
                    );
                }
            }

            // Détection géolocalisation anormale (simulation)
            if (isAnomalousLocation(ipAddress, userId)) {
                triggerSuspiciousActivityAlert(
                    userId,
                    ipAddress,
                    "Connexion depuis localisation inhabituelle",
                    action
                );
            }

            // Mise à jour dernière activité
            lastActivityByUser.put(userId, now);

            // Monitor requests par IP
            requestCountByIP.computeIfAbsent(ipAddress, k -> new AtomicLong(0)).incrementAndGet();

        } catch (Exception e) {
            System.err.println("Erreur monitoring user activity: " + e.getMessage());
        }
    }

    /**
     * Surveille les requests suspects (rate limiting, payloads malveillants)
     */
    public void monitorHttpRequest(HttpServletRequest request) {

        if (!monitoringEnabled) return;

        try {
            
            String ipAddress = getClientIP(request);
            String userAgent = request.getHeader("User-Agent");
            String uri = request.getRequestURI();
            String method = request.getMethod();

            // Détection payloads malveillants
            if (containsMaliciousPayload(request)) {
                
                triggerMaliciousPayloadAlert(ipAddress, uri, method, userAgent);
            }

            // Détection scanning/enumeration
            if (isScanningBehavior(ipAddress, uri)) {
                
                triggerScanningAlert(ipAddress, uri);
            }

            // Rate limiting check
            long requestCount = requestCountByIP.computeIfAbsent(ipAddress, k -> new AtomicLong(0)).incrementAndGet();
            
            if (requestCount > suspiciousRequestThreshold) {
                triggerRateLimitAlert(ipAddress, requestCount);
            }

        } catch (Exception e) {
            System.err.println("Erreur monitoring HTTP request: " + e.getMessage());
        }
    }

    /**
     * Surveille les accès aux données sensibles
     */
    public void monitorDataAccess(String userId, String dataType, String recordId, String action) {

        if (!monitoringEnabled) return;

        try {
            
            // Audit accès données sensibles
            auditService.log(
                "DATA_ACCESS_" + action.toUpperCase(),
                dataType,
                recordId,
                String.format("User %s accessed %s record %s with action %s", 
                         userId, dataType, recordId, action),
                userId
            );

            // Détection accès anormal aux données
            if (isAbnormalDataAccess(userId, dataType, action)) {
                
                triggerDataAccessAlert(userId, dataType, recordId, action);
            }

        } catch (Exception e) {
            System.err.println("Erreur monitoring data access: " + e.getMessage());
        }
    }

    /**
     * Génération rapport sécurité temps réel
     */
    public SecurityMonitoringReport generateSecurityReport() {

        try {
            
            LocalDateTime now = LocalDateTime.now();
            
            // Statistiques dernières 24h
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("failed_logins_by_ip", failedLoginsByIP.size());
            metrics.put("failed_logins_by_user", failedLoginsByUser.size());
            metrics.put("total_requests", requestCountByIP.values().stream().mapToLong(AtomicLong::get).sum());
            metrics.put("unique_ips", requestCountByIP.size());
            metrics.put("active_users", lastActivityByUser.size());

            // Top IPs suspectes
            List<String> topSuspiciousIPs = failedLoginsByIP.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                    (a, b) -> b.get() - a.get()))
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

            // Événements récents
            List<SecurityEvent> recentEvents = getRecentSecurityEvents();

            return new SecurityMonitoringReport(
                now,
                metrics,
                topSuspiciousIPs,
                recentEvents,
                "OPERATIONAL" // ou WARNING/CRITICAL selon seuils
            );

        } catch (Exception e) {
            System.err.println("Erreur génération rapport sécurité: " + e.getMessage());
            return null;
        }
    }

    // =================================================================
    // Méthodes d'Alertes
    // =================================================================

    @Async
    private void triggerBruteForceAlert(String target, int attempts, String type) {

        String alertKey = "BRUTE_FORCE_" + target;
        
        if (recentAlerts.contains(alertKey)) {
            return; // Éviter spam
        }

        try {
            
            String alertMessage = String.format(
                "🚨 ALERTE SÉCURITÉ - Attaque par Force Brute Détectée\\n\\n" +
                "Type: %s\\n" +
                "Cible: %s\\n" +
                "Tentatives: %d\\n" +
                "Seuil: %d\\n" +
                "Horodatage: %s\\n\\n" +
                "Action recommandée: Bloquer ou surveiller étroitement.",
                type, target, attempts, failedLoginThreshold,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            // Audit alert
            auditService.log(
                "BRUTE_FORCE_ALERT",
                "SecurityAlert",
                alertKey,
                alertMessage,
                "SECURITY_MONITOR"
            );

            // Envoyer alertes
            sendAlert("Attaque Force Brute - " + target, alertMessage);

            // Cache alert pour 1h
            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 3600000); // 1h

        } catch (Exception e) {
            System.err.println("Erreur envoi alerte brute force: " + e.getMessage());
        }
    }

    @Async
    private void triggerSuspiciousActivityAlert(String userId, String ipAddress, String reason, String action) {

        String alertKey = "SUSPICIOUS_" + userId + "_" + ipAddress;
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }

        try {
            
            String alertMessage = String.format(
                "⚠️ ALERTE SÉCURITÉ - Activité Suspecte\\n\\n" +
                "Utilisateur: %s\\n" +
                "IP: %s\\n" +
                "Raison: %s\\n" +
                "Action: %s\\n" +
                "Horodatage: %s",
                userId, ipAddress, reason, action,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            auditService.log(
                "SUSPICIOUS_ACTIVITY_ALERT",
                "SecurityAlert",
                alertKey,
                alertMessage,
                userId
            );

            sendAlert("Activité Suspecte - " + userId, alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 1800000); // 30min

        } catch (Exception e) {
            System.err.println("Erreur alerte activité suspecte: " + e.getMessage());
        }
    }

    @Async
    private void triggerMaliciousPayloadAlert(String ipAddress, String uri, String method, String userAgent) {

        String alertKey = "MALICIOUS_" + ipAddress;
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }

        try {
            
            String alertMessage = String.format(
                "🛡️ ALERTE SÉCURITÉ - Payload Malveillant Détecté\\n\\n" +
                "IP Source: %s\\n" +
                "URI: %s\\n" +
                "Méthode: %s\\n" +
                "User-Agent: %s\\n" +
                "Horodatage: %s",
                ipAddress, uri, method, userAgent,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            auditService.log(
                "MALICIOUS_PAYLOAD_ALERT",
                "SecurityAlert",
                alertKey,
                alertMessage,
                "SECURITY_MONITOR"
            );

            sendAlert("Payload Malveillant - " + ipAddress, alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 3600000);

        } catch (Exception e) {
            System.err.println("Erreur alerte payload malveillant: " + e.getMessage());
        }
    }

    @Async
    private void triggerScanningAlert(String ipAddress, String uri) {
        
        String alertKey = "SCANNING_" + ipAddress;
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }

        try {
            
            String alertMessage = String.format(
                "🔍 ALERTE SÉCURITÉ - Scanning/Enumération Détecté\\n\\n" +
                "IP Source: %s\\n" +
                "URI: %s\\n" +
                "Horodatage: %s",
                ipAddress, uri,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            sendAlert("Scanning Détecté - " + ipAddress, alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 1800000);

        } catch (Exception e) {
            System.err.println("Erreur alerte scanning: " + e.getMessage());
        }
    }

    @Async
    private void triggerRateLimitAlert(String ipAddress, long requestCount) {
        
        String alertKey = "RATE_LIMIT_" + ipAddress;
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }

        try {
            
            String alertMessage = String.format(
                "⚡ ALERTE SÉCURITÉ - Limite de Requêtes Dépassée\\n\\n" +
                "IP Source: %s\\n" +
                "Requêtes: %d\\n" +
                "Seuil: %d\\n" +
                "Horodatage: %s",
                ipAddress, requestCount, suspiciousRequestThreshold,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            sendAlert("Rate Limit - " + ipAddress, alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 900000); // 15min

        } catch (Exception e) {
            System.err.println("Erreur alerte rate limit: " + e.getMessage());
        }
    }

    @Async
    private void triggerDataAccessAlert(String userId, String dataType, String recordId, String action) {
        
        String alertKey = "DATA_ACCESS_" + userId + "_" + dataType;
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }

        try {
            
            String alertMessage = String.format(
                "📊 ALERTE SÉCURITÉ - Accès Données Anormal\\n\\n" +
                "Utilisateur: %s\\n" +
                "Type de données: %s\\n" +
                "Enregistrement: %s\\n" +
                "Action: %s\\n" +
                "Horodatage: %s",
                userId, dataType, recordId, action,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            sendAlert("Accès Données Anormal - " + userId, alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 1800000);

        } catch (Exception e) {
            System.err.println("Erreur alerte accès données: " + e.getMessage());
        }
    }

    @Async
    private void triggerMLAnomalyAlert(String target, String type, double anomalyScore, List<String> reasons) {
        
        String alertKey = "ML_ANOMALY_" + target;
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }
        
        try {
            
            String reasonsText = String.join(", ", reasons);
            String alertMessage = String.format(
                "🤖 ALERTE ML - Anomalie Comportementale Détectée\\n\\n" +
                "Type: %s\\n" +
                "Cible: %s\\n" +
                "Score d'anomalie: %.2f%%\\n" +
                "Raisons: %s\\n" +
                "Horodatage: %s\\n\\n" +
                "Cette alerte est basée sur l'analyse comportementale par machine learning.",
                type, target, anomalyScore * 100, reasonsText,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            auditService.log(
                "ML_ANOMALY_ALERT",
                "SecurityML",
                alertKey,
                alertMessage,
                "SECURITY_MONITOR"
            );

            sendAlert("Anomalie ML - " + target, alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 1800000);

        } catch (Exception e) {
            System.err.println("Erreur alerte ML anomalie: " + e.getMessage());
        }
    }

    @Async
    private void triggerDistributedAttackAlert(long ipCount, long totalFailures) {
        
        String alertKey = "DISTRIBUTED_ATTACK";
        
        if (recentAlerts.contains(alertKey)) {
            return;
        }

        try {
            
            String alertMessage = String.format(
                "🌐 ALERTE SÉCURITÉ - Attaque Distribuée Détectée\\n\\n" +
                "Nombre d'IPs: %d\\n" +
                "Tentatives totales: %d\\n" +
                "Type: Credential Stuffing / DDoS\\n" +
                "Horodatage: %s\\n\\n" +
                "Action recommandée: Activer rate limiting global et blocage géographique.",
                ipCount, totalFailures,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            );

            auditService.log(
                "DISTRIBUTED_ATTACK_ALERT",
                "SecurityAlert",
                alertKey,
                alertMessage,
                "SECURITY_MONITOR"
            );

            sendAlert("Attaque Distribuée", alertMessage);

            recentAlerts.add(alertKey);
            scheduleAlertRemoval(alertKey, 3600000);

        } catch (Exception e) {
            System.err.println("Erreur alerte attaque distribuée: " + e.getMessage());
        }
    }

    // =================================================================
    // Méthodes d'Analyse
    // =================================================================

    private void analyzeAttackPatterns(String ipAddress, String userEmail) {
        
        try {
            // 1. Analyse comportementale ML - Profil IP
            IPBehaviorProfile ipProfile = ipBehaviorProfiles.computeIfAbsent(
                ipAddress, 
                k -> new IPBehaviorProfile()
            );
            ipProfile.recordFailedLogin();
            
            double ipAnomalyScore = ipProfile.calculateAnomalyScore();
            if (ipAnomalyScore > 0.8) { // Seuil 80% = comportement très anormal
                triggerMLAnomalyAlert(ipAddress, "IP", ipAnomalyScore, ipProfile.getAnomalyReasons());
            }
            
            // 2. Analyse comportementale ML - Profil Utilisateur
            UserBehaviorProfile userProfile = userBehaviorProfiles.computeIfAbsent(
                userEmail,
                k -> new UserBehaviorProfile()
            );
            userProfile.recordFailedLogin(ipAddress);
            
            double userAnomalyScore = userProfile.calculateAnomalyScore();
            if (userAnomalyScore > 0.75) { // Seuil 75% pour utilisateurs
                triggerMLAnomalyAlert(userEmail, "USER", userAnomalyScore, userProfile.getAnomalyReasons());
            }
            
            // 3. Détection attaque distribuée coordonnée (DDoS, credential stuffing)
            long recentIPs = failedLoginsByIP.entrySet().stream()
                .filter(entry -> entry.getValue().get() > 0)
                .count();

            if (recentIPs > 10) {
                long totalFailures = failedLoginsByIP.values().stream()
                    .mapToInt(AtomicInteger::get)
                    .sum();
                
                // Pattern d'attaque distribuée si nombreuses IPs avec peu de tentatives chacune
                double averagePerIP = (double) totalFailures / recentIPs;
                if (averagePerIP < 3 && recentIPs > 20) {
                    triggerDistributedAttackAlert(recentIPs, totalFailures);
                }
            }
            
            // 4. Analyse de corrélation temporelle
            if (ipProfile.hasRapidFirePattern()) {
                // Détection pattern automatisé (bot/script)
                auditService.log(
                    "AUTOMATED_ATTACK_DETECTED",
                    "SecurityML",
                    ipAddress,
                    "Pattern automatisé détecté: " + ipProfile.getAttackVelocity() + " req/sec",
                    "SECURITY_MONITOR"
                );
            }
            
        } catch (Exception e) {
            System.err.println("Erreur analyse ML patterns: " + e.getMessage());
        }
    }

    private boolean isAnomalousLocation(String ipAddress, String userId) {
        
        // Simulation géolocalisation - en production utiliser service tiers
        // Vérifier si l'IP est dans une plage inhabituelle pour l'utilisateur
        
        // IPs suspectes (exemple)
        Set<String> suspiciousRanges = Set.of(
            "192.168.", // Réseaux privés depuis internet
            "10.", // Réseaux privés
            "172.16." // Réseaux privés
        );

        return suspiciousRanges.stream().anyMatch(ipAddress::startsWith);
    }

    private boolean containsMaliciousPayload(HttpServletRequest request) {
        
        // Patterns d'attaque communs
        List<String> maliciousPatterns = Arrays.asList(
            "<script", "javascript:", "eval(", "base64",
            "union select", "drop table", "' or '1'='1",
            "../", "..\\\\", "/etc/passwd", "cmd.exe",
            "${jndi:", "{{", "<%", "php://"
        );

        // Vérifier URL
        String uri = request.getRequestURI().toLowerCase();
        String query = request.getQueryString();
        
        for (String pattern : maliciousPatterns) {
            if (uri.contains(pattern) || 
                (query != null && query.toLowerCase().contains(pattern))) {
                return true;
            }
        }

        // Vérifier headers
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName).toLowerCase();
            
            for (String pattern : maliciousPatterns) {
                if (headerValue.contains(pattern)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isScanningBehavior(String ipAddress, String uri) {
        
        // Détecter patterns de scanning
        Set<String> scanningPaths = Set.of(
            "/admin", "/wp-admin", "/phpmyadmin", "/config",
            "/.git", "/.env", "/backup", "/test", "/debug"
        );

        return scanningPaths.stream().anyMatch(uri::contains);
    }

    private boolean isAbnormalDataAccess(String userId, String dataType, String action) {
        
        // Exemples de comportements anormaux
        if ("DELETE".equals(action) && "AUDIT_LOG".equals(dataType)) {
            return true; // Tentative suppression logs
        }

        if ("BULK_EXPORT".equals(action)) {
            return true; // Export massif suspect
        }

        return false;
    }

    // =================================================================
    // Utilitaires
    // =================================================================

    @Async
    private void sendAlert(String subject, String message) {
        
        try {
            
            // Email alert
            sendEmailAlert(subject, message);
            
            // Webhook alert (Slack, Teams, etc.)
            if (alertWebhook != null && !alertWebhook.isEmpty()) {
                sendWebhookAlert(subject, message);
            }

        } catch (Exception e) {
            System.err.println("Erreur envoi alerte: " + e.getMessage());
        }
    }

    private void sendEmailAlert(String subject, String message) {
        
        // Implémentation simplifiée - en production utiliser service email
        System.out.println("EMAIL ALERT TO: " + alertEmail);
        System.out.println("SUBJECT: " + subject);
        System.out.println("MESSAGE: " + message);
    }

    private void sendWebhookAlert(String subject, String message) throws Exception {
        
        String payload = String.format(
            "{\"text\": \"%s\\n\\n%s\"}", 
            subject.replace("\"", "\\\""), 
            message.replace("\"", "\\\"").replace("\\n", "\\\\n")
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(alertWebhook))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String getClientIP(HttpServletRequest request) {
        
        if (request == null) return "unknown";

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    private List<SecurityEvent> getRecentSecurityEvents() {
        
        // Récupérer événements récents depuis audit
        // Implémentation simplifiée
        return new ArrayList<>();
    }

    private void scheduleAlertRemoval(String alertKey, long delayMs) {
        
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                recentAlerts.remove(alertKey);
            }
        }, delayMs);
    }

    // Reset compteurs périodiquement
    @Scheduled(fixedRate = 3600000) // Toutes les heures
    public void resetCounters() {
        
        failedLoginsByIP.clear();
        failedLoginsByUser.clear();
        requestCountByIP.clear();
    }

    // =================================================================
    // Classes de Données
    // =================================================================

    public static class SecurityMonitoringReport {
        private final LocalDateTime timestamp;
        private final Map<String, Object> metrics;
        private final List<String> topSuspiciousIPs;
        private final List<SecurityEvent> recentEvents;
        private final String overallStatus;

        public SecurityMonitoringReport(LocalDateTime timestamp, Map<String, Object> metrics,
                                      List<String> topSuspiciousIPs, List<SecurityEvent> recentEvents,
                                      String overallStatus) {
            this.timestamp = timestamp;
            this.metrics = metrics;
            this.topSuspiciousIPs = topSuspiciousIPs;
            this.recentEvents = recentEvents;
            this.overallStatus = overallStatus;
        }

        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getMetrics() { return metrics; }
        public List<String> getTopSuspiciousIPs() { return topSuspiciousIPs; }
        public List<SecurityEvent> getRecentEvents() { return recentEvents; }
        public String getOverallStatus() { return overallStatus; }
    }

    public static class SecurityEvent {
        private final String type;
        private final String description;
        private final LocalDateTime timestamp;
        private final String severity;

        public SecurityEvent(String type, String description, LocalDateTime timestamp, String severity) {
            this.type = type;
            this.description = description;
            this.timestamp = timestamp;
            this.severity = severity;
        }

        public String getType() { return type; }
        public String getDescription() { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getSeverity() { return severity; }
    }

    /**
     * Profil comportemental d'une adresse IP pour détection d'anomalies ML
     */
    private static class IPBehaviorProfile {
        private final List<Long> requestTimestamps = new ArrayList<>();
        private int totalFailedLogins = 0;
        private LocalDateTime firstSeen;

        public IPBehaviorProfile() {
            this.firstSeen = LocalDateTime.now();
        }

        public void recordFailedLogin() {
            totalFailedLogins++;
            requestTimestamps.add(System.currentTimeMillis());
            
            // Garder seulement les 100 dernières requêtes pour performances
            if (requestTimestamps.size() > 100) {
                requestTimestamps.remove(0);
            }
        }

        /**
         * Calcule un score d'anomalie basé sur plusieurs facteurs (0.0 = normal, 1.0 = très anormal)
         */
        public double calculateAnomalyScore() {
            List<String> reasons = new ArrayList<>();
            double score = 0.0;

            // 1. Taux d'échec élevé
            if (totalFailedLogins > 10) {
                score += 0.3;
                reasons.add("Taux d'échec élevé");
            }

            // 2. Vélocité d'attaque (requêtes/seconde)
            double velocity = getAttackVelocity();
            if (velocity > 2.0) { // Plus de 2 req/sec
                score += 0.3;
                reasons.add("Vélocité élevée: " + String.format("%.1f req/s", velocity));
            }

            // 3. Pattern de rafale (burst)
            if (hasRapidFirePattern()) {
                score += 0.2;
                reasons.add("Pattern automatisé détecté");
            }

            // 4. Nouveauté (IP jamais vue)
            Duration age = Duration.between(firstSeen, LocalDateTime.now());
            if (age.toMinutes() < 5 && totalFailedLogins > 5) {
                score += 0.2;
                reasons.add("IP nouvelle avec activité suspecte");
            }

            return Math.min(1.0, score);
        }

        public boolean hasRapidFirePattern() {
            if (requestTimestamps.size() < 5) return false;

            // Vérifier si les 5 dernières requêtes sont espacées de moins de 500ms
            long now = System.currentTimeMillis();
            List<Long> recent = requestTimestamps.stream()
                .filter(ts -> now - ts < 5000) // Dernières 5 secondes
                .toList();

            if (recent.size() >= 5) {
                long minInterval = Long.MAX_VALUE;
                for (int i = 1; i < recent.size(); i++) {
                    minInterval = Math.min(minInterval, recent.get(i) - recent.get(i-1));
                }
                return minInterval < 500; // Intervalle < 500ms = bot/script
            }
            return false;
        }

        public double getAttackVelocity() {
            if (requestTimestamps.size() < 2) return 0.0;

            long now = System.currentTimeMillis();
            long recentWindow = 10000; // 10 secondes
            
            long recentCount = requestTimestamps.stream()
                .filter(ts -> now - ts < recentWindow)
                .count();

            return recentCount / 10.0; // requêtes par seconde
        }

        public List<String> getAnomalyReasons() {
            List<String> reasons = new ArrayList<>();
            
            if (totalFailedLogins > 10) {
                reasons.add("Échecs multiples (" + totalFailedLogins + ")");
            }
            if (getAttackVelocity() > 2.0) {
                reasons.add("Vélocité élevée");
            }
            if (hasRapidFirePattern()) {
                reasons.add("Pattern automatisé");
            }
            
            return reasons;
        }
    }

    /**
     * Profil comportemental d'un utilisateur pour détection d'anomalies ML
     */
    private static class UserBehaviorProfile {
        private final Set<String> knownIPs = new HashSet<>();
        private final Map<String, Integer> ipFailureCounts = new HashMap<>();
        private int totalFailedLogins = 0;
        private final List<LocalDateTime> failureTimestamps = new ArrayList<>();

        public void recordFailedLogin(String ipAddress) {
            totalFailedLogins++;
            failureTimestamps.add(LocalDateTime.now());
            
            knownIPs.add(ipAddress);
            ipFailureCounts.put(ipAddress, ipFailureCounts.getOrDefault(ipAddress, 0) + 1);
            
            // Garder seulement les 50 dernières
            if (failureTimestamps.size() > 50) {
                failureTimestamps.remove(0);
            }
        }

        /**
         * Calcule un score d'anomalie basé sur le comportement utilisateur
         */
        public double calculateAnomalyScore() {
            double score = 0.0;

            // 1. Tentatives depuis plusieurs IPs (compte compromis)
            if (knownIPs.size() > 5) {
                score += 0.3;
            }

            // 2. Nombreuses tentatives échouées
            if (totalFailedLogins > 10) {
                score += 0.25;
            }

            // 3. Pattern de credential stuffing (tentatives régulières)
            if (hasCredentialStuffingPattern()) {
                score += 0.3;
            }

            // 4. Activité concentrée dans le temps
            if (hasTimeCompressionAnomaly()) {
                score += 0.15;
            }

            return Math.min(1.0, score);
        }

        private boolean hasCredentialStuffingPattern() {
            // Détecte si les tentatives viennent de nombreuses IPs différentes
            // (signe de credential stuffing depuis botnet)
            return knownIPs.size() > 3 && totalFailedLogins > 8;
        }

        private boolean hasTimeCompressionAnomaly() {
            if (failureTimestamps.size() < 5) return false;

            // Vérifier si 5+ tentatives en moins de 2 minutes
            LocalDateTime now = LocalDateTime.now();
            long recentCount = failureTimestamps.stream()
                .filter(ts -> Duration.between(ts, now).toMinutes() < 2)
                .count();

            return recentCount >= 5;
        }

        public List<String> getAnomalyReasons() {
            List<String> reasons = new ArrayList<>();
            
            if (knownIPs.size() > 5) {
                reasons.add("Multiples IPs (" + knownIPs.size() + ")");
            }
            if (totalFailedLogins > 10) {
                reasons.add("Échecs répétés (" + totalFailedLogins + ")");
            }
            if (hasCredentialStuffingPattern()) {
                reasons.add("Pattern credential stuffing");
            }
            if (hasTimeCompressionAnomaly()) {
                reasons.add("Burst temporel");
            }
            
            return reasons;
        }
    }
}