package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final UserRepository     userRepository;
    private final ClientRepository   clientRepository;
    private final CaseRepository     caseRepository;
    private final DocumentRepository documentRepository;

    @GetMapping("/")
    public String home() {
        return "landing";
    }

    @GetMapping("/welcome")
    public String welcome() {
        return "landing";
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        model.addAttribute("user", user);

        // Rediriger les admins vers leur panneau d'administration
        if (user.isAdmin()) {
            return "redirect:/admin";
        } else if (user.isLawyer()) {
            buildLawyerDashboard(user, model);
        } else if (user.isClient()) {
            buildClientDashboard(user, model);
        } else {
            buildEmptyDashboard(model);
        }

        return "dashboard/index";
    }

    // ------------------------------------------------------------------
    private void buildLawyerDashboard(User user, Model model) {

        List<Client> clients = clientRepository.findByLawyerId(user.getId());
        List<Case>   cases   = caseRepository.findByLawyerId(user.getId());
        cases.forEach(c -> { if (c.getClient() != null) c.getClient().getName(); });

        long openCases     = cases.stream().filter(c -> c.getStatus() == Case.CaseStatus.OPEN).count();
        long inProgressCases = cases.stream().filter(c -> c.getStatus() == Case.CaseStatus.IN_PROGRESS).count();
        long closedCases   = cases.stream().filter(c -> c.getStatus() == Case.CaseStatus.CLOSED).count();
        long archivedCases = cases.stream().filter(c -> c.getStatus() == Case.CaseStatus.ARCHIVED).count();
        
        // Dossiers actifs = OPEN + IN_PROGRESS
        long activeCases = openCases + inProgressCases;

        // Taille totale des documents (en octets)
        long totalStorage = 0;
        try {
            totalStorage = documentRepository.findAll().stream()
                    .mapToLong(d -> d.getFileSize() != null ? d.getFileSize() : 0)
                    .sum();
        } catch (Exception ignored) {}

        // 5 dossiers les plus récents
        List<Case> recentCases = new ArrayList<>(cases);
        recentCases.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        if (recentCases.size() > 5) recentCases = recentCases.subList(0, 5);

        // 5 clients les plus récents
        List<Client> recentClients = new ArrayList<>(clients);
        recentClients.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        if (recentClients.size() > 5) recentClients = recentClients.subList(0, 5);

        // Variables pour le template dashboard
        model.addAttribute("clientsCount",     clients.size());
        model.addAttribute("casesCount",       cases.size());
        model.addAttribute("documentsCount",   documentRepository.count());
        model.addAttribute("signaturesCount",  0); // À implémenter avec YouSign
        
        // Variables pour statistiques détaillées
        model.addAttribute("totalClients",   clients.size());
        model.addAttribute("openCases",      openCases);
        model.addAttribute("inProgressCases", inProgressCases);
        model.addAttribute("activeCases",    activeCases); // OPEN + IN_PROGRESS
        model.addAttribute("closedCases",    closedCases);
        model.addAttribute("archivedCases",  archivedCases);
        model.addAttribute("totalStorage",   totalStorage);
        model.addAttribute("recentCases",    recentCases);
        model.addAttribute("recentClients",  recentClients);
        
        // Activités récentes (mockées pour l'instant)
        model.addAttribute("recentActivities", createMockActivities());
    }

    // ------------------------------------------------------------------
    private void buildClientDashboard(User user, Model model) {

        List<Case> cases = new ArrayList<>();
        try {
            clientRepository.findByEmail(user.getEmail()).ifPresent(client ->
                cases.addAll(caseRepository.findByClientId(client.getId()))
            );
        } catch (Exception ignored) {}

        long openCases = cases.stream().filter(c -> c.getStatus() == Case.CaseStatus.OPEN).count();
        long inProgressCases = cases.stream().filter(c -> c.getStatus() == Case.CaseStatus.IN_PROGRESS).count();
        long activeCases = openCases + inProgressCases;

        // Variables pour le template dashboard
        model.addAttribute("clientsCount",     0); // Client ne voit pas les autres clients
        model.addAttribute("casesCount",       cases.size());
        model.addAttribute("documentsCount",   0); // À implémenter
        model.addAttribute("signaturesCount",  0); // À implémenter avec YouSign
        
        // Variables pour statistiques détaillées
        model.addAttribute("totalClients",   0);
        model.addAttribute("openCases",      openCases);
        model.addAttribute("inProgressCases", inProgressCases);
        model.addAttribute("activeCases",    activeCases);
        model.addAttribute("closedCases",    0);
        model.addAttribute("archivedCases",  0);
        model.addAttribute("totalStorage",   0L);
        model.addAttribute("recentCases",    cases);
        model.addAttribute("recentClients",  new ArrayList<>());
        
        // Activités récentes (mockées pour l'instant)
        model.addAttribute("recentActivities", createMockActivities());
    }

    // ------------------------------------------------------------------
    private void buildEmptyDashboard(Model model) {
        // Variables pour le template dashboard
        model.addAttribute("clientsCount",     0);
        model.addAttribute("casesCount",       0);
        model.addAttribute("documentsCount",   0);
        model.addAttribute("signaturesCount",  0);
        
        // Variables pour statistiques détaillées
        model.addAttribute("totalClients",   0);
        model.addAttribute("openCases",      0);
        model.addAttribute("inProgressCases", 0);
        model.addAttribute("activeCases",    0);
        model.addAttribute("closedCases",    0);
        model.addAttribute("archivedCases",  0);
        model.addAttribute("totalStorage",   0L);
        model.addAttribute("recentCases",    new ArrayList<>());
        model.addAttribute("recentClients",  new ArrayList<>());
        
        // Activités récentes (vides)
        model.addAttribute("recentActivities", new ArrayList<>());
    }
    
    // ------------------------------------------------------------------
    // Méthode pour créer des activités récentes mockées
    private List<RecentActivity> createMockActivities() {
        List<RecentActivity> activities = new ArrayList<>();
        // Pour l'instant, on retourne une liste vide
        // À implémenter avec de vraies activités
        return activities;
    }
    
    // Classe interne pour les activités récentes
    public static class RecentActivity {
        private String title;
        private String description;
        private String time;
        
        public RecentActivity(String title, String description, String time) {
            this.title = title;
            this.description = description;
            this.time = time;
        }
        
        // Getters
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getTime() { return time; }
    }
}
