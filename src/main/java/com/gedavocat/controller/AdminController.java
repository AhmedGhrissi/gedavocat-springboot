package com.gedavocat.controller;

import com.gedavocat.dto.SystemMetricsDTO;
import com.gedavocat.model.Barreau;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.service.AdminMetricsService;
import com.gedavocat.service.BarreauService;
import com.gedavocat.service.ClientInvitationService;
import com.gedavocat.service.LogService;
import com.gedavocat.service.MaintenanceService;
import com.gedavocat.service.UserService;
import com.gedavocat.security.monitoring.SecurityMonitoringService;
import com.gedavocat.security.monitoring.SecurityMonitoringService.SecurityMonitoringReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@SuppressWarnings("null")
public class AdminController {

    private final AdminMetricsService metricsService;
    private final LogService logService;
    private final UserService userService;
    private final MaintenanceService maintenanceService;
    private final ClientRepository clientRepository;
    private final ClientInvitationService invitationService;
    private final SecurityMonitoringService securityMonitoringService;
    private final BarreauService barreauService;

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
            log.error("Erreur chargement métriques dashboard", e);
            model.addAttribute("error", "Erreur lors du chargement des métriques");
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
            log.error("Erreur lecture des logs", e);
            model.addAttribute("error", "Erreur lors de la lecture des logs");
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
            log.error("Erreur chargement utilisateurs", e);
            model.addAttribute("error", "Erreur lors du chargement des utilisateurs");
            return "admin/users";
        }
    }

    /**
     * Retourne les infos d'un utilisateur en JSON (pour la modale de détail)
     */
    @GetMapping(value = "/users/{id}", produces = "application/json")
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
            log.error("Erreur modification utilisateur {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la modification de l'utilisateur");
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
            log.error("Erreur suppression utilisateur {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression de l'utilisateur");
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
            log.error("Erreur toggle block utilisateur {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Une erreur est survenue lors de l'opération");
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
     * Page de monitoring de sécurité
     */
    @GetMapping("/security")
    public String security(Model model) {
        try {
            SecurityMonitoringReport report = securityMonitoringService.generateSecurityReport();
            if (report != null) {
                model.addAttribute("report", report);
                model.addAttribute("metrics", report.getMetrics());
                model.addAttribute("topIPs", report.getTopSuspiciousIPs());
                model.addAttribute("status", report.getOverallStatus());
                model.addAttribute("timestamp", report.getTimestamp());
            }
            return "admin/security-monitoring";
        } catch (Exception e) {
            log.error("Erreur chargement monitoring sécurité", e);
            model.addAttribute("error", "Erreur lors du chargement du monitoring de sécurité");
            return "admin/security-monitoring";
        }
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
            log.error("Erreur création utilisateur", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur lors de la création de l'utilisateur");
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
            log.error("Erreur envoi invitation client {}", clientId, e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur lors de l'envoi de l'invitation");
        }
        return "redirect:/admin/users";
    }

    /**
     * Page de gestion des barreaux
     */
    @GetMapping("/barreaux")
    public String barreaux(Model model,
                          @RequestParam(required = false) String search,
                          @RequestParam(required = false) String region) {
        try {
            if (search != null && !search.isBlank()) {
                model.addAttribute("barreaux", barreauService.searchBarreaux(search));
                model.addAttribute("filterSearch", search);
            } else if (region != null && !region.isBlank()) {
                model.addAttribute("barreaux", barreauService.getBarreauxByRegion(region));
                model.addAttribute("filterRegion", region);
            } else {
                model.addAttribute("barreaux", barreauService.getAllBarreaux());
            }
            
            model.addAttribute("regions", barreauService.getAllRegions());
            model.addAttribute("stats", barreauService.getStatistiques());
            
            return "admin/barreaux";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des barreaux : " + e.getMessage());
            return "admin/barreaux";
        }
    }

    /**
     * Crée un nouveau barreau
     */
    @PostMapping("/barreaux/create")
    public String createBarreau(
            @RequestParam String barreau,
            @RequestParam String region,
            @RequestParam String villeSiege,
            @RequestParam String courAppel,
            @RequestParam(required = false) String tribunalJudiciaire,
            RedirectAttributes redirectAttributes) {
        try {
            Barreau newBarreau = new Barreau();
            newBarreau.setBarreau(barreau);
            newBarreau.setRegion(region);
            newBarreau.setVilleSiege(villeSiege);
            newBarreau.setCourAppel(courAppel);
            newBarreau.setTribunalJudiciaire(tribunalJudiciaire);
            newBarreau.setActif(true);
            
            barreauService.createBarreau(newBarreau);
            redirectAttributes.addFlashAttribute("success",
                    "Barreau " + barreau + " créé avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Erreur lors de la création : " + e.getMessage());
        }
        return "redirect:/admin/barreaux";
    }

    /**
     * Met à jour un barreau existant
     */
    @PostMapping("/barreaux/{id}/edit")
    public String editBarreau(
            @PathVariable Long id,
            @RequestParam String barreau,
            @RequestParam String region,
            @RequestParam String villeSiege,
            @RequestParam String courAppel,
            @RequestParam(required = false) String tribunalJudiciaire,
            @RequestParam(defaultValue = "true") boolean actif,
            RedirectAttributes redirectAttributes) {
        try {
            Barreau updatedBarreau = new Barreau();
            updatedBarreau.setBarreau(barreau);
            updatedBarreau.setRegion(region);
            updatedBarreau.setVilleSiege(villeSiege);
            updatedBarreau.setCourAppel(courAppel);
            updatedBarreau.setTribunalJudiciaire(tribunalJudiciaire);
            updatedBarreau.setActif(actif);
            
            barreauService.updateBarreau(id, updatedBarreau);
            redirectAttributes.addFlashAttribute("success",
                    "Barreau " + barreau + " mis à jour avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Erreur lors de la mise à jour : " + e.getMessage());
        }
        return "redirect:/admin/barreaux";
    }

    /**
     * Supprime un barreau
     */
    @PostMapping("/barreaux/{id}/delete")
    public String deleteBarreau(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            barreauService.getBarreauById(id).ifPresentOrElse(b -> {
                String name = b.getBarreau();
                barreauService.deleteBarreau(id);
                redirectAttributes.addFlashAttribute("success", "Barreau " + name + " supprimé");
            }, () -> redirectAttributes.addFlashAttribute("error", "Barreau introuvable"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur suppression : " + e.getMessage());
        }
        return "redirect:/admin/barreaux";
    }

    /**
     * Active ou désactive un barreau
     */
    @PostMapping("/barreaux/{id}/toggle-active")
    public String toggleBarreauActive(
            @PathVariable Long id,
            @RequestParam boolean actif,
            RedirectAttributes redirectAttributes) {
        try {
            barreauService.getBarreauById(id).ifPresentOrElse(b -> {
                Barreau updated = new Barreau();
                updated.setActif(actif);
                barreauService.updateBarreau(id, updated);
                String action = actif ? "activé" : "désactivé";
                redirectAttributes.addFlashAttribute("success",
                        "Barreau " + b.getBarreau() + " " + action + " avec succès");
            }, () -> redirectAttributes.addFlashAttribute("error", "Barreau introuvable"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur : " + e.getMessage());
        }
        return "redirect:/admin/barreaux";
    }
}

