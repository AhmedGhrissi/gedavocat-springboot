package com.gedavocat.service;

import com.gedavocat.dto.SystemMetricsDTO;
import com.gedavocat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

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
        long storageLimit = 10737418240L; // 10 GB par défaut
        double storageUsagePercent = (storageLimit > 0) ? ((double) storageUsed / storageLimit) * 100 : 0;
        
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
            .activeConnections(getActiveConnections())
            .maxConnections(100) // À configurer selon votre pool
            .totalUsers(userRepository.count())
            .totalClients(clientRepository.count())
            .totalCases(caseRepository.count())
            .totalDocuments(documentRepository.count())
            .totalInvoices(invoiceRepository.count())
            .storageUsed(storageUsed)
            .storageLimit(storageLimit)
            .storageUsagePercent(storageUsagePercent)
            .storageUsedFormatted(formatBytes(storageUsed))
            .storageLimitFormatted(formatBytes(storageLimit))
            .usersLastHour(0L) // À implémenter avec audit logs
            .usersLastDay(0L)
            .documentsUploadedToday(0L)
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
     * Récupère le nombre de connexions actives
     */
    private int getActiveConnections() {
        // À implémenter selon votre pool de connexions (HikariCP, etc.)
        return 5; // Placeholder
    }

    /**
     * Récupère l'espace de stockage utilisé
     */
    private long getStorageUsed() {
        // À implémenter selon votre système de stockage
        return 0L; // Placeholder
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
        // Note: Ces statistiques nécessiteraient des timestamps appropriés dans les entités
        // Pour l'instant, on retourne des valeurs par défaut
        stats.put("newUsersLast7Days", 0L);
        stats.put("newClientsLast7Days", 0L);
        stats.put("newCasesLast7Days", 0L);
        stats.put("newDocumentsLast7Days", 0L);
        stats.put("loginCountLast7Days", 0L);
        
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
