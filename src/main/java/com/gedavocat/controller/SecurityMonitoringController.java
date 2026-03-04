package com.gedavocat.controller;

import com.gedavocat.security.monitoring.SecurityMonitoringService;
import com.gedavocat.security.monitoring.SecurityMonitoringService.SecurityMonitoringReport;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Contrôleur pour le tableau de bord de monitoring de sécurité
 * Accessible uniquement aux administrateurs
 */
@Controller
@RequestMapping("/admin/security-monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class SecurityMonitoringController {

    private final SecurityMonitoringService securityMonitoringService;
    private final UserRepository userRepository;

    /**
     * Tableau de bord principal de monitoring
     */
    @GetMapping
    public String dashboard(Authentication authentication, Model model) {
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            // Générer le rapport de sécurité
            SecurityMonitoringReport report = securityMonitoringService.generateSecurityReport();

            model.addAttribute("user", user);
            model.addAttribute("report", report);
            model.addAttribute("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            return "admin/security-monitoring";

        } catch (Exception e) {
            log.error("Erreur lors de la génération du tableau de bord de sécurité", e);
            model.addAttribute("error", "Erreur lors du chargement du rapport: " + e.getMessage());
            return "admin/security-monitoring";
        }
    }

    /**
     * API REST pour récupérer les métriques en temps réel (AJAX)
     */
    @GetMapping("/api/metrics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            SecurityMonitoringReport report = securityMonitoringService.generateSecurityReport();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", LocalDateTime.now());
            response.put("metrics", report.getMetrics());
            response.put("topSuspiciousIPs", report.getTopSuspiciousIPs());
            response.put("overallStatus", report.getOverallStatus());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des métriques", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * API REST pour récupérer l'historique des événements
     */
    @GetMapping("/api/events")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEvents(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            SecurityMonitoringReport report = securityMonitoringService.generateSecurityReport();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("events", report.getRecentEvents());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des événements", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Exporter le rapport de sécurité au format JSON
     */
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<SecurityMonitoringReport> exportReport() {
        try {
            SecurityMonitoringReport report = securityMonitoringService.generateSecurityReport();
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=security-report-" + 
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json")
                    .body(report);

        } catch (Exception e) {
            log.error("Erreur lors de l'export du rapport", e);
            return ResponseEntity.status(500).build();
        }
    }
}
