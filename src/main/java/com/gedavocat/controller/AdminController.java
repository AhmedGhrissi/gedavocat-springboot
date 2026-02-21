package com.gedavocat.controller;

import com.gedavocat.dto.SystemMetricsDTO;
import com.gedavocat.model.Client;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.service.AdminMetricsService;
import com.gedavocat.service.ClientInvitationService;
import com.gedavocat.service.LogService;
import com.gedavocat.service.MaintenanceService;
import com.gedavocat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final ClientRepository clientRepository;
    private final ClientInvitationService invitationService;

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
            model.addAttribute("activityStats", metricsService.getActivityStats());
            // Clients créés par les avocats sans compte utilisateur lié
            model.addAttribute("clientsWithoutAccount", clientRepository.findByClientUserIsNull());
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

    /**
     * Toggle maintenance via formulaire (CSRF-protected, fiable)
     */
    @PostMapping("/settings/toggle-maintenance")
    public String toggleMaintenance(RedirectAttributes redirectAttributes) {
        boolean newState = maintenanceService.toggle();
        redirectAttributes.addFlashAttribute("maintenanceMsg",
            newState ? "🔴 Mode maintenance activé — le site est inaccessible aux utilisateurs."
                     : "🟢 Mode maintenance désactivé — le site est de nouveau accessible.");
        return "redirect:/admin/settings";
    }

    /**
     * Crée un nouvel utilisateur (avocat ou client)
     */
    @PostMapping("/users/create")
    public String createUser(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role,
            RedirectAttributes redirectAttributes) {
        try {
            userService.createUser(firstName, lastName, email, password, role);
            String roleLabel = "LAWYER".equals(role) ? "Avocat" : "Client";
            redirectAttributes.addFlashAttribute("success",
                    roleLabel + " " + firstName + " " + lastName + " créé avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Erreur lors de la création : " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * Envoie une invitation de création de compte à un client sans compte
     */
    @PostMapping("/users/send-invitation")
    public String sendInvitation(@RequestParam String clientId, RedirectAttributes redirectAttributes) {
        try {
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new RuntimeException("Client introuvable : " + clientId));
            invitationService.sendInvitation(client, "Administrateur");
            redirectAttributes.addFlashAttribute("success",
                    "Invitation envoyée à " + client.getName() + " (" + client.getEmail() + ")");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Erreur lors de l'envoi de l'invitation : " + e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
