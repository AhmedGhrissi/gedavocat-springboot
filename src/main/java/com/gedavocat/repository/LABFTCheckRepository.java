package com.gedavocat.repository;

import com.gedavocat.model.LABFTCheck;
import com.gedavocat.model.LABFTCheck.CheckResult;
import com.gedavocat.model.LABFTCheck.CheckType;
import com.gedavocat.model.LABFTCheck.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les contrôles LAB-FT
 * 
 * Permet la traçabilité complète des vérifications anti-blanchiment
 * conformément aux exigences ACPR.
 * 
 * @author DPO Marie DUBOIS
 */
@Repository
public interface LABFTCheckRepository extends JpaRepository<LABFTCheck, String> {

    /**
     * Trouve tous les contrôles pour un client donné
     */
    List<LABFTCheck> findByClientIdOrderByCreatedAtDesc(String clientId);

    /**
     * Trouve tous les contrôles pour un cabinet
     */
    List<LABFTCheck> findByFirmIdOrderByCreatedAtDesc(String firmId);

    /**
     * Trouve les contrôles par type
     */
    List<LABFTCheck> findByCheckTypeAndFirmIdOrderByCreatedAtDesc(CheckType checkType, String firmId);

    /**
     * Trouve les contrôles par niveau de risque
     */
    List<LABFTCheck> findByRiskLevelAndFirmIdOrderByCreatedAtDesc(RiskLevel riskLevel, String firmId);

    /**
     * Trouve les contrôles avec alertes
     */
    List<LABFTCheck> findByCheckResultInAndFirmIdOrderByCreatedAtDesc(
        List<CheckResult> results, String firmId
    );

    /**
     * Trouve les contrôles avec PEP détecté
     */
    List<LABFTCheck> findByPepDetectedTrueAndFirmIdOrderByCreatedAtDesc(String firmId);

    /**
     * Trouve les contrôles avec sanctions détectées
     */
    List<LABFTCheck> findBySanctionsDetectedTrueAndFirmIdOrderByCreatedAtDesc(String firmId);

    /**
     * Trouve les déclarations TRACFIN
     */
    List<LABFTCheck> findByTracfinDeclaredTrueAndFirmIdOrderByCreatedAtDesc(String firmId);

    /**
     * Trouve les contrôles nécessitant une révision
     */
    List<LABFTCheck> findByNextReviewDateBeforeAndFirmIdOrderByNextReviewDateAsc(
        LocalDate date, String firmId
    );

    /**
     * Trouve le dernier contrôle d'un client par type
     */
    Optional<LABFTCheck> findFirstByClientIdAndCheckTypeOrderByCreatedAtDesc(
        String clientId, CheckType checkType
    );

    /**
     * Trouve les contrôles par plage de dates
     */
    List<LABFTCheck> findByFirmIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        String firmId, LocalDateTime startDate, LocalDateTime endDate
    );

    /**
     * Compte les contrôles par résultat pour un cabinet
     */
    @Query("SELECT c.checkResult, COUNT(c) FROM LABFTCheck c WHERE c.firm.id = :firmId GROUP BY c.checkResult")
    List<Object[]> countByCheckResultForFirm(@Param("firmId") String firmId);

    /**
     * Compte les contrôles par niveau de risque pour un cabinet
     */
    @Query("SELECT c.riskLevel, COUNT(c) FROM LABFTCheck c WHERE c.firm.id = :firmId GROUP BY c.riskLevel")
    List<Object[]> countByRiskLevelForFirm(@Param("firmId") String firmId);

    /**
     * Trouve les contrôles à haut risque récents
     */
    @Query("SELECT c FROM LABFTCheck c WHERE c.firm.id = :firmId " +
           "AND c.riskLevel IN ('ELEVE', 'CRITIQUE') " +
           "AND c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<LABFTCheck> findRecentHighRiskChecks(
        @Param("firmId") String firmId,
        @Param("since") LocalDateTime since
    );

    /**
     * Compte les déclarations TRACFIN pour une période
     */
    @Query("SELECT COUNT(c) FROM LABFTCheck c WHERE c.firm.id = :firmId " +
           "AND c.tracfinDeclared = true " +
           "AND c.createdAt BETWEEN :startDate AND :endDate")
    long countTracfinDeclarations(
        @Param("firmId") String firmId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Trouve les clients à risque élevé
     */
    @Query("SELECT DISTINCT c.client FROM LABFTCheck c WHERE c.firm.id = :firmId " +
           "AND c.riskLevel IN ('ELEVE', 'CRITIQUE') " +
           "ORDER BY c.createdAt DESC")
    List<Object> findHighRiskClients(@Param("firmId") String firmId);

    /**
     * Statistiques LAB-FT pour dashboard ACPR
     */
    @Query("SELECT " +
           "COUNT(DISTINCT c.client.id) as totalClients, " +
           "SUM(CASE WHEN c.riskLevel = 'CRITIQUE' THEN 1 ELSE 0 END) as criticalRisks, " +
           "SUM(CASE WHEN c.pepDetected = true THEN 1 ELSE 0 END) as pepCount, " +
           "SUM(CASE WHEN c.sanctionsDetected = true THEN 1 ELSE 0 END) as sanctionsCount, " +
           "SUM(CASE WHEN c.tracfinDeclared = true THEN 1 ELSE 0 END) as tracfinCount " +
           "FROM LABFTCheck c WHERE c.firm.id = :firmId " +
           "AND c.createdAt >= :since")
    Object getComplianceStatistics(
        @Param("firmId") String firmId,
        @Param("since") LocalDateTime since
    );
}
