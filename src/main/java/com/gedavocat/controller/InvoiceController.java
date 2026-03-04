package com.gedavocat.controller;

import com.gedavocat.dto.InvoiceRequest;
import com.gedavocat.dto.InvoiceResponse;
import com.gedavocat.model.User;
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

    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
    
    /**
     * Crée une nouvelle facture
     */
    @PostMapping
    @PreAuthorize("hasRole('LAWYER')")
    public ResponseEntity<?> createInvoice(@Valid @RequestBody InvoiceRequest request,
                                           Authentication authentication) {
        try {
            // SEC-IDOR FIX : passer le lawyerId pour vérification ownership dans le service
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.createInvoice(request, user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            // Log error for monitoring
            log.error("Erreur lors de la création de la facture", e);
            Map<String, Object> body = Map.of(
                    "error", true,
                    "message", e.getMessage() != null ? e.getMessage() : "Erreur interne"
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }
    
    /**
     * Met à jour une facture existante
     */
    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasRole('LAWYER')")
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
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT', 'ADMIN')")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable String invoiceId,
                                                           Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            
            // Vérifier le rôle de l'utilisateur
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isLawyer = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_LAWYER"));
            
            // Pour les admins : accès complet sans vérification
            // Pour les lawyers : vérification via leur ID
            // Pour les clients : vérification via leur ID
            String checkLawyerId = isAdmin ? "ADMIN_BYPASS" : (isLawyer ? user.getId() : user.getId());
            
            InvoiceResponse response = invoiceService.getInvoiceById(invoiceId, checkLawyerId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la facture {}: {}", invoiceId, e.getMessage());
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
            if (isClient && !user.getId().equals(clientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            // Pour LAWYER : utiliser la version avec vérification ownership
            boolean isLawyer = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_LAWYER"));
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
    @PreAuthorize("hasRole('LAWYER')")
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
    @PreAuthorize("hasRole('LAWYER')")
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
    @PreAuthorize("hasRole('LAWYER')")
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
     * Supprime une facture
     */
    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasRole('LAWYER')")
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
    @PreAuthorize("hasRole('LAWYER')")
    public ResponseEntity<Map<String, String>> generateInvoiceNumber() {
        String invoiceNumber = invoiceService.generateInvoiceNumber();
        return ResponseEntity.ok(Map.of("invoiceNumber", invoiceNumber));
    }
}
