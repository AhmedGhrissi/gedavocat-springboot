package com.gedavocat.service;

import com.gedavocat.dto.*;
import com.gedavocat.model.Client;
import com.gedavocat.model.Invoice;
import com.gedavocat.model.InvoiceItem;
import com.gedavocat.model.Invoice.InvoiceStatus;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.InvoiceRepository;
import com.gedavocat.repository.InvoiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service pour la gestion des factures
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {
    
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ClientRepository clientRepository;
    
    /**
     * Crée une nouvelle facture
     */
    @Transactional
    public InvoiceResponse createInvoice(InvoiceRequest request) {
        // Vérifier que le client existe
        Client client = clientRepository.findById(request.getClientId())
            .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        
        // Vérifier que le numéro de facture n'existe pas déjà
        if (invoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
            throw new RuntimeException("Le numéro de facture existe déjà");
        }
        
        // Créer la facture
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setClient(client);
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setPaidDate(request.getPaidDate());
        invoice.setStatus(request.getStatus() != null ? request.getStatus() : InvoiceStatus.DRAFT);
        invoice.setNotes(request.getNotes());
        invoice.setPaymentMethod(request.getPaymentMethod());
        invoice.setDocumentUrl(request.getDocumentUrl());
        
        // Ajouter les lignes de facture
        for (InvoiceItemRequest itemRequest : request.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setDescription(itemRequest.getDescription());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPriceHT(itemRequest.getUnitPriceHT());
            item.setTvaRate(itemRequest.getTvaRate());
            item.setDisplayOrder(itemRequest.getDisplayOrder());
            invoice.addItem(item);
        }
        
        // Calculer les totaux
        invoice.calculateTotals();
        
        // Sauvegarder la facture
        Invoice savedInvoice = invoiceRepository.save(invoice);
        
        return convertToResponse(savedInvoice);
    }
    
    /**
     * Met à jour une facture existante
     */
    @Transactional
    public InvoiceResponse updateInvoice(String invoiceId, InvoiceRequest request) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        
        // Vérifier que le numéro de facture n'est pas déjà utilisé par une autre facture
        if (!invoice.getInvoiceNumber().equals(request.getInvoiceNumber()) 
            && invoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
            throw new RuntimeException("Le numéro de facture existe déjà");
        }
        
        // Mettre à jour les champs
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setPaidDate(request.getPaidDate());
        invoice.setStatus(request.getStatus() != null ? request.getStatus() : invoice.getStatus());
        invoice.setNotes(request.getNotes());
        invoice.setPaymentMethod(request.getPaymentMethod());
        invoice.setDocumentUrl(request.getDocumentUrl());
        
        // Supprimer les anciennes lignes et ajouter les nouvelles
        invoice.getItems().clear();
        for (InvoiceItemRequest itemRequest : request.getItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setDescription(itemRequest.getDescription());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPriceHT(itemRequest.getUnitPriceHT());
            item.setTvaRate(itemRequest.getTvaRate());
            item.setDisplayOrder(itemRequest.getDisplayOrder());
            invoice.addItem(item);
        }
        
        // Recalculer les totaux
        invoice.calculateTotals();
        
        Invoice updatedInvoice = invoiceRepository.save(invoice);
        return convertToResponse(updatedInvoice);
    }
    
    /**
     * Récupère une facture par son ID
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        return convertToResponse(invoice);
    }
    
    /**
     * Récupère toutes les factures d'un client
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByClient(String clientId) {
        List<Invoice> invoices = invoiceRepository.findByClientId(clientId);
        return invoices.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Récupère toutes les factures d'un avocat
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByLawyer(String lawyerId) {
        List<Invoice> invoices = invoiceRepository.findByLawyerId(lawyerId);
        return invoices.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Récupère les factures en retard d'un avocat
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getOverdueInvoicesByLawyer(String lawyerId) {
        List<Invoice> invoices = invoiceRepository.findOverdueInvoicesByLawyer(lawyerId, LocalDate.now());
        return invoices.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Marque une facture comme payée
     */
    @Transactional
    public InvoiceResponse markAsPaid(String invoiceId, String paymentMethod) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidDate(LocalDate.now());
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            invoice.setPaymentMethod(paymentMethod);
        }
        
        Invoice updatedInvoice = invoiceRepository.save(invoice);
        return convertToResponse(updatedInvoice);
    }
    
    /**
     * Supprime une facture
     */
    @Transactional
    public void deleteInvoice(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        invoiceRepository.delete(invoice);
    }
    
    /**
     * Génère un numéro de facture automatique
     */
    public String generateInvoiceNumber() {
        int year = LocalDate.now().getYear();
        long count = invoiceRepository.count() + 1;
        return String.format("FACT-%d-%05d", year, count);
    }
    
    /**
     * Convertit une entité Invoice en InvoiceResponse
     */
    private InvoiceResponse convertToResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setInvoiceNumber(invoice.getInvoiceNumber());
        response.setClientId(invoice.getClient().getId());
        response.setClientName(invoice.getClient().getName());
        response.setClientEmail(invoice.getClient().getEmail());
        response.setInvoiceDate(invoice.getInvoiceDate());
        response.setDueDate(invoice.getDueDate());
        response.setPaidDate(invoice.getPaidDate());
        response.setStatus(invoice.getStatus());
        response.setTotalHT(invoice.getTotalHT());
        response.setTotalTVA(invoice.getTotalTVA());
        response.setTotalTTC(invoice.getTotalTTC());
        response.setCurrency(invoice.getCurrency());
        response.setNotes(invoice.getNotes());
        response.setPaymentMethod(invoice.getPaymentMethod());
        response.setDocumentUrl(invoice.getDocumentUrl());
        response.setCreatedAt(invoice.getCreatedAt());
        response.setUpdatedAt(invoice.getUpdatedAt());
        response.setOverdue(invoice.isOverdue());
        
        // Convertir les lignes de facture
        List<InvoiceItemResponse> itemResponses = invoice.getItems().stream()
            .map(this::convertItemToResponse)
            .collect(Collectors.toList());
        response.setItems(itemResponses);
        
        return response;
    }
    
    /**
     * Convertit une entité InvoiceItem en InvoiceItemResponse
     */
    private InvoiceItemResponse convertItemToResponse(InvoiceItem item) {
        InvoiceItemResponse response = new InvoiceItemResponse();
        response.setId(item.getId());
        response.setDescription(item.getDescription());
        response.setQuantity(item.getQuantity());
        response.setUnitPriceHT(item.getUnitPriceHT());
        response.setTvaRate(item.getTvaRate());
        response.setTotalHT(item.getTotalHT());
        response.setTotalTVA(item.getTotalTVA());
        response.setTotalTTC(item.getTotalTTC());
        response.setDisplayOrder(item.getDisplayOrder());
        return response;
    }
}
