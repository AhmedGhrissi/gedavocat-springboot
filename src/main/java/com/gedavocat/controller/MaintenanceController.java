package com.gedavocat.controller;

import com.gedavocat.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur pour la page de maintenance publique et l'API admin.
 */
@Controller
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    /**
     * Page de maintenance publique (accessible sans authentification)
     */
    @GetMapping("/maintenance")
    public String maintenancePage(Model model) {
        // Si maintenance désactivée, renvoyer vers l'accueil
        if (!maintenanceService.isMaintenanceEnabled()) {
            return "redirect:/";
        }
        return "maintenance";
    }

    /**
     * Obtenir le statut du mode maintenance (ADMIN uniquement)
     */
    @GetMapping("/api/admin/maintenance/status")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "enabled", maintenanceService.isMaintenanceEnabled()
        ));
    }

    /**
     * Activer / désactiver le mode maintenance (ADMIN uniquement)
     */
    @PostMapping("/api/admin/maintenance/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggle() {
        boolean newState = maintenanceService.toggle();
        return ResponseEntity.ok(Map.of(
            "enabled", newState,
            "message", newState
                ? "🔴 Mode maintenance activé — le site est inaccessible aux utilisateurs."
                : "🟢 Mode maintenance désactivé — le site est de nouveau accessible."
        ));
    }
}
