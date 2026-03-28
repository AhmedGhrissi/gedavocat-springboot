package com.gedavocat.controller;

import com.gedavocat.dto.InvoiceRequest;
import com.gedavocat.dto.InvoiceResponse;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des factures
 */
@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    
    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final com.gedavocat.service.NotificationService notificationService;

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
    
    /**
     * Crée une nouvelle facture
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<?> createInvoice(@Valid @RequestBody InvoiceRequest request,
                                           Authentication authentication) {
        try {
            // SEC-IDOR FIX : passer le lawyerId pour vérification ownership dans le service
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.createInvoice(request, user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            // SEC-INFO-LEAK FIX : ne pas exposer e.getMessage() au client
            log.error("Erreur lors de la création de la facture", e);
            Map<String, Object> body = Map.of(
                    "error", true,
                    "message", "Erreur lors de la création de la facture. Veuillez réessayer."
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }
    
    /**
     * Met à jour une facture existante
     */
    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @PathVariable String invoiceId,
            @Valid @RequestBody InvoiceRequest request,
            Authentication authentication) {
        try {
            // SEC-IDOR FIX : ownership vérifié dans le service
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.updateInvoice(invoiceId, request, user.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Récupère une facture par son ID
     */
    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT', 'ADMIN', 'AVOCAT_ADMIN')")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable String invoiceId,
                                                           Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            // SEC-BYPASS FIX : passer l'ID utilisateur directement, le service vérifie le rôle via DB
            InvoiceResponse response = invoiceService.getInvoiceById(invoiceId, user.getId());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Accès refusé à la facture {} pour l'utilisateur {}", invoiceId, authentication.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la facture {}", invoiceId, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Récupère toutes les factures d'un client
     */
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT')")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByClient(@PathVariable String clientId,
                                                                      Authentication authentication) {
        try {
            // SÉCURITÉ CTL-12 FIX : vérifier ownership pour CLIENT et LAWYER
            User user = getCurrentUser(authentication);
            boolean isClient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"));
            if (isClient) {
                // User.id != Client.id — must compare via Client.clientUser FK
                Client c = clientRepository.findById(clientId).orElse(null);
                if (c == null || c.getClientUser() == null || !c.getClientUser().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                List<InvoiceResponse> invoices = invoiceService.getInvoicesByClient(clientId);
                return ResponseEntity.ok(invoices);
            }
            // Pour LAWYER / AVOCAT_ADMIN : utiliser la version avec vérification ownership
            boolean isLawyer = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_LAWYER") || a.getAuthority().equals("ROLE_AVOCAT_ADMIN"));
            if (isLawyer) {
                List<InvoiceResponse> invoices = invoiceService.getInvoicesByClient(clientId, user.getId());
                return ResponseEntity.ok(invoices);
            }
            List<InvoiceResponse> invoices = invoiceService.getInvoicesByClient(clientId);
            return ResponseEntity.ok(invoices);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Récupère toutes les factures d'un avocat
     */
    @GetMapping("/lawyer/{lawyerId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByLawyer(@PathVariable String lawyerId,
                                                                      Authentication authentication) {
        try {
            // SÉCURITÉ : forcer le lawyerId de l'utilisateur authentifié
            User user = getCurrentUser(authentication);
            if (!user.getId().equals(lawyerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            List<InvoiceResponse> invoices = invoiceService.getInvoicesByLawyer(lawyerId);
            return ResponseEntity.ok(invoices);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Récupère les factures en retard d'un avocat
     */
    @GetMapping("/lawyer/{lawyerId}/overdue")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<List<InvoiceResponse>> getOverdueInvoicesByLawyer(@PathVariable String lawyerId,
                                                                             Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            if (!user.getId().equals(lawyerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            List<InvoiceResponse> invoices = invoiceService.getOverdueInvoicesByLawyer(lawyerId);
            return ResponseEntity.ok(invoices);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Marque une facture comme payée
     */
    @PatchMapping("/{invoiceId}/mark-as-paid")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<InvoiceResponse> markAsPaid(
            @PathVariable String invoiceId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            // SEC-IDOR FIX : ownership vérifié dans le service
            User user = getCurrentUser(authentication);
            String paymentMethod = body != null ? body.get("paymentMethod") : null;
            InvoiceResponse response = invoiceService.markAsPaid(invoiceId, paymentMethod, user.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Client déclare avoir payé une facture
     */
    @PostMapping("/{invoiceId}/declare-payment")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> declarePayment(
            @PathVariable String invoiceId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            String paymentMethod = body != null ? body.get("paymentMethod") : null;
            InvoiceResponse response = invoiceService.declarePayment(invoiceId, paymentMethod, user.getId());

            // Notifier l'avocat
            Client client = clientRepository.findByClientUserId(user.getId()).orElse(null);
            if (client != null && client.getLawyer() != null) {
                notificationService.create(
                    client.getLawyer(),
                    "PAYMENT_DECLARED",
                    "Paiement déclaré",
                    client.getName() + " déclare avoir payé la facture " + response.getInvoiceNumber()
                        + (paymentMethod != null ? " (" + paymentMethod + ")" : ""),
                    "/invoices/" + invoiceId,
                    "fa-money-check-alt",
                    "warning"
                );
            }

            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Erreur lors de la déclaration de paiement", e);
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    /**
     * L'avocat valide le paiement déclaré par le client
     */
    @PatchMapping("/{invoiceId}/validate-payment")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<?> validatePayment(
            @PathVariable String invoiceId,
            Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.validatePayment(invoiceId, user.getId());

            // Notifier le client
            Client client = clientRepository.findById(response.getClientId()).orElse(null);
            if (client != null && client.getClientUser() != null) {
                notificationService.create(
                    client.getClientUser(),
                    "PAYMENT_VALIDATED",
                    "Paiement validé",
                    "Votre paiement pour la facture " + response.getInvoiceNumber() + " a été validé",
                    "/invoices/" + invoiceId,
                    "fa-check-circle",
                    "success"
                );
            }

            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Erreur lors de la validation du paiement", e);
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    /**
     * L'avocat rejette le paiement déclaré par le client
     */
    @PatchMapping("/{invoiceId}/reject-payment")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<?> rejectPayment(
            @PathVariable String invoiceId,
            Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.rejectPayment(invoiceId, user.getId());

            // Notifier le client
            Client client = clientRepository.findById(response.getClientId()).orElse(null);
            if (client != null && client.getClientUser() != null) {
                notificationService.create(
                    client.getClientUser(),
                    "PAYMENT_REJECTED",
                    "Paiement non confirmé",
                    "Votre déclaration de paiement pour la facture " + response.getInvoiceNumber()
                        + " n'a pas été confirmée. Veuillez contacter votre avocat.",
                    "/invoices/" + invoiceId,
                    "fa-exclamation-circle",
                    "danger"
                );
            }

            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Erreur lors du rejet du paiement", e);
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", e.getMessage()));
        }
    }

    /**
     * Supprime une facture
     */
    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable String invoiceId,
                                               Authentication authentication) {
        try {
            // SEC-IDOR FIX : ownership vérifié dans le service
            User user = getCurrentUser(authentication);
            invoiceService.deleteInvoice(invoiceId, user.getId());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Génère un numéro de facture automatique
     */
    @GetMapping("/generate-number")
    @PreAuthorize("hasAnyRole('LAWYER', 'AVOCAT_ADMIN')")
    public ResponseEntity<Map<String, String>> generateInvoiceNumber() {
        String invoiceNumber = invoiceService.generateInvoiceNumber();
        return ResponseEntity.ok(Map.of("invoiceNumber", invoiceNumber));
    }
}
