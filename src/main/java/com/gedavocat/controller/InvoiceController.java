package com.gedavocat.controller;

import com.gedavocat.dto.InvoiceRequest;
import com.gedavocat.dto.InvoiceResponse;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest request,
                                                          Authentication authentication) {
        try {
            // SÉCURITÉ : vérifier que le client appartient au lawyer authentifié
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.createInvoice(request);
            // Vérifier que la facture créée appartient bien au lawyer via client.lawyer
            if (response.getLawyerId() != null && !user.getId().equals(response.getLawyerId())) {
                invoiceService.deleteInvoice(response.getId()); // rollback
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
            // SÉCURITÉ : vérifier ownership via service
            User user = getCurrentUser(authentication);
            InvoiceResponse existing = invoiceService.getInvoiceById(invoiceId);
            if (existing == null || !user.getId().equals(existing.getLawyerId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            InvoiceResponse response = invoiceService.updateInvoice(invoiceId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Récupère une facture par son ID
     */
    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLIENT')")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable String invoiceId,
                                                           Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            InvoiceResponse response = invoiceService.getInvoiceById(invoiceId);
            // SÉCURITÉ : vérifier que l'utilisateur est le lawyer ou le client de cette facture
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !user.getId().equals(response.getLawyerId()) && !user.getId().equals(response.getClientId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
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
            // SÉCURITÉ : forcer clientId du user authentifié si CLIENT, sinon vérifier ownership
            User user = getCurrentUser(authentication);
            boolean isClient = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"));
            if (isClient && !user.getId().equals(clientId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
            User user = getCurrentUser(authentication);
            InvoiceResponse existing = invoiceService.getInvoiceById(invoiceId);
            if (!user.getId().equals(existing.getLawyerId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            String paymentMethod = body != null ? body.get("paymentMethod") : null;
            InvoiceResponse response = invoiceService.markAsPaid(invoiceId, paymentMethod);
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
            User user = getCurrentUser(authentication);
            InvoiceResponse existing = invoiceService.getInvoiceById(invoiceId);
            if (!user.getId().equals(existing.getLawyerId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            invoiceService.deleteInvoice(invoiceId);
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
