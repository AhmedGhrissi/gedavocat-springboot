package com.gedavocat.controller;

import com.gedavocat.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * API REST pour les actions d'administration (GC, sauvegarde, nettoyage)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiController {

    private final AuditLogRepository auditLogRepository;

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/gedavocat}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:gedavocat}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    /**
     * Force le Garbage Collector JVM
     */
    @PostMapping("/gc")
    public ResponseEntity<Map<String, Object>> runGarbageCollector() {
        long before = Runtime.getRuntime().freeMemory();
        System.gc();
        long after = Runtime.getRuntime().freeMemory();
        long freed = Math.max(0, after - before);
        log.info("Garbage Collector lancé manuellement. Mémoire libérée approx : {} bytes", freed);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Garbage Collector exécuté. Mémoire libre avant : " + formatBytes(before) + ", après : " + formatBytes(after)
        ));
    }

    /**
     * Lance un mysqldump de la base de données
     */
    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> runBackup() {
        try {
            // Extraire le nom de la base depuis l'URL JDBC
            String dbName = extractDbName(datasourceUrl);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupDir = "/opt/gedavocat/backups";
            String backupFile = backupDir + "/backup_" + timestamp + ".sql.gz";

            // Créer le répertoire si inexistant
            new File(backupDir).mkdirs();

            String[] cmd = {
                "/bin/bash", "-c",
                String.format("mysqldump -u %s -p%s %s 2>/dev/null | gzip > %s",
                    datasourceUsername, datasourcePassword, dbName, backupFile)
            };

            Process process = Runtime.getRuntime().exec(cmd);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                long size = new File(backupFile).length();
                log.info("Sauvegarde créée : {} ({} bytes)", backupFile, size);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sauvegarde créée : " + backupFile + " (" + formatBytes(size) + ")"
                ));
            } else {
                log.warn("mysqldump terminé avec code {}", exitCode);
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Sauvegarde terminée avec code " + exitCode + ". Vérifiez les logs serveur."
                ));
            }
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Erreur : " + e.getMessage()
            ));
        }
    }

    /**
     * Supprime les audit logs de plus de 90 jours
     */
    @PostMapping("/clean-audit-logs")
    @Transactional
    public ResponseEntity<Map<String, Object>> cleanAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        long count = auditLogRepository.countByCreatedAtBefore(cutoff);
        auditLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Nettoyage audit logs : {} entrées supprimées (antérieures à {})", count, cutoff);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", count + " entrées d'audit supprimées (antérieures à 90 jours)"
        ));
    }

    private String extractDbName(String jdbcUrl) {
        try {
            // jdbc:mysql://host:port/dbname?params
            String[] parts = jdbcUrl.split("/");
            String last = parts[parts.length - 1];
            return last.contains("?") ? last.substring(0, last.indexOf("?")) : last;
        } catch (Exception e) {
            return "gedavocat";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }
}
