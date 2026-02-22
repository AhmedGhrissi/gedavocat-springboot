package com.gedavocat.controller;

import com.gedavocat.dto.SystemMetricsDTO;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.service.AdminMetricsService;
import com.gedavocat.service.ClientInvitationService;
import com.gedavocat.service.LogService;
import com.gedavocat.service.MaintenanceService;
import com.gedavocat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * Page de gestion des utilisateurs avec filtres
     */
    @GetMapping("/users")
    public String users(Model model,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String role,
                        @RequestParam(required = false) String status) {
        try {
            boolean hasFilter = (search != null && !search.isBlank())
                             || (role != null && !role.isBlank())
                             || (status != null && !status.isBlank());
            if (hasFilter) {
                model.addAttribute("users", userService.findWithFilters(search, role, status));
            } else {
                model.addAttribute("users", userService.getAllUsers());
            }
            model.addAttribute("activityStats", metricsService.getActivityStats());
            model.addAttribute("clientsWithoutAccount", clientRepository.findByClientUserIsNull());
            // Pour pré-remplir les filtres après soumission
            model.addAttribute("filterSearch", search != null ? search : "");
            model.addAttribute("filterRole",   role   != null ? role   : "");
            model.addAttribute("filterStatus", status != null ? status : "");
            return "admin/users";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des utilisateurs : " + e.getMessage());
            return "admin/users";
        }
    }

    /**
     * Retourne les infos d'un utilisateur en JSON (pour la modale de détail)
     */
    @GetMapping("/users/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserJson(@PathVariable String id) {
        return userService.getUserById(id).map(u -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",               u.getId());
            data.put("firstName",        u.getFirstName());
            data.put("lastName",         u.getLastName());
            data.put("email",            u.getEmail());
            data.put("role",             u.getRole() != null ? u.getRole().name() : "");
            data.put("roleLabel",        u.getRole() != null ? u.getRole().getDisplayName() : "");
            data.put("accountEnabled",   u.isAccountEnabled());
            data.put("subscriptionPlan", u.getSubscriptionPlan() != null ? u.getSubscriptionPlan().name() : "");
            data.put("subscriptionStatus", u.getSubscriptionStatus() != null ? u.getSubscriptionStatus().name() : "");
            data.put("maxClients",       u.getMaxClients());
            data.put("phone",            u.getPhone() != null ? u.getPhone() : "");
            data.put("barNumber",        u.getBarNumber() != null ? u.getBarNumber() : "");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            data.put("createdAt",  u.getCreatedAt()  != null ? u.getCreatedAt().format(fmt)  : "");
            data.put("updatedAt",  u.getUpdatedAt()  != null ? u.getUpdatedAt().format(fmt)  : "");
            data.put("accessEndsAt", u.getAccessEndsAt() != null ? u.getAccessEndsAt().format(fmt) : "");
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Modifie les infos d'un utilisateur (admin)
     */
    @PostMapping("/users/{id}/edit")
    public String editUser(@PathVariable String id,
                           @RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam String email,
                           @RequestParam String role,
                           @RequestParam(defaultValue = "false") boolean accountEnabled,
                           RedirectAttributes redirectAttributes) {
        try {
            User updated = userService.updateUserAdmin(id, firstName, lastName, email, role, accountEnabled);
            redirectAttributes.addFlashAttribute("success",
                "Utilisateur " + updated.getFirstName() + " " + updated.getLastName() + " mis à jour");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur modification : " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * Supprime un utilisateur
     */
    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            userService.getUserById(id).ifPresentOrElse(u -> {
                String name = u.getFirstName() + " " + u.getLastName();
                userService.deleteUser(id);
                redirectAttributes.addFlashAttribute("success", "Utilisateur " + name + " supprimé");
            }, () -> redirectAttributes.addFlashAttribute("error", "Utilisateur introuvable"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur suppression : " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * Bloque ou débloque un utilisateur
     */
    @PostMapping("/users/{id}/toggle-block")
    public String toggleBlock(@PathVariable String id,
                              @RequestParam boolean block,
                              RedirectAttributes redirectAttributes) {
        try {
            User u = userService.blockUser(id, block);
            String action = block ? "bloqué" : "débloqué";
            redirectAttributes.addFlashAttribute("success",
                "Utilisateur " + u.getFirstName() + " " + u.getLastName() + " " + action + " avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur : " + e.getMessage());
        }
        return "redirect:/admin/users";
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
