package com.gedavocat.service;

import com.gedavocat.dto.LogEntryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service pour la lecture et l'analyse des logs
 */
@Slf4j
@Service
public class LogService {

    @Value("${logging.file.name:logs/application.log}")
    private String LOG_FILE_PATH;
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(INFO|WARN|ERROR|DEBUG|TRACE)\\s+\\d+\\s+---\\s+\\[([^\\]]+)\\]\\s+([^:]+)\\s*:\\s*(.*)$"
    );

    /**
     * Récupère les dernières lignes de log
     */
    public List<LogEntryDTO> getRecentLogs(int maxLines) {
        File logFile = new File(LOG_FILE_PATH);
        
        if (!logFile.exists()) {
            log.warn("Fichier de log non trouvé: {}", LOG_FILE_PATH);
            return Collections.emptyList();
        }

        List<LogEntryDTO> logs = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            List<String> allLines = new ArrayList<>();
            String line;
            
            // Lire toutes les lignes
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }
            
            // Prendre les dernières lignes
            int start = Math.max(0, allLines.size() - maxLines);
            List<String> lastLines = allLines.subList(start, allLines.size());
            
            // Parser les lignes
            for (String logLine : lastLines) {
                LogEntryDTO entry = parseLogLine(logLine);
                if (entry != null) {
                    logs.add(entry);
                }
            }
            
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier de log", e);
        }
        
        return logs;
    }

    /**
     * Recherche dans les logs
     */
    public List<LogEntryDTO> searchLogs(String keyword, String level, int maxResults) {
        File logFile = new File(LOG_FILE_PATH);
        
        if (!logFile.exists()) {
            return Collections.emptyList();
        }

        List<LogEntryDTO> results = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            int count = 0;
            
            while ((line = reader.readLine()) != null && count < maxResults) {
                LogEntryDTO entry = parseLogLine(line);
                
                if (entry != null) {
                    boolean matchKeyword = keyword == null || keyword.isEmpty() || 
                                          entry.getMessage().toLowerCase().contains(keyword.toLowerCase());
                    boolean matchLevel = level == null || level.isEmpty() || 
                                        entry.getLevel().equalsIgnoreCase(level);
                    
                    if (matchKeyword && matchLevel) {
                        results.add(entry);
                        count++;
                    }
                }
            }
            
        } catch (IOException e) {
            log.error("Erreur lors de la recherche dans les logs", e);
        }
        
        return results;
    }

    /**
     * Parse une ligne de log
     */
    private LogEntryDTO parseLogLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        
        if (matcher.matches()) {
            try {
                LocalDateTime timestamp = LocalDateTime.parse(
                    matcher.group(1), 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                );
                
                return new LogEntryDTO(
                    timestamp,
                    matcher.group(2),  // level
                    matcher.group(4),  // logger
                    matcher.group(5),  // message
                    matcher.group(3),  // thread
                    null               // exception (à parser si nécessaire)
                );
            } catch (DateTimeParseException e) {
                log.warn("Impossible de parser la date du log: {}", line);
            }
        }
        
        // Si le pattern ne correspond pas, retourner une entrée simple
        return new LogEntryDTO(
            LocalDateTime.now(),
            "INFO",
            "unknown",
            line,
            "main",
            null
        );
    }

    /**
     * Récupère les statistiques des logs
     */
    public LogStatistics getLogStatistics() {
        File logFile = new File(LOG_FILE_PATH);
        
        LogStatistics stats = new LogStatistics();
        
        if (!logFile.exists()) {
            return stats;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR")) {
                    stats.incrementErrors();
                } else if (line.contains("WARN")) {
                    stats.incrementWarnings();
                } else if (line.contains("INFO")) {
                    stats.incrementInfos();
                }
                stats.incrementTotal();
            }
            
            stats.setFileSize(logFile.length());
            
        } catch (IOException e) {
            log.error("Erreur lors du calcul des statistiques de log", e);
        }
        
        return stats;
    }

    /**
     * Classe interne pour les statistiques de logs
     */
    public static class LogStatistics {
        private long total;
        private long errors;
        private long warnings;
        private long infos;
        private long fileSize;

        public void incrementTotal() { total++; }
        public void incrementErrors() { errors++; }
        public void incrementWarnings() { warnings++; }
        public void incrementInfos() { infos++; }

        // Getters et setters
        public long getTotal() { return total; }
        public long getErrors() { return errors; }
        public long getWarnings() { return warnings; }
        public long getInfos() { return infos; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    }
}
