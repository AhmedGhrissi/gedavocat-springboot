package com.gedavocat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO pour les métriques système de l'admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetricsDTO {
    
    // Métriques système
    private String javaVersion;
    private String javaVendor;
    private String jvmName;
    private String jvmVersion;
    private String osName;
    private String osVersion;
    private String osArch;
    private String workingDirectory;
    private String userName;
    private long totalMemory;
    private long freeMemory;
    private long usedMemory;
    private long maxMemory;
    private double memoryUsagePercent;
    private String usedMemoryFormatted;
    private String maxMemoryFormatted;
    private int availableProcessors;
    private double cpuUsage;
    private int threadCount;
    private int peakThreadCount;
    private long loadedClasses;
    
    // Métriques application
    private long uptime;
    private LocalDateTime startTime;
    private String applicationVersion;
    private String springBootVersion;
    
    // Métriques base de données
    private String databaseProductName;
    private String databaseVersion;
    private String databaseUrl;
    private int activeConnections;
    private int maxConnections;
    
    // Statistiques application
    private long totalUsers;
    private long totalClients;
    private long totalCases;
    private long totalDocuments;
    private long totalInvoices;
    private long storageUsed;
    private long storageLimit;
    private double storageUsagePercent;
    private String storageUsedFormatted;
    private String storageLimitFormatted;
    
    // Activité récente
    private long usersLastHour;
    private long usersLastDay;
    private long documentsUploadedToday;
    
    // Santé système
    private String status;
    private Map<String, Object> healthDetails;
}
