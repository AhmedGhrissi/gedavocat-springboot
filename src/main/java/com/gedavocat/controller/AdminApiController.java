package com.gedavocat.controller;

import com.gedavocat.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

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
    private final DataSource dataSource;

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/gedavocat}")
    private String datasourceUrl;

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
     * Lance un dump SQL complet via JDBC (compatible tous environnements Docker)
     */
    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> runBackup() {
        try {
            String dbName = extractDbName(datasourceUrl);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupDir = "/opt/gedavocat/backups";
            String backupFile = backupDir + "/backup_" + timestamp + ".sql.gz";

            new File(backupDir).mkdirs();

            try (Connection conn = dataSource.getConnection();
                 OutputStream fos = new FileOutputStream(backupFile);
                 GZIPOutputStream gzip = new GZIPOutputStream(fos);
                 Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {

                writer.write("-- DocAvocat SQL Backup\n");
                writer.write("-- Date: " + LocalDateTime.now() + "\n");
                writer.write("-- Database: " + dbName + "\n\n");
                writer.write("SET FOREIGN_KEY_CHECKS=0;\n\n");

                // Liste des tables (SEC FIX CTL-03 : validation du nom de table)
                List<String> tables = new ArrayList<>();
                java.util.regex.Pattern validTableName = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,64}$");
                try (ResultSet rs = conn.getMetaData().getTables(dbName, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tname = rs.getString("TABLE_NAME");
                        if (tname != null && validTableName.matcher(tname).matches()) {
                            tables.add(tname);
                        } else {
                            log.warn("Table ignorée (nom invalide) : {}", tname);
                        }
                    }
                }

                for (String table : tables) {
                    // DDL : CREATE TABLE
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                        if (rs.next()) {
                            writer.write("DROP TABLE IF EXISTS `" + table + "`;\n");
                            writer.write(rs.getString(2) + ";\n\n");
                        }
                    }

                    // Données : INSERT
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "`")) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        boolean hasRows = false;

                        // Colonnes
                        StringBuilder colNames = new StringBuilder("INSERT INTO `").append(table).append("` (");
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) colNames.append(", ");
                            colNames.append("`").append(meta.getColumnName(i)).append("`");
                        }
                        colNames.append(") VALUES\n");

                        StringBuilder rows = new StringBuilder();
                        while (rs.next()) {
                            hasRows = true;
                            rows.append("  (");
                            for (int i = 1; i <= colCount; i++) {
                                if (i > 1) rows.append(", ");
                                Object val = rs.getObject(i);
                                if (val == null) {
                                    rows.append("NULL");
                                } else if (val instanceof Number) {
                                    rows.append(val);
                                } else if (val instanceof Boolean) {
                                    rows.append((Boolean) val ? 1 : 0);
                                } else {
                                    rows.append("'").append(val.toString()
                                        .replace("\\", "\\\\")
                                        .replace("'", "''")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")).append("'");
                                }
                            }
                            rows.append("),\n");
                        }

                        if (hasRows) {
                            // Remplace la dernière virgule par un point-virgule
                            String rowStr = rows.toString();
                            rowStr = rowStr.substring(0, rowStr.lastIndexOf(",\n")) + ";\n\n";
                            writer.write(colNames.toString() + rowStr);
                        }
                    }
                }
                writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
            }

            long size = new File(backupFile).length();
            log.info("Sauvegarde JDBC créée : {} ({})", backupFile, formatBytes(size));
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Sauvegarde créée : " + backupFile + " (" + formatBytes(size) + ")"
            ));

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde JDBC", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Erreur lors de la sauvegarde"
            ));
        }
    }

    /**
     * Supprime les audit logs de plus de 90 jours
     */
    @PostMapping("/clean-audit-logs")
    public ResponseEntity<Map<String, Object>> cleanAuditLogs() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            long count = auditLogRepository.countByCreatedAtBefore(cutoff);
            auditLogRepository.deleteByCreatedAtBefore(cutoff);
            log.info("Nettoyage audit logs : {} entrées supprimées (antérieures à {})", count, cutoff);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", count + " entrées d'audit supprimées (antérieures à 90 jours)"
            ));
        } catch (Exception e) {
            log.error("Erreur lors du nettoyage des audit logs", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Erreur lors du nettoyage des audit logs"
            ));
        }
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
