package com.gedavocat.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité LABFTCheck - Traçabilité des contrôles LAB-FT
 * 
 * Conformité ACPR (Autorité de Contrôle Prudentiel et de Résolution):
 * - Code Monétaire et Financier Art. L561-1 et suivants
 * - Obligation de vigilance sur la clientèle
 * - Déclarations TRACFIN obligatoires
 * - Conservation des contrôles pendant 5 ans minimum
 * 
 * @author DPO Marie DUBOIS
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "labft_checks", indexes = {
    @Index(name = "idx_labft_client_id", columnList = "client_id"),
    @Index(name = "idx_labft_firm_id", columnList = "firm_id"),
    @Index(name = "idx_labft_payment_id", columnList = "payment_id"),
    @Index(name = "idx_labft_check_type", columnList = "check_type"),
    @Index(name = "idx_labft_risk_level", columnList = "risk_level"),
    @Index(name = "idx_labft_result", columnList = "check_result"),
    @Index(name = "idx_labft_created_at", columnList = "created_at"),
    @Index(name = "idx_labft_tracfin", columnList = "tracfin_declared,tracfin_reference")
})
public class LABFTCheck {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    private Firm firm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    /**
     * Type de vérification LAB-FT effectuée
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 50)
    private CheckType checkType;

    /**
     * Niveau de risque évalué
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    /**
     * Score de risque calculé (0-100)
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * Résultat de la vérification
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_result", nullable = false, length = 20)
    private CheckResult checkResult;

    /**
     * Montant de la transaction analysée (si applicable)
     */
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Type de transaction
     */
    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    /**
     * Raisons des alertes détectées (stocké en JSON)
     */
    @Column(name = "alert_reasons", columnDefinition = "TEXT")
    private String alertReasons;

    /**
     * Facteurs de risque identifiés (stocké en JSON)
     */
    @Column(name = "risk_factors", columnDefinition = "TEXT")
    private String riskFactors;

    /**
     * Personne Politiquement Exposée détectée
     */
    @Column(name = "pep_detected", nullable = false)
    private boolean pepDetected = false;

    /**
     * Présence sur liste de sanctions
     */
    @Column(name = "sanctions_detected", nullable = false)
    private boolean sanctionsDetected = false;

    /**
     * Déclaration TRACFIN effectuée
     */
    @Column(name = "tracfin_declared", nullable = false)
    private boolean tracfinDeclared = false;

    /**
     * Référence de la déclaration TRACFIN
     */
    @Column(name = "tracfin_reference", length = 100)
    private String tracfinReference;

    /**
     * Utilisateur ayant effectué le contrôle
     */
    @Column(name = "checked_by", length = 36)
    private String checkedBy;

    /**
     * Vérification automatique (true) ou manuelle (false)
     */
    @Column(name = "automatic_check", nullable = false)
    private boolean automaticCheck = true;

    /**
     * Commentaires du vérificateur
     */
    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    /**
     * Date de création du contrôle
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Date de dernière mise à jour
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Date de prochaine révision obligatoire
     */
    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    /**
     * Version pour gestion optimiste du verrouillage
     */
    @Version
    @Column(name = "entity_version")
    private Long version;

    // =============================================================================
    // Énumérations
    // =============================================================================

    /**
     * Types de contrôles LAB-FT
     */
    public enum CheckType {
        VIGILANCE_CLIENT("Vigilance client initiale"),
        PEP_CHECK("Contrôle Personne Politiquement Exposée"),
        SANCTIONS_CHECK("Contrôle listes de sanctions"),
        TRANSACTION_ANALYSIS("Analyse de transaction"),
        TRACFIN_DECLARATION("Déclaration TRACFIN"),
        RISK_SCORING("Évaluation score de risque"),
        DUE_DILIGENCE("Due diligence renforcée");

        private final String description;

        CheckType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Niveaux de risque LAB-FT
     */
    public enum RiskLevel {
        FAIBLE("Risque faible"),
        MODERE("Risque modéré"),
        ELEVE("Risque élevé"),
        CRITIQUE("Risque critique - Vigilance renforcée requise");

        private final String description;

        RiskLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Résultats possibles d'un contrôle
     */
    public enum CheckResult {
        CONFORME("Conforme - Aucune alerte"),
        ALERTE("Alerte - Surveillance recommandée"),
        SUSPECT("Suspect - Vigilance renforcée obligatoire"),
        BLOQUE("Bloqué - Opération refusée");

        private final String description;

        CheckResult(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // =============================================================================
    // Callbacks JPA
    // =============================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        
        // Définir la prochaine date de révision (6 mois pour risque faible, 3 mois pour modéré/élevé, 1 mois pour critique)
        if (this.nextReviewDate == null && this.riskLevel != null) {
            int monthsToAdd = switch (this.riskLevel) {
                case CRITIQUE -> 1;
                case ELEVE -> 3;
                case MODERE -> 6;
                case FAIBLE -> 12;
            };
            this.nextReviewDate = LocalDate.now().plusMonths(monthsToAdd);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // =============================================================================
    // Méthodes utilitaires
    // =============================================================================

    /**
     * Vérifie si une révision est nécessaire
     */
    public boolean needsReview() {
        return this.nextReviewDate != null && 
               this.nextReviewDate.isBefore(LocalDate.now());
    }

    /**
     * Vérifie si le contrôle a généré une alerte
     */
    public boolean hasAlert() {
        return this.checkResult == CheckResult.ALERTE || 
               this.checkResult == CheckResult.SUSPECT ||
               this.checkResult == CheckResult.BLOQUE;
    }

    /**
     * Vérifie si le client est à haut risque
     */
    public boolean isHighRisk() {
        return this.riskLevel == RiskLevel.ELEVE || 
               this.riskLevel == RiskLevel.CRITIQUE;
    }

    /**
     * Retourne un résumé du contrôle pour l'audit
     */
    public String getSummary() {
        return String.format(
            "LAB-FT Check [%s] - Client: %s, Type: %s, Résultat: %s, Risque: %s%s%s",
            this.id,
            this.client != null ? this.client.getId() : "N/A",
            this.checkType.getDescription(),
            this.checkResult.getDescription(),
            this.riskLevel != null ? this.riskLevel.getDescription() : "N/A",
            this.pepDetected ? ", PEP détecté" : "",
            this.sanctionsDetected ? ", Sanctions détectées" : ""
        );
    }
}
