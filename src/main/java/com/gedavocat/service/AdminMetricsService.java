package com.gedavocat.service;

import com.gedavocat.dto.SystemMetricsDTO;
import com.gedavocat.repository.*;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service pour les métriques et informations système de l'admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final DataSource dataSource;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.upload.dir:./uploads/documents}")
    private String uploadDir;

    private static final long START_TIME = System.currentTimeMillis();

    /**
     * Récupère toutes les métriques système
     */
    public SystemMetricsDTO getSystemMetrics() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = Runtime.getRuntime().maxMemory();
        double memoryUsagePercent = (maxMemory > 0) ? ((double) usedMemory / maxMemory) * 100 : 0;
        
        long storageUsed = getStorageUsed();
        long storageLimit = getDiskTotalSpace();
        double storageUsagePercent = (storageLimit > 0) ? ((double) storageUsed / storageLimit) * 100 : 0;
        
        int activeConn = getActiveConnections();
        int maxConn = getMaxConnections();
        
        return SystemMetricsDTO.builder()
            .javaVersion(System.getProperty("java.version"))
            .javaVendor(System.getProperty("java.vendor"))
            .jvmName(System.getProperty("java.vm.name"))
            .jvmVersion(System.getProperty("java.vm.version"))
            .osName(System.getProperty("os.name"))
            .osVersion(System.getProperty("os.version"))
            .osArch(System.getProperty("os.arch"))
            .workingDirectory(System.getProperty("user.dir"))
            .userName(System.getProperty("user.name"))
            .totalMemory(totalMemory)
            .freeMemory(freeMemory)
            .usedMemory(usedMemory)
            .maxMemory(maxMemory)
            .memoryUsagePercent(memoryUsagePercent)
            .usedMemoryFormatted(formatBytes(usedMemory))
            .maxMemoryFormatted(formatBytes(maxMemory))
            .availableProcessors(Runtime.getRuntime().availableProcessors())
            .cpuUsage(getCpuUsage())
            .threadCount(Thread.activeCount())
            .peakThreadCount(ManagementFactory.getThreadMXBean().getPeakThreadCount())
            .loadedClasses(ManagementFactory.getClassLoadingMXBean().getLoadedClassCount())
            .uptime(System.currentTimeMillis() - START_TIME)
            .startTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(START_TIME), ZoneId.systemDefault()))
            .applicationVersion("1.0.0")
            .springBootVersion(SpringApplication.class.getPackage().getImplementationVersion())
            .databaseProductName(getDatabaseProductName())
            .databaseVersion(getDatabaseVersion())
            .databaseUrl(getDatabaseUrl())
            .activeConnections(activeConn)
            .maxConnections(maxConn)
            .totalUsers(userRepository.count())
            .totalClients(clientRepository.count())
            .totalCases(caseRepository.count())
            .totalDocuments(documentRepository.countNonDeleted())
            .totalInvoices(invoiceRepository.count())
            .storageUsed(storageUsed)
            .storageLimit(storageLimit)
            .storageUsagePercent(storageUsagePercent)
            .storageUsedFormatted(formatBytes(storageUsed))
            .storageLimitFormatted(formatBytes(storageLimit))
            .usersLastHour(countCreatedAfter(userRepository, LocalDateTime.now().minusHours(1)))
            .usersLastDay(countCreatedAfter(userRepository, LocalDateTime.now().minusDays(1)))
            .documentsUploadedToday(documentRepository.countNonDeletedCreatedAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)))
            .status("UP")
            .healthDetails(getHealthDetails())
            .build();
    }

    /**
     * Récupère l'utilisation CPU
     */
    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) osBean).getSystemCpuLoad() * 100;
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer l'utilisation CPU", e);
        }
        return 0.0;
    }

    /**
     * Récupère le nom du produit de base de données
     */
    private String getDatabaseProductName() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            return metaData.getDatabaseProductName();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du nom de la base de données", e);
            return "Unknown";
        }
    }

    /**
     * Récupère la version de la base de données
     */
    private String getDatabaseVersion() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            return metaData.getDatabaseProductVersion();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la version de la base de données", e);
            return "Unknown";
        }
    }

    /**
     * Récupère l'URL de la base de données
     */
    private String getDatabaseUrl() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            return metaData.getURL();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'URL de la base de données", e);
            return "Unknown";
        }
    }

    /**
     * Récupère le nombre de connexions actives depuis HikariCP
     */
    private int getActiveConnections() {
        try {
            if (dataSource instanceof HikariDataSource hikari) {
                HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                return pool != null ? pool.getActiveConnections() : 0;
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer les connexions actives HikariCP", e);
        }
        return 0;
    }

    /**
     * Récupère la taille max du pool HikariCP
     */
    private int getMaxConnections() {
        try {
            if (dataSource instanceof HikariDataSource hikari) {
                return hikari.getMaximumPoolSize();
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer la taille max du pool", e);
        }
        return 10;
    }

    /**
     * Calcule la taille réelle du répertoire uploads
     */
    private long getStorageUsed() {
        try {
            Path p = Paths.get(uploadDir);
            if (!Files.exists(p)) return 0L;
            try (Stream<Path> walk = Files.walk(p)) {
                return walk.filter(Files::isRegularFile)
                           .mapToLong(f -> f.toFile().length())
                           .sum();
            }
        } catch (Exception e) {
            log.warn("Impossible de calculer l'espace utilisé", e);
            return 0L;
        }
    }

    /**
     * Espace total du disque sur lequel tourne l'application
     */
    private long getDiskTotalSpace() {
        try {
            File root = new File(System.getProperty("user.dir"));
            return root.getTotalSpace();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Compte les entités créées après une date (User, Case, Client)
     */
    private long countCreatedAfter(UserRepository repo, LocalDateTime since) {
        try {
            return repo.countByCreatedAtAfter(since);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Formate les bytes en une chaîne lisible (KB, MB, GB, etc.)
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Récupère les détails de santé du système
     */
    private Map<String, Object> getHealthDetails() {
        Map<String, Object> health = new HashMap<>();
        
        // Vérifier la base de données
        try (Connection conn = dataSource.getConnection()) {
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("databaseError", e.getMessage());
        }
        
        // Vérifier la mémoire
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        if (memoryUsagePercent > 90) {
            health.put("memory", "WARNING");
        } else if (memoryUsagePercent > 95) {
            health.put("memory", "CRITICAL");
        } else {
            health.put("memory", "UP");
        }
        
        return health;
    }

    /**
     * Récupère les statistiques d'activité
     */
    public Map<String, Object> getActivityStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Statistiques par rôle - compter manuellement
        long lawyersCount = userRepository.findAll().stream()
            .filter(u -> "LAWYER".equals(u.getRole() != null ? u.getRole().name() : ""))
            .count();
        long clientsCount = userRepository.findAll().stream()
            .filter(u -> "CLIENT".equals(u.getRole() != null ? u.getRole().name() : ""))
            .count();
        long adminsCount = userRepository.findAll().stream()
            .filter(u -> "ADMIN".equals(u.getRole() != null ? u.getRole().name() : ""))
            .count();
        
        stats.put("lawyersCount", lawyersCount);
        stats.put("clientsCount", clientsCount);
        stats.put("adminsCount", adminsCount);
        
        // Statistiques sur les dossiers - compter manuellement par statut
        long openCases = caseRepository.findAll().stream()
            .filter(c -> "OPEN".equals(c.getStatus() != null ? c.getStatus().name() : ""))
            .count();
        long inProgressCases = caseRepository.findAll().stream()
            .filter(c -> "IN_PROGRESS".equals(c.getStatus() != null ? c.getStatus().name() : ""))
            .count();
        long closedCases = caseRepository.findAll().stream()
            .filter(c -> "CLOSED".equals(c.getStatus() != null ? c.getStatus().name() : ""))
            .count();
        long archivedCases = caseRepository.findAll().stream()
            .filter(c -> "ARCHIVED".equals(c.getStatus() != null ? c.getStatus().name() : ""))
            .count();
        
        stats.put("openCases", openCases);
        stats.put("inProgressCases", inProgressCases);
        stats.put("closedCases", closedCases);
        stats.put("archivedCases", archivedCases);
        
        // Statistiques temporelles (7 derniers jours)
        LocalDateTime since7days = LocalDateTime.now().minusDays(7);
        try { stats.put("newUsersLast7Days", userRepository.countByCreatedAtAfter(since7days)); }
        catch (Exception e) { stats.put("newUsersLast7Days", 0L); }
        try { stats.put("newClientsLast7Days", clientRepository.countByCreatedAtAfter(since7days)); }
        catch (Exception e) { stats.put("newClientsLast7Days", 0L); }
        try { stats.put("newCasesLast7Days", caseRepository.countByCreatedAtAfter(since7days)); }
        catch (Exception e) { stats.put("newCasesLast7Days", 0L); }
        try { stats.put("newDocumentsLast7Days", documentRepository.countNonDeletedCreatedAfter(since7days)); }
        catch (Exception e) { stats.put("newDocumentsLast7Days", 0L); }
        try { stats.put("loginCountLast7Days", auditLogRepository.countByActionAndCreatedAtAfter("LOGIN", since7days)); }
        catch (Exception e) { stats.put("loginCountLast7Days", 0L); }
        
        return stats;
    }

    /**
     * Formate la durée de fonctionnement
     */
    public String formatUptime(long uptimeMs) {
        Duration duration = Duration.ofMillis(uptimeMs);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        
        if (days > 0) {
            return String.format("%d jours, %d heures, %d minutes", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d heures, %d minutes", hours, minutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }
}
