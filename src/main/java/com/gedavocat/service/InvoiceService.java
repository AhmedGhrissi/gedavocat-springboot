package com.gedavocat.service;

import com.gedavocat.dto.*;
import com.gedavocat.model.Client;
import com.gedavocat.model.Invoice;
import com.gedavocat.model.InvoiceItem;
import com.gedavocat.model.Invoice.InvoiceStatus;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;


import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;

/**
 * Service pour la gestion des factures
 */
@Slf4j                        // ← ajouter ceci
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;

    /**
     * Crée une nouvelle facture
     * SEC-IDOR FIX : vérification ownership client → avocat
     */
    @Transactional
    public InvoiceResponse createInvoice(InvoiceRequest request, String lawyerId) {
        // Vérifier que le client existe
        Client client = clientRepository.findById(request.getClientId())
            .orElseThrow(() -> new RuntimeException("Client non trouvé"));

        // SÉCURITÉ : vérifier que le client appartient bien à l'avocat connecté
        if (client.getLawyer() == null || !client.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé : ce client ne vous appartient pas");
        }

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
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public InvoiceResponse updateInvoice(String invoiceId, InvoiceRequest request, String lawyerId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));

        // SÉCURITÉ : vérifier que la facture appartient bien à l'avocat connecté
        if (invoice.getClient() == null || invoice.getClient().getLawyer() == null
                || !invoice.getClient().getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à cette facture");
        }

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
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(String invoiceId, String lawyerId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        // SÉCURITÉ : vérifier que la facture appartient bien à l'avocat connecté
        if (invoice.getClient() != null && invoice.getClient().getLawyer() != null
                && lawyerId != null && !invoice.getClient().getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à cette facture");
        }
        return convertToResponse(invoice);
    }

    /**
     * Récupère toutes les factures d'un client
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByClient(String clientId, String lawyerId) {
        List<Invoice> invoices = invoiceRepository.findByClientId(clientId);
        // SEC-IDOR FIX : vérifier que le client appartient bien à l'avocat
        if (lawyerId != null && !invoices.isEmpty()) {
            Invoice first = invoices.get(0);
            if (first.getClient() != null && first.getClient().getLawyer() != null
                    && !first.getClient().getLawyer().getId().equals(lawyerId)) {
                throw new SecurityException("Accès non autorisé aux factures de ce client");
            }
        }
        return invoices.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Récupère toutes les factures d'un client (usage interne)
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
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public InvoiceResponse markAsPaid(String invoiceId, String paymentMethod, String lawyerId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));

        // SÉCURITÉ : vérifier que la facture appartient bien à l'avocat connecté
        if (invoice.getClient() == null || invoice.getClient().getLawyer() == null
                || !invoice.getClient().getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à cette facture");
        }

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
     * SEC-IDOR FIX : vérification ownership
     */
    @Transactional
    public void deleteInvoice(String invoiceId, String lawyerId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        // SÉCURITÉ : vérifier que la facture appartient bien à l'avocat connecté
        if (invoice.getClient() == null || invoice.getClient().getLawyer() == null
                || !invoice.getClient().getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à cette facture");
        }
        invoiceRepository.delete(invoice);
    }

    /**
     * Génère un numéro de facture automatique
     */
    // SEC FIX M-03 : synchronize pour éviter la race condition sur le numéro de facture
    public synchronized String generateInvoiceNumber() {
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
        // SÉCURITÉ : exposer le lawyerId via le client propriétaire
        if (invoice.getClient().getLawyer() != null) {
            response.setLawyerId(invoice.getClient().getLawyer().getId());
        }
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



  @Transactional(readOnly = true)
public byte[] generatePdf(String invoiceId, String lawyerId) {
    Invoice invoice = invoiceRepository.findByIdWithDetails(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture introuvable : " + invoiceId));
    // SÉCURITÉ : vérifier que la facture appartient bien à l'avocat connecté
    if (lawyerId != null && invoice.getClient() != null && invoice.getClient().getLawyer() != null
            && !invoice.getClient().getLawyer().getId().equals(lawyerId)) {
        throw new SecurityException("Accès non autorisé à cette facture");
    }

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

        Document doc = new Document(PageSize.A4, 55, 55, 70, 55);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        // ── Polices ──
        Font fontSection = new Font(Font.HELVETICA, 10, Font.BOLD,   new Color(13, 27, 42));
        Font fontNormal  = new Font(Font.HELVETICA,  9, Font.NORMAL, new Color(44, 62, 80));
        Font fontMuted   = new Font(Font.HELVETICA,  8, Font.NORMAL, new Color(107, 114, 128));
        Font fontHeader  = new Font(Font.HELVETICA,  8, Font.BOLD,   new Color(255, 255, 255));
        Font fontTotal   = new Font(Font.HELVETICA, 13, Font.BOLD,   new Color(13, 27, 42));
        Font fontGold    = new Font(Font.HELVETICA, 13, Font.BOLD,   new Color(139, 111, 30));

        // ── Variables ──
        String num          = invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "—";
        String clientName   = invoice.getClient() != null && invoice.getClient().getName() != null
                              ? invoice.getClient().getName() : "—";
        String statut       = invoice.getStatus() != null ? invoice.getStatus().name() : "—";
        String dateFacture  = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "—";
        String dateEcheance = invoice.getDueDate()     != null ? invoice.getDueDate().toString()     : "—";
        double ht           = invoice.getTotalHT()  != null ? invoice.getTotalHT().doubleValue()  : 0;
        double tva          = invoice.getTotalTVA() != null ? invoice.getTotalTVA().doubleValue() : 0;
        double ttc          = invoice.getTotalTTC() != null ? invoice.getTotalTTC().doubleValue() : 0;

        // ── En-tête : logo + numéro facture ──
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.5f, 1f});
        headerTable.setSpacingAfter(20);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPaddingBottom(8);
        logoCell.addElement(new Paragraph("GED AVOCAT",
                new Font(Font.HELVETICA, 18, Font.BOLD, new Color(13, 27, 42))));
        logoCell.addElement(new Paragraph("Cabinet de gestion documentaire", fontMuted));
        headerTable.addCell(logoCell);

        PdfPCell numCell = new PdfPCell();
        numCell.setBorder(Rectangle.NO_BORDER);
        numCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        numCell.setPaddingBottom(8);
        Paragraph labelFact = new Paragraph("FACTURE",
                new Font(Font.HELVETICA, 9, Font.BOLD, new Color(184, 149, 42)));
        labelFact.setAlignment(Element.ALIGN_RIGHT);
        Paragraph numFact = new Paragraph(num,
                new Font(Font.HELVETICA, 16, Font.BOLD, new Color(13, 27, 42)));
        numFact.setAlignment(Element.ALIGN_RIGHT);
        numCell.addElement(labelFact);
        numCell.addElement(numFact);
        headerTable.addCell(numCell);
        doc.add(headerTable);

        // ── Ligne dorée ──
        LineSeparator sep = new LineSeparator(1f, 100, new Color(184, 149, 42), Element.ALIGN_CENTER, -2);
        doc.add(new Chunk(sep));
        doc.add(Chunk.NEWLINE);

        // ── Infos client + dates ──
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1.5f, 1f});
        infoTable.setSpacingBefore(12);
        infoTable.setSpacingAfter(20);

        PdfPCell clientCell = new PdfPCell();
        clientCell.setBorder(Rectangle.NO_BORDER);
        clientCell.setPaddingBottom(6);
        Paragraph clientLabel = new Paragraph("CLIENT",
                new Font(Font.HELVETICA, 7, Font.BOLD, new Color(184, 149, 42)));
        clientCell.addElement(clientLabel);
        clientCell.addElement(new Paragraph(clientName, fontSection));
        infoTable.addCell(clientCell);

        PdfPCell dateCell = new PdfPCell();
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pDate = new Paragraph("Date : " + dateFacture, fontNormal);
        pDate.setAlignment(Element.ALIGN_RIGHT);
        Paragraph pEch = new Paragraph("Échéance : " + dateEcheance, fontNormal);
        pEch.setAlignment(Element.ALIGN_RIGHT);
        Paragraph pStatut = new Paragraph("Statut : " + statut,
                new Font(Font.HELVETICA, 9, Font.BOLD, new Color(13, 27, 42)));
        pStatut.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(pDate);
        dateCell.addElement(pEch);
        dateCell.addElement(pStatut);
        infoTable.addCell(dateCell);
        doc.add(infoTable);

        // ── Tableau des prestations ──
        PdfPTable itemsTable = new PdfPTable(5);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{4f, 1f, 1.5f, 1f, 1.5f});
        itemsTable.setSpacingAfter(16);

        Color navyBg = new Color(13, 27, 42);
        String[] cols  = {"Description", "Qté", "Prix unit. HT", "TVA", "Total HT"};
        int[]    aligns = {Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT,
                           Element.ALIGN_CENTER, Element.ALIGN_RIGHT};

        for (int i = 0; i < cols.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(cols[i], fontHeader));
            c.setBackgroundColor(navyBg);
            c.setPadding(7);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(aligns[i]);
            itemsTable.addCell(c);
        }

        Color rowAlt = new Color(250, 248, 243);
        List<InvoiceItem> itemList = invoice.getItems();
        if (itemList != null && !itemList.isEmpty()) {
            int rowIdx = 0;
            for (InvoiceItem item : itemList) {
                Color bg      = (rowIdx % 2 == 0) ? Color.WHITE : rowAlt;
                double qty    = item.getQuantity()    != null ? item.getQuantity().doubleValue()    : 0;
                double price  = item.getUnitPriceHT() != null ? item.getUnitPriceHT().doubleValue() : 0;
                double tvaRate= item.getTvaRate()     != null ? item.getTvaRate().doubleValue()     : 0;
                double lineHT = qty * price;

                String[] vals = {
                    item.getDescription() != null ? item.getDescription() : "—",
                    String.format("%.2f", qty),
                    String.format("%.2f €", price),
                    String.format("%.1f %%", tvaRate),
                    String.format("%.2f €", lineHT)
                };
                for (int i = 0; i < vals.length; i++) {
                    PdfPCell c = new PdfPCell(new Phrase(vals[i], fontNormal));
                    c.setBackgroundColor(bg);
                    c.setPadding(7);
                    c.setBorderColor(new Color(220, 215, 205));
                    c.setBorderWidth(0.5f);
                    c.setHorizontalAlignment(aligns[i]);
                    itemsTable.addCell(c);
                }
                rowIdx++;
            }
        } else {
            // Ligne vide si aucune prestation
            PdfPCell empty = new PdfPCell(new Phrase("Aucune prestation", fontMuted));
            empty.setColspan(5);
            empty.setPadding(10);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(new Color(220, 215, 205));
            empty.setBorderWidth(0.5f);
            itemsTable.addCell(empty);
        }
        doc.add(itemsTable);

        // ── Totaux ──
        PdfPTable totauxTable = new PdfPTable(2);
        totauxTable.setWidthPercentage(38);
        totauxTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totauxTable.setWidths(new float[]{1.6f, 1f});
        totauxTable.setSpacingAfter(20);

        addTotalRow(totauxTable, "Total HT",  String.format("%.2f €", ht),  fontNormal, fontNormal, false);
        addTotalRow(totauxTable, "TVA",       String.format("%.2f €", tva), fontNormal, fontNormal, false);
        addTotalRow(totauxTable, "Total TTC", String.format("%.2f €", ttc), fontTotal,  fontGold,   true);
        doc.add(totauxTable);

        // ── Notes ──
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            doc.add(new Chunk(new LineSeparator(0.5f, 100,
                    new Color(220, 215, 205), Element.ALIGN_CENTER, -2)));
            doc.add(Chunk.NEWLINE);
            Paragraph titreNotes = new Paragraph("Conditions de paiement", fontSection);
            titreNotes.setSpacingBefore(8);
            doc.add(titreNotes);
            Paragraph notes = new Paragraph(invoice.getNotes(), fontMuted);
            notes.setSpacingBefore(4);
            doc.add(notes);
        }

        doc.close();
        return baos.toByteArray();

    } catch (Exception e) {
        log.error("Erreur génération PDF pour facture {}", invoiceId, e);
        throw new RuntimeException("Impossible de générer le PDF", e);
    }
}

// ── Méthode utilitaire ──
private void addTotalRow(PdfPTable table, String label, String value,
                          Font labelFont, Font valueFont, boolean highlight) {
    Color bg = highlight ? new Color(245, 243, 238) : Color.WHITE;

    PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
    lCell.setBorderColor(new Color(220, 215, 205));
    lCell.setBorderWidth(0.5f);
    lCell.setPadding(6);
    lCell.setBackgroundColor(bg);

    PdfPCell vCell = new PdfPCell(new Phrase(value, valueFont));
    vCell.setBorderColor(new Color(220, 215, 205));
    vCell.setBorderWidth(0.5f);
    vCell.setPadding(6);
    vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    vCell.setBackgroundColor(bg);

    table.addCell(lCell);
    table.addCell(vCell);
}



}
