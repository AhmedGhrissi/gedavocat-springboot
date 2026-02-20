package com.gedavocat.controller;

import com.gedavocat.dto.SystemMetricsDTO;
import com.gedavocat.service.AdminMetricsService;
import com.gedavocat.service.LogService;
import com.gedavocat.service.MaintenanceService;
import com.gedavocat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Contrôleur pour le panneau d'administration
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminMetricsService metricsService;
    private final LogService logService;
    private final UserService userService;
    private final MaintenanceService maintenanceService;

    /**
     * Dashboard principal de l'admin
     */
    @GetMapping
    public String dashboard(Model model) {
        try {
            SystemMetricsDTO metrics = metricsService.getSystemMetrics();
            model.addAttribute("metrics", metrics);
            model.addAttribute("uptime", metricsService.formatUptime(metrics.getUptime()));
            
            // Statistiques d'activité
            model.addAttribute("activityStats", metricsService.getActivityStats());
            
            return "admin/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des métriques: " + e.getMessage());
            return "admin/dashboard";
        }
    }

    /**
     * Page d'informations système
     */
    @GetMapping("/system")
    public String systemInfo(Model model) {
        try {
            SystemMetricsDTO metrics = metricsService.getSystemMetrics();
            model.addAttribute("metrics", metrics);
            model.addAttribute("uptime", metricsService.formatUptime(metrics.getUptime()));
            
            return "admin/system";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des informations système");
            return "admin/system";
        }
    }

    /**
     * Page de consultation des logs
     */
    @GetMapping("/logs")
    public String logs(Model model,
                      @RequestParam(defaultValue = "500") int maxLines,
                      @RequestParam(required = false) String search,
                      @RequestParam(required = false) String level) {
        try {
            if (search != null && !search.isEmpty()) {
                model.addAttribute("logs", logService.searchLogs(search, level, maxLines));
                model.addAttribute("searchQuery", search);
            } else {
                model.addAttribute("logs", logService.getRecentLogs(maxLines));
            }
            
            model.addAttribute("logStats", logService.getLogStatistics());
            model.addAttribute("maxLines", maxLines);
            model.addAttribute("level", level);
            
            return "admin/logs";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors de la lecture des logs: " + e.getMessage());
            return "admin/logs";
        }
    }

    /**
     * Page de gestion des utilisateurs
     */
    @GetMapping("/users")
    public String users(Model model) {
        try {
            model.addAttribute("users", userService.getAllUsers());
            return "admin/users";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des utilisateurs");
            return "admin/users";
        }
    }

    /**
     * Page de gestion de la base de données
     */
    @GetMapping("/database")
    public String database(Model model) {
        try {
            SystemMetricsDTO metrics = metricsService.getSystemMetrics();
            model.addAttribute("metrics", metrics);
            
            return "admin/database";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des informations de la base de données");
            return "admin/database";
        }
    }

    /**
     * Page de statistiques avancées
     */
    @GetMapping("/statistics")
    public String statistics(Model model) {
        try {
            SystemMetricsDTO metrics = metricsService.getSystemMetrics();
            model.addAttribute("metrics", metrics);
            model.addAttribute("activityStats", metricsService.getActivityStats());
            
            return "admin/statistics";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des statistiques");
            return "admin/statistics";
        }
    }

    /**
     * Page de configuration système
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("maintenanceEnabled", maintenanceService.isMaintenanceEnabled());
        return "admin/settings";
    }
}
