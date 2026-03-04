package com.gedavocat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration pour la conformité réglementaire
 * 
 * Cette classe centralise toutes les configurations liées à :
 * - RGPD et DPO (Data Protection Officer)
 * - LAB-FT (Lutte Anti-Blanchiment et Financement du Terrorisme)
 * - PRA (Plan de Reprise d'Activité)
 * - eIDAS (Archivage électronique qualifié)
 * 
 * @author DPO Marie DUBOIS
 * @version 2.0 - Conformité renforcée 2026
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class ComplianceConfig {

    // =============================================================================
    // RGPD - Configuration DPO
    // =============================================================================
    
    @Value("${app.rgpd.dpo.name}")
    private String dpoName;
    
    @Value("${app.rgpd.dpo.email}")
    private String dpoEmail;
    
    @Value("${app.rgpd.dpo.phone}")
    private String dpoPhone;
    
    @Value("${app.rgpd.dpo.address}")
    private String dpoAddress;
    
    @Value("${app.rgpd.dpo.certification}")
    private String dpoCertification;
    
    // Durées de rétention (en années)
    @Value("${app.rgpd.retention.client-data}")
    private int clientDataRetention;
    
    @Value("${app.rgpd.retention.case-data}")
    private int caseDataRetention;
    
    @Value("${app.rgpd.retention.financial-data}")
    private int financialDataRetention;
    
    @Value("${app.rgpd.retention.audit-logs}")
    private int auditLogsRetention;
    
    @Value("${app.rgpd.retention.communication-data}")
    private int communicationDataRetention;

    // =============================================================================
    // LAB-FT - Configuration Anti-Blanchiment
    // =============================================================================
    
    @Value("${app.labft.enabled}")
    private boolean labftEnabled;
    
    @Value("${app.labft.seuil.vigilance}")
    private double seuilVigilance;
    
    @Value("${app.labft.seuil.declaration}")
    private double seuilDeclaration;
    
    @Value("${app.labft.scoring.enabled}")
    private boolean scoringEnabled;
    
    @Value("${app.labft.pep.check}")
    private boolean pepCheckEnabled;
    
    @Value("${app.labft.sanctions.check}")
    private boolean sanctionsCheckEnabled;
    
    @Value("${app.labft.tracfin.endpoint}")
    private String tracfinEndpoint;

    // =============================================================================
    // PRA - Configuration Plan de Reprise d'Activité
    // =============================================================================
    
    @Value("${app.pra.enabled}")
    private boolean praEnabled;
    
    @Value("${app.pra.rto.max}")
    private int rtoMaxHours;
    
    @Value("${app.pra.rpo.max}")
    private int rpoMaxHours;
    
    @Value("${app.pra.backup.frequency}")
    private int backupFrequencyHours;
    
    @Value("${app.pra.contact.responsable}")
    private String praResponsable;
    
    @Value("${app.pra.site.secours}")
    private String siteSecours;

    // =============================================================================
    // eIDAS - Configuration Archivage Électronique
    // =============================================================================
    
    @Value("${app.eidas.tsa.enabled}")
    private boolean tsaEnabled;
    
    @Value("${app.eidas.tsa.url}")
    private String tsaUrl;
    
    @Value("${app.eidas.tsa.policy}")
    private String tsaPolicy;
    
    @Value("${app.eidas.archive.format}")
    private String archiveFormat;
    
    @Value("${app.eidas.signature.level}")
    private String signatureLevel;
    
    @Value("${app.eidas.retention.legal}")
    private int legalRetentionYears;

    // =============================================================================
    // Getters pour accès aux propriétés
    // =============================================================================
    
    public String getDpoName() { return dpoName; }
    public String getDpoEmail() { return dpoEmail; }
    public String getDpoPhone() { return dpoPhone; }
    public String getDpoAddress() { return dpoAddress; }
    public String getDpoCertification() { return dpoCertification; }
    
    public int getClientDataRetention() { return clientDataRetention; }
    public int getCaseDataRetention() { return caseDataRetention; }
    public int getFinancialDataRetention() { return financialDataRetention; }
    public int getAuditLogsRetention() { return auditLogsRetention; }
    public int getCommunicationDataRetention() { return communicationDataRetention; }
    
    public boolean isLabftEnabled() { return labftEnabled; }
    public double getSeuilVigilance() { return seuilVigilance; }
    public double getSeuilDeclaration() { return seuilDeclaration; }
    public boolean isScoringEnabled() { return scoringEnabled; }
    public boolean isPepCheckEnabled() { return pepCheckEnabled; }
    public boolean isSanctionsCheckEnabled() { return sanctionsCheckEnabled; }
    public String getTracfinEndpoint() { return tracfinEndpoint; }
    
    public boolean isPraEnabled() { return praEnabled; }
    public int getRtoMaxHours() { return rtoMaxHours; }
    public int getRpoMaxHours() { return rpoMaxHours; }
    public int getBackupFrequencyHours() { return backupFrequencyHours; }
    public String getPraResponsable() { return praResponsable; }
    public String getSiteSecours() { return siteSecours; }
    
    public boolean isTsaEnabled() { return tsaEnabled; }
    public String getTsaUrl() { return tsaUrl; }
    public String getTsaPolicy() { return tsaPolicy; }
    public String getArchiveFormat() { return archiveFormat; }
    public String getSignatureLevel() { return signatureLevel; }
    public int getLegalRetentionYears() { return legalRetentionYears; }

    /**
     * Validation de la configuration de conformité
     * @return true si la configuration est valide
     */
    public boolean isComplianceConfigurationValid() {
        return dpoName != null && !dpoName.isEmpty() &&
               dpoEmail != null && !dpoEmail.isEmpty() &&
               labftEnabled && 
               praEnabled &&
               tsaEnabled;
    }

    /**
     * Génère un rapport de conformité
     * @return String contenant le statut de conformité
     */
    public String getComplianceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT DE CONFORMITÉ RÉGLEMENTAIRE ===\n");
        report.append("DPO Désigné: ").append(dpoName).append(" (").append(dpoEmail).append(")\n");
        report.append("LAB-FT Actif: ").append(labftEnabled ? "OUI" : "NON").append("\n");
        report.append("PRA Configuré: ").append(praEnabled ? "OUI" : "NON").append("\n");
        report.append("eIDAS Qualifié: ").append(tsaEnabled ? "OUI" : "NON").append("\n");
        report.append("Score Conformité: CIBLE 95/100\n");
        return report.toString();
    }
}