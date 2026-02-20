package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.InvoiceService;
import com.gedavocat.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

/**
 * Contrôleur Web pour les pages de facturation
 */
@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final UserRepository userRepository;

    /**
     * Résout l'utilisateur courant depuis son email (principal name)
     */
    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    /**
     * Affiche la liste des factures
     */
    /** Renvoie les clients selon le rôle : tous pour ADMIN, ou ceux de l'avocat. */
    private List<com.gedavocat.model.Client> getClientsForUser(Authentication auth, String lawyerId) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? clientService.getAllClients() : clientService.getClientsByLawyer(lawyerId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    public String index(Model model, Authentication authentication,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String client) {
        try {
            String lawyerId = getCurrentUser(authentication).getId();
            
            // Récupérer toutes les factures de l'avocat
            var invoices = invoiceService.getInvoicesByLawyer(lawyerId);
            
            // Calculer les statistiques
            long paidCount = invoices.stream().filter(i -> "PAID".equals(i.getStatus().name())).count();
            long pendingCount = invoices.stream().filter(i -> "SENT".equals(i.getStatus().name())).count();
            long overdueCount = invoices.stream().filter(i -> i.isOverdue()).count();
            double totalAmount = invoices.stream()
                .filter(i -> "PAID".equals(i.getStatus().name()))
                .mapToDouble(i -> i.getTotalTTC().doubleValue())
                .sum();
            
            model.addAttribute("invoices", invoices);
            model.addAttribute("paidCount", paidCount);
            model.addAttribute("pendingCount", pendingCount);
            model.addAttribute("overdueCount", overdueCount);
            model.addAttribute("totalAmount", totalAmount);
            
            return "invoices/index";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement des factures");
            return "invoices/index";
        }
    }

    /**
     * Affiche le formulaire de création d'une facture
     */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    public String newInvoice(Model model, Authentication authentication) {
        try {
            String lawyerId = getCurrentUser(authentication).getId();
            
            // Récupérer la liste des clients (tous pour ADMIN, sinon ceux de l'avocat)
            var clients = getClientsForUser(authentication, lawyerId);
            model.addAttribute("clients", clients);
            
            return "invoices/new";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement du formulaire");
            return "redirect:/invoices";
        }
    }

    /**
     * Affiche une facture spécifique
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT')")
    public String show(@PathVariable String id, Model model) {
        try {
            var invoice = invoiceService.getInvoiceById(id);
            model.addAttribute("invoice", invoice);
            return "invoices/show";
        } catch (Exception e) {
            model.addAttribute("error", "Facture non trouvée");
            return "redirect:/invoices";
        }
    }

    /**
     * Affiche le formulaire d'édition d'une facture
     */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    public String edit(@PathVariable String id, Model model, Authentication authentication) {
        try {
            String lawyerId = getCurrentUser(authentication).getId();
            
            var invoice = invoiceService.getInvoiceById(id);
            var clients = getClientsForUser(authentication, lawyerId);
            
            model.addAttribute("invoice", invoice);
            model.addAttribute("clients", clients);
            
            return "invoices/edit";
        } catch (Exception e) {
            model.addAttribute("error", "Facture non trouvée");
            return "redirect:/invoices";
        }
    }

    /**
     * Page pour les clients - mes factures
     */
    @GetMapping("/my-invoices")
    @PreAuthorize("hasRole('CLIENT')")
    public String myInvoices(Model model, Authentication authentication) {
        try {
            User clientUser = getCurrentUser(authentication);
            // Trouver le Client lié à ce User
            var clientOpt = clientService.findByClientUser(clientUser.getId());
            if (clientOpt.isPresent()) {
                var invoices = invoiceService.getInvoicesByClient(clientOpt.get().getId());
                model.addAttribute("invoices", invoices);
            } else {
                model.addAttribute("invoices", java.util.Collections.emptyList());
            }
            return "invoices/my-invoices";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement de vos factures");
            return "invoices/my-invoices";
        }
    }
}
