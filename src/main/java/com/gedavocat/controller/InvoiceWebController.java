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
import com.gedavocat.service.EmailService;
import com.gedavocat.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@SuppressWarnings("null")
public class InvoiceWebController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final EmailService emailService;

    private static final String BUCKET = "docavocat-documents";

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
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
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
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
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
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
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

            // Ownership check: verify the client belongs to the authenticated lawyer
            User user = getCurrentUser(authentication);
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && (client.getLawyer() == null || !client.getLawyer().getId().equals(user.getId()))) {
                redirectAttributes.addFlashAttribute("importError", "Accès non autorisé à ce client.");
                return "redirect:/invoices";
            }

            // Vérifier unicité du numéro de facture
            if (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
                redirectAttributes.addFlashAttribute("importError", "Le numéro de facture " + invoiceNumber + " existe déjà.");
                return "redirect:/invoices";
            }

            // Sauvegarder le fichier si fourni
            String docUrl = null;
            if (file != null && !file.isEmpty()) {
                // SEC-NEW-04 FIX : validation extension et taille du fichier
                String origName = file.getOriginalFilename();
                if (origName != null) {
                    String ext = origName.contains(".") ? origName.substring(origName.lastIndexOf(".")).toLowerCase() : "";
                    if (!java.util.Set.of(".pdf", ".jpg", ".jpeg", ".png", ".doc", ".docx").contains(ext)) {
                        redirectAttributes.addFlashAttribute("importError", "Type de fichier non autorisé. Formats acceptés : PDF, JPG, PNG, DOC, DOCX.");
                        return "redirect:/invoices";
                    }
                }
                if (file.getSize() > 20 * 1024 * 1024) { // 20 MB max
                    redirectAttributes.addFlashAttribute("importError", "Fichier trop volumineux (max 20 Mo).");
                    return "redirect:/invoices";
                }
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null) {
                    originalFilename = "document.pdf";
                }
                String filename = UUID.randomUUID() + "_" + originalFilename
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
                String objectKey = "invoices/" + filename;
                String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
                storageService.storeBytes(BUCKET, objectKey, file.getBytes(), mimeType);
                docUrl = "/invoices/attachment?key=" + objectKey;
            }

            // Calculer TVA et TTC
            BigDecimal tva = totalHT.multiply(BigDecimal.valueOf(tvaRate / 100.0))
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalTTC = totalHT.add(tva).setScale(2, RoundingMode.HALF_UP);

            // Créer la facture
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber(invoiceNumber);
            invoice.setClient(client);
            // Ensure invoiceDate is never null
            invoice.setInvoiceDate(invoiceDate != null ? invoiceDate : LocalDate.now());
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
            redirectAttributes.addFlashAttribute("importError", "Erreur lors de l'upload du fichier");
        } catch (Exception e) {
            log.error("Erreur lors de l'import de la facture", e);
            redirectAttributes.addFlashAttribute("importError", "Erreur lors de l'import de la facture");
        }
        return "redirect:/invoices";
    }

    /**
     * Affiche une facture spécifique
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT', 'AVOCAT_ADMIN')")
    public String show(@PathVariable String id, Model model, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            // SEC-BYPASS FIX : passer l'ID utilisateur, le service vérifie le rôle via DB
            var invoice = invoiceService.getInvoiceById(id, user.getId());

            model.addAttribute("invoice", invoice);
            return "invoices/show";
        } catch (Exception e) {
            boolean isClient = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLIENT".equals(a.getAuthority()));
            return "redirect:" + (isClient ? "/invoices/my-invoices" : "/invoices");
        }
    }

    /**
     * Affiche le formulaire d'édition d'une facture
     */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
    public String edit(@PathVariable String id, Model model, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            // SEC-BYPASS FIX : passer l'ID utilisateur, le service vérifie le rôle via DB
            var invoice = invoiceService.getInvoiceById(id, user.getId());

            var clients = getClientsForUser(authentication, user.getId());
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

    /**
     * Envoyer une facture par email au client avec le PDF en pièce jointe
     */
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
    public String sendInvoiceByEmail(@PathVariable String id, Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(authentication);
            var invoice = invoiceService.getInvoiceById(id, user.getId());

            if (invoice.getClient() == null || invoice.getClient().getEmail() == null) {
                redirectAttributes.addFlashAttribute("error", "Ce client n'a pas d'adresse email.");
                return "redirect:/invoices/" + id;
            }

            byte[] pdfBytes = invoiceService.generatePdf(id, user.getId());
            String safeNumber = invoice.getInvoiceNumber().replaceAll("[^a-zA-Z0-9_.-]", "_");
            String filename = "facture-" + safeNumber + ".pdf";

            String contentHtml = "<p style='color:#374151;font-size:15px;line-height:1.7'>Bonjour,</p>"
                + "<p style='color:#374151;font-size:15px;line-height:1.7'>Veuillez trouver en pièce jointe votre facture.</p>"
                + "<table style='border-collapse:collapse;margin:20px 0' cellpadding='0' cellspacing='0'>"
                + "<tr><td style='padding:10px 16px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;color:#0F172A;font-size:14px'>"
                + "<strong>N° de facture :</strong> " + escapeHtml(invoice.getInvoiceNumber())
                + "<br><strong>Montant TTC :</strong> " + invoice.getTotalTTC() + " €"
                + "</td></tr></table>";

            emailService.sendEmailFromLawyerWithAttachment(
                invoice.getClient().getEmail(),
                "Facture " + invoice.getInvoiceNumber(),
                contentHtml,
                user,
                pdfBytes,
                filename
            );

            redirectAttributes.addFlashAttribute("message", "Facture envoyée par email à " + invoice.getClient().getEmail());
        } catch (Exception e) {
            log.error("Erreur envoi facture par email {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'envoi de la facture par email.");
        }
        return "redirect:/invoices/" + id;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

 // InvoiceWebController.java — ajouter cet endpoint
    @GetMapping("/attachment")
            @RequestParam String key,
            Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            // Valider le format de la clé pour prévenir le path traversal
            if (!key.matches("^invoices/[a-zA-Z0-9_\\-\\.]+$")) {
                log.warn("Clé de pièce jointe invalide demandée par {}: {}", user.getEmail(), key);
                return ResponseEntity.badRequest().build();
            }
            // Vérifier qu'une facture accessible par cet utilisateur référence cette clé
            boolean authorized = invoiceRepository.existsByDocumentUrlAndUserId(
                    "/invoices/attachment?key=" + key, user.getId());
            if (!authorized) {
                return ResponseEntity.status(403).build();
            }
            byte[] bytes = storageService.getBytes(BUCKET, key);
            String filename = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } catch (Exception e) {
            log.error("Erreur téléchargement pièce jointe facture", e);
            return ResponseEntity.notFound().build();
        }
    }

 // InvoiceWebController.java — ajouter cet endpoint
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT', 'ADMIN', 'AVOCAT_ADMIN')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id,
                                               Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            // SEC-BYPASS FIX : passer l'ID utilisateur, le service vérifie le rôle via DB
            var invoice = invoiceService.getInvoiceById(id, user.getId());

            byte[] pdfBytes = invoiceService.generatePdf(id, user.getId());

            // SEC-NEW-12 FIX : sanitize invoiceNumber for Content-Disposition header
            String safeInvoiceNumber = invoice.getInvoiceNumber()
                    .replaceAll("[^a-zA-Z0-9_.-]", "_");
            String filename = "facture-" + safeInvoiceNumber + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (Exception e) {
            log.error("Erreur PDF facture {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }
}
