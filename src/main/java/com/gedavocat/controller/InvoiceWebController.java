package com.gedavocat.controller;

import com.gedavocat.model.Client;
import com.gedavocat.model.Invoice;
import com.gedavocat.model.InvoiceItem;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.InvoiceRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.InvoiceService;
import com.gedavocat.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contrôleur Web pour les pages de facturation
 */
@Slf4j
@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceWebController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Value("${app.upload.dir:/opt/gedavocat/uploads/documents}")
    private String uploadDir;

    /**
     * Résout l'utilisateur courant depuis son email (principal name)
     */
    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    /** Renvoie les clients selon le rôle : tous pour ADMIN, ou ceux de l'avocat. */
    private List<Client> getClientsForUser(Authentication auth, String lawyerId) {
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
            User user = getCurrentUser(authentication);
            String lawyerId = user.getId();
            
            var invoices = invoiceService.getInvoicesByLawyer(lawyerId);
            
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
            model.addAttribute("clients", getClientsForUser(authentication, lawyerId));
            model.addAttribute("today", LocalDate.now());
            
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
            var clients = getClientsForUser(authentication, lawyerId);
            model.addAttribute("clients", clients);
            return "invoices/new";
        } catch (Exception e) {
            model.addAttribute("error", "Erreur lors du chargement du formulaire");
            return "redirect:/invoices";
        }
    }

    /**
     * Importer une facture PDF existante
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
    public String importInvoice(
            Authentication authentication,
            @RequestParam("clientId") String clientId,
            @RequestParam("invoiceNumber") String invoiceNumber,
            @RequestParam("invoiceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
            @RequestParam(value = "dueDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam("totalHT") BigDecimal totalHT,
            @RequestParam(value = "tvaRate", defaultValue = "0") double tvaRate,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "file", required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) {
        try {
            Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client introuvable"));

            // Vérifier unicité du numéro de facture
            if (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
                redirectAttributes.addFlashAttribute("importError", "Le numéro de facture " + invoiceNumber + " existe déjà.");
                return "redirect:/invoices";
            }

            // Sauvegarder le fichier si fourni
            String docUrl = null;
            if (file != null && !file.isEmpty()) {
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename()
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
                Path invoicesDir = Paths.get(uploadDir).getParent().resolve("invoices");
                Files.createDirectories(invoicesDir);
                Path dest = invoicesDir.resolve(filename);
                file.transferTo(dest.toFile());
                docUrl = "/uploads/invoices/" + filename;
            }

            // Calculer TVA et TTC
            BigDecimal tva = totalHT.multiply(BigDecimal.valueOf(tvaRate / 100.0))
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalTTC = totalHT.add(tva).setScale(2, RoundingMode.HALF_UP);

            // Créer la facture
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber(invoiceNumber);
            invoice.setClient(client);
            invoice.setInvoiceDate(invoiceDate);
            invoice.setDueDate(dueDate);
            invoice.setStatus(Invoice.InvoiceStatus.SENT);
            invoice.setTotalHT(totalHT.setScale(2, RoundingMode.HALF_UP));
            invoice.setTotalTVA(tva);
            invoice.setTotalTTC(totalTTC);
            invoice.setNotes(notes);
            invoice.setDocumentUrl(docUrl);

            // Ajouter une ligne synthétique
            InvoiceItem item = new InvoiceItem();
            item.setDescription("Honoraires d'avocat");
            item.setQuantity(BigDecimal.ONE);
            item.setUnitPriceHT(totalHT);
            item.setTvaRate(BigDecimal.valueOf(tvaRate));
            item.setDisplayOrder(1);
            invoice.addItem(item);

            invoiceRepository.save(invoice);

            redirectAttributes.addFlashAttribute("message", "Facture " + invoiceNumber + " importée avec succès.");
        } catch (IOException e) {
            log.error("Erreur lors de la sauvegarde du fichier", e);
            redirectAttributes.addFlashAttribute("importError", "Erreur lors de l'upload du fichier : " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de l'import de la facture", e);
            redirectAttributes.addFlashAttribute("importError", "Erreur : " + e.getMessage());
        }
        return "redirect:/invoices";
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

