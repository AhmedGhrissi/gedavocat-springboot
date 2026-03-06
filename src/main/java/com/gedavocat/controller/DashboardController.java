package com.gedavocat.controller;

import com.gedavocat.model.AuditLog;
import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.AuditLogRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.SignatureRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.model.Signature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'CLIENT', 'LAWYER_SECONDARY', 'HUISSIER')")
@Slf4j
public class DashboardController {

    private final UserRepository     userRepository;
    private final ClientRepository   clientRepository;
    private final CaseRepository     caseRepository;
    private final DocumentRepository documentRepository;
    private final SignatureRepository signatureRepository;
    private final AuditLogRepository  auditLogRepository;

    @GetMapping("/")
    @PreAuthorize("permitAll()")
    public String home() {
        return "landing";
    }

    @GetMapping("/welcome")
    @PreAuthorize("permitAll()")
    public String welcome() {
        return "landing";
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur non trouvé"));

        model.addAttribute("user", user);

        // Rediriger les admins vers leur panneau d'administration
        if (user.isAdmin()) {
            return "redirect:/admin";
        } else if (user.isLawyer()) {
            buildLawyerDashboard(user, model);
        } else if (user.isClient()) {
            // B19 FIX : Les clients sont redirigés vers leur portail dédié
            return "redirect:/my-cases";
        } else if (user.isCollaborator()) {
            // Les collaborateurs ont leur propre portail
            return "redirect:/my-cases-collab";
        } else if (user.isHuissier()) {
            // Les huissiers ont leur propre portail
            return "redirect:/my-cases-huissier";
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

        // Taille totale des documents (en octets) - exclut les documents supprimés
        long totalStorage = 0;
        try {
            totalStorage = documentRepository.sumFileSizeNonDeleted();
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
        // SEC-TENANT FIX : documentsCount scopé au lawyer (pas global)
        long lawyerDocumentsCount = 0;
        try {
            lawyerDocumentsCount = documentRepository.countNonDeletedByLawyerId(user.getId());
        } catch (Exception e) {
            // Fallback si la méthode n'existe pas encore
            try { lawyerDocumentsCount = documentRepository.countNonDeleted(); } catch (Exception ignored) {}
        }
        model.addAttribute("clientsCount",     clients.size());
        model.addAttribute("casesCount",       cases.size());
        model.addAttribute("documentsCount",   lawyerDocumentsCount);
        model.addAttribute("signaturesCount",  signatureRepository.countPendingByUserId(user.getId()));
        
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
        
        // Signatures récentes (5 dernières, tous statuts)
        List<Signature> allSignatures = signatureRepository.findByRequestedByIdWithCase(user.getId());
        List<Signature> recentSignatures = allSignatures.stream()
                .limit(5)
                .toList();
        long pendingSignatures = allSignatures.stream()
                .filter(s -> s.getStatus() == Signature.SignatureStatus.PENDING).count();
        long signedSignatures = allSignatures.stream()
                .filter(s -> s.getStatus() == Signature.SignatureStatus.SIGNED).count();
        model.addAttribute("recentSignatures", recentSignatures);
        model.addAttribute("pendingSignatures", pendingSignatures);
        model.addAttribute("signedSignatures", signedSignatures);
        
        // Objet metrics pour le nouveau template dashboard-institutional
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("activeCases", activeCases);
        metrics.put("unpaidInvoices", 0); // Factures impayées - à implémenter avec module facturation
        metrics.put("pendingSignatures", pendingSignatures);
        metrics.put("upcomingDeadlines", 0); // Échéances proches - à implémenter avec module calendrier
        model.addAttribute("metrics", metrics);
        
        // Activités récentes depuis l'audit log
        model.addAttribute("recentActivities", buildRecentActivities(user.getId()));
    }

    // ------------------------------------------------------------------
    @SuppressWarnings("unused") // Gardée pour réactivation future du dashboard client
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
        
        // Objet metrics pour le nouveau template dashboard-institutional
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("activeCases", activeCases);
        metrics.put("unpaidInvoices", 0);
        metrics.put("pendingSignatures", 0);
        metrics.put("upcomingDeadlines", 0);
        model.addAttribute("metrics", metrics);
        
        // Activités récentes depuis l'audit log
        model.addAttribute("recentActivities", buildRecentActivities(user.getId()));
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
        
        // Objet metrics pour le nouveau template dashboard-institutional
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("activeCases", 0);
        metrics.put("unpaidInvoices", 0);
        metrics.put("pendingSignatures", 0);
        metrics.put("upcomingDeadlines", 0);
        model.addAttribute("metrics", metrics);
        
        // Activités récentes vides
        model.addAttribute("recentActivities", new ArrayList<>());
    }
    
    // ------------------------------------------------------------------
    /** Charge les 10 dernières entrées d'audit pour cet utilisateur */
    private List<RecentActivity> buildRecentActivities(String userId) {
        try {
            List<AuditLog> logs = auditLogRepository
                .findByUserId(userId, PageRequest.of(0, 10))
                .getContent();
            List<RecentActivity> activities = new ArrayList<>();
            for (AuditLog log : logs) {
                String icon = switch (log.getAction()) {
                    case "CLIENT_CREATED"  -> "👤";
                    case "CASE_CREATED"    -> "📂";
                    case "CASE_UPDATED"   -> "✏️";
                    case "DOCUMENT_UPLOAD" -> "📄";
                    case "USER_LOGIN"      -> "🔐";
                    default               -> "📋";
                };
                String time = log.getCreatedAt() != null
                        ? log.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                        : "";
                activities.add(new RecentActivity(
                    icon + " " + log.getAction().replace("_", " "),
                    log.getDetails() != null ? log.getDetails() : "",
                    time
                ));
            }
            return activities;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------------
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