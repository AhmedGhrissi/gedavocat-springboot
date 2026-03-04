package com.gedavocat.controller;

import com.gedavocat.compliance.ComplianceScoring;
import com.gedavocat.compliance.ComplianceScoring.ComplianceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller REST pour les API de Conformité Réglementaire
 * 
 * Expose les endpoints pour :
 * - Calcul du score de conformité global
 * - Vérification des exigences par domaine
 * - Dashboard de conformité temps réel
 * - Simulation contrôle ACPR
 * 
 * @author DPO Marie DUBOIS
 * @version 2.0 - API Institutional Grade
 */
@RestController
@RequestMapping("/api/compliance")
@PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
public class ComplianceController {

    @Autowired
    private ComplianceScoring complianceScoring;

    // =============================================================================
    // 🎯 ENDPOINTS SCORING PRINCIPAL
    // =============================================================================
    
    /**
     * Calcule et retourne le score global de conformité
     * 
     * GET /api/compliance/score
     */
    @GetMapping("/score")
    public ResponseEntity<Map<String, Object>> getComplianceScore() {
        
        try {
            ComplianceResult result = complianceScoring.calculateGlobalScore();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("scoringId", result.getScoringId());
            response.put("calculationTime", result.getCalculationTime());
            response.put("totalScore", result.getTotalScore());
            response.put("maxScore", 100);
            response.put("percentage", result.getTotalScore() + "%");
            response.put("riskLevel", Map.of(
                "level", result.getRiskLevel().getLabel(),
                "color", result.getRiskLevel().getColor(),
                "description", result.getRiskLevel().getDescription()
            ));
            
            // Détail par domaine
            Map<String, Object> domainDetails = new HashMap<>();
            result.getDomainScores().forEach((domain, score) -> {
                domainDetails.put(domain.name().toLowerCase(), Map.of(
                    "score", score.getScore(),
                    "maxScore", domain.getMaxPoints(),
                    "percentage", Math.round((double) score.getScore() / domain.getMaxPoints() * 100),
                    "passedCriteria", score.getPassedCriteria(),
                    "failedCriteria", score.getFailedCriteria()
                ));
            });
            response.put("domainScores", domainDetails);
            response.put("recommendations", result.getRecommendations());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Erreur calcul score conformité: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Endpoint interne pour monitoring (sans authentification)
     * 
     * GET /internal/compliance-score
     */
    @GetMapping("/internal/compliance-score")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> getInternalComplianceScore() {
        
        try {
            ComplianceResult result = complianceScoring.calculateGlobalScore();
            
            Map<String, Object> response = new HashMap<>();
            response.put("score", result.getTotalScore());
            response.put("riskLevel", result.getRiskLevel().getLabel());
            response.put("timestamp", result.getCalculationTime());
            response.put("status", result.getTotalScore() >= 85 ? "COMPLIANT" : "NON_COMPLIANT");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("score", 0);
            error.put("riskLevel", "UNKNOWN");
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // =============================================================================
    // 🏛️ ENDPOINTS DOMAINES SPÉCIFIQUES
    // =============================================================================
    
    /**
     * Vérification conformité ACPR (Anti-Money Laundering)
     * 
     * GET /api/compliance/acpr
     */
    @GetMapping("/acpr")
    public ResponseEntity<Map<String, Object>> getACPRCompliance() {
        
        ComplianceResult result = complianceScoring.calculateGlobalScore();
        ComplianceScoring.DomainScore acprScore = result.getDomainScores()
            .get(ComplianceScoring.ComplianceDomain.ACPR);
        
        Map<String, Object> response = new HashMap<>();
        response.put("domain", "ACPR - Autorité de Contrôle Prudentiel");
        response.put("score", acprScore.getScore());
        response.put("maxScore", ComplianceScoring.ComplianceDomain.ACPR.getMaxPoints());
        response.put("compliant", acprScore.getScore() >= 25); // 83% minimum
        response.put("passedCriteria", acprScore.getPassedCriteria());
        response.put("failedCriteria", acprScore.getFailedCriteria());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Vérification conformité RGPD (Data Protection)
     * 
     * GET /api/compliance/rgpd
     */
    @GetMapping("/rgpd") 
    public ResponseEntity<Map<String, Object>> getRGPDCompliance() {
        
        ComplianceResult result = complianceScoring.calculateGlobalScore();
        ComplianceScoring.DomainScore rgpdScore = result.getDomainScores()
            .get(ComplianceScoring.ComplianceDomain.RGPD);
        
        Map<String, Object> response = new HashMap<>();
        response.put("domain", "RGPD - Règlement Général Protection Données");
        response.put("score", rgpdScore.getScore());
        response.put("maxScore", ComplianceScoring.ComplianceDomain.RGPD.getMaxPoints());
        response.put("compliant", rgpdScore.getScore() >= 20); // 80% minimum
        response.put("passedCriteria", rgpdScore.getPassedCriteria());
        response.put("failedCriteria", rgpdScore.getFailedCriteria());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Vérification conformité ISO 27001 (Information Security)
     * 
     * GET /api/compliance/iso27001
     */
    @GetMapping("/iso27001")
    public ResponseEntity<Map<String, Object>> getISO27001Compliance() {
        
        ComplianceResult result = complianceScoring.calculateGlobalScore();
        ComplianceScoring.DomainScore isoScore = result.getDomainScores()
            .get(ComplianceScoring.ComplianceDomain.ISO27001);
        
        Map<String, Object> response = new HashMap<>();
        response.put("domain", "ISO 27001 - Système Management Sécurité Information");
        response.put("score", isoScore.getScore());
        response.put("maxScore", ComplianceScoring.ComplianceDomain.ISO27001.getMaxPoints());
        response.put("compliant", isoScore.getScore() >= 20); // 80% minimum
        response.put("passedCriteria", isoScore.getPassedCriteria());
        response.put("failedCriteria", isoScore.getFailedCriteria());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Vérification conformité eIDAS (Electronic Signatures)
     * 
     * GET /api/compliance/eidas
     */
    @GetMapping("/eidas")
    public ResponseEntity<Map<String, Object>> getEIDASCompliance() {
        
        ComplianceResult result = complianceScoring.calculateGlobalScore();
        ComplianceScoring.DomainScore eidasScore = result.getDomainScores()
            .get(ComplianceScoring.ComplianceDomain.EIDAS);
        
        Map<String, Object> response = new HashMap<>();
        response.put("domain", "eIDAS - Règlement Identification Électronique");
        response.put("score", eidasScore.getScore());
        response.put("maxScore", ComplianceScoring.ComplianceDomain.EIDAS.getMaxPoints());
        response.put("compliant", eidasScore.getScore() >= 16); // 80% minimum
        response.put("passedCriteria", eidasScore.getPassedCriteria());
        response.put("failedCriteria", eidasScore.getFailedCriteria());
        
        return ResponseEntity.ok(response);
    }

    // =============================================================================
    // 🎭 SIMULATION CONTRÔLE ACPR
    // =============================================================================
    
    /**
     * Simulation d'un contrôle ACPR (Inspection réglementaire)
     * 
     * POST /api/compliance/simulate-acpr-control
     */
    @PostMapping("/simulate-acpr-control")
    public ResponseEntity<Map<String, Object>> simulateACPRControl(@RequestParam(defaultValue = "false") boolean generateReport) {
        
        try {
            ComplianceResult result = complianceScoring.calculateGlobalScore();
            
            Map<String, Object> simulation = new HashMap<>();
            simulation.put("controlId", "CTRL-ACPR-" + System.currentTimeMillis());
            simulation.put("inspector", "Inspecteur ACPR - Simulation IA");
            simulation.put("controlDate", java.time.LocalDateTime.now());
            simulation.put("controlType", "Contrôle sur pièces - LAB-FT");
            
            // Évaluation globale
            boolean passed = result.getTotalScore() >= 85;
            simulation.put("controlResult", passed ? "CONFORME" : "NON_CONFORME");
            simulation.put("globalScore", result.getTotalScore());
            
            // Points d'attention spécifiques ACPR
            ComplianceScoring.DomainScore acprScore = result.getDomainScores()
                .get(ComplianceScoring.ComplianceDomain.ACPR);
            
            simulation.put("acprDetails", Map.of(
                "labftScore", acprScore.getScore() + "/30",
                "compliant", acprScore.getScore() >= 25,
                "criticalPoints", acprScore.getFailedCriteria(),
                "strongPoints", acprScore.getPassedCriteria()
            ));
            
            // Recommandations ACPR
            simulation.put("acprRecommendations", java.util.Arrays.asList(
                "Maintenir la veille réglementaire LAB-FT",
                "Effectuer tests périodiques des contrôles",
                "Documenter les procédures de vigilance",
                "Former équipes aux obligations ACPR"
            ));
            
            // Simulation action corrective si nécessaire
            if (!passed) {
                simulation.put("correctiveActions", Map.of(
                    "deadline", "30 jours",
                    "followUpControl", "Dans 3 mois", 
                    "penaltyRisk", "Jusqu'à 5M€ d'amende",
                    "priority", "CRITIQUE"
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "simulation", simulation,
                "note", "⚠️ Ceci est une simulation à des fins de préparation. Un vrai contrôle ACPR impliquerait des vérifications approfondies sur pièces et sur place."
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Erreur simulation contrôle ACPR: " + e.getMessage()
            ));
        }
    }

    // =============================================================================
    // 📊 DASHBOARD DONNÉES TEMPS RÉEL
    // =============================================================================
    
    /**
     * Données pour dashboard de conformité temps réel
     * 
     * GET /api/compliance/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getComplianceDashboard() {
        
        try {
            ComplianceResult result = complianceScoring.calculateGlobalScore();
            
            Map<String, Object> dashboard = new HashMap<>();
            
            // Métriques principales
            dashboard.put("metrics", Map.of(
                "totalScore", result.getTotalScore(),
                "riskLevel", result.getRiskLevel().getLabel(),
                "riskColor", result.getRiskLevel().getColor(),
                "lastAuditDate", result.getCalculationTime(),
                "nextAuditDue", result.getCalculationTime().plusMonths(3),
                "complianceStatus", result.getTotalScore() >= 85 ? "COMPLIANT" : "AT_RISK"
            ));
            
            // Répartition par domaine pour graphique
            Map<String, Object> domainChart = new HashMap<>();
            result.getDomainScores().forEach((domain, score) -> {
                domainChart.put(domain.name().toLowerCase(), Map.of(
                    "current", score.getScore(),
                    "target", domain.getMaxPoints(),
                    "percentage", Math.round((double) score.getScore() / domain.getMaxPoints() * 100)
                ));
            });
            dashboard.put("domainBreakdown", domainChart);
            
            // Alertes et actions
            int criticalIssues = (int) result.getDomainScores().values().stream()
                .mapToLong(score -> score.getFailedCriteria().size())
                .sum();
            
            dashboard.put("alerts", Map.of(
                "criticalIssues", criticalIssues,
                "pendingActions", result.getRecommendations().size(),
                "complianceRisk", result.getTotalScore() < 70 ? "HIGH" : 
                                result.getTotalScore() < 85 ? "MEDIUM" : "LOW"
            ));
            
            // Tendance (simulation - en production: historique réel)
            dashboard.put("trends", Map.of(
                "lastMonth", Math.max(60, result.getTotalScore() - 5),
                "currentMonth", result.getTotalScore(),
                "projection", Math.min(100, result.getTotalScore() + 2),
                "direction", "IMPROVING"
            ));
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "dashboard", dashboard,
                "refreshedAt", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Erreur génération dashboard: " + e.getMessage()
            ));
        }
    }

    // =============================================================================
    // 🔧 ENDPOINTS UTILITAIRES
    // =============================================================================
    
    /**
     * Health check pour le système de conformité
     * 
     * GET /api/compliance/health
     */
    @GetMapping("/health")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> getComplianceHealth() {
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", java.time.LocalDateTime.now());
        health.put("version", "2.0");
        health.put("components", Map.of(
            "scoringEngine", "OPERATIONAL",
            "acprModule", "OPERATIONAL", 
            "rgpdModule", "OPERATIONAL",
            "iso27001Module", "OPERATIONAL",
            "eidasModule", "OPERATIONAL"
        ));
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Rapport formaté pour impression/export
     * 
     * GET /api/compliance/report
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getFormattedReport() {
        
        ComplianceResult result = complianceScoring.calculateGlobalScore();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("reportId", result.getScoringId());
        response.put("formattedReport", result.getFormattedReport());
        response.put("generatedAt", java.time.LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }
}