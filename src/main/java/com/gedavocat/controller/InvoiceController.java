package com.gedavocat.controller;

import com.gedavocat.dto.InvoiceRequest;
import com.gedavocat.dto.InvoiceResponse;
import com.gedavocat.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    
    /**
     * Crée une nouvelle facture
     */
    @PostMapping
    @PreAuthorize("hasRole('LAWYER')")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        try {
            InvoiceResponse response = invoiceService.createInvoice(request);
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
            @Valid @RequestBody InvoiceRequest request) {
        try {
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
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable String invoiceId) {
        try {
            InvoiceResponse response = invoiceService.getInvoiceById(invoiceId);
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
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByClient(@PathVariable String clientId) {
        try {
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
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByLawyer(@PathVariable String lawyerId) {
        try {
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
    public ResponseEntity<List<InvoiceResponse>> getOverdueInvoicesByLawyer(@PathVariable String lawyerId) {
        try {
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
            @RequestBody(required = false) Map<String, String> body) {
        try {
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
    public ResponseEntity<Void> deleteInvoice(@PathVariable String invoiceId) {
        try {
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
