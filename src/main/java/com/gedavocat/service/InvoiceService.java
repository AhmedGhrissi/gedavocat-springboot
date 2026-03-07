package com.gedavocat.service;

import com.gedavocat.dto.*;
import com.gedavocat.model.Client;
import com.gedavocat.model.Invoice;
import com.gedavocat.model.InvoiceItem;
import com.gedavocat.model.Invoice.InvoiceStatus;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.InvoiceRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFont;
import java.io.ByteArrayOutputStream;

/**
 * Service pour la gestion des factures
 */
@Slf4j                        // ← ajouter ceci
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

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
        // Ensure invoiceDate is never null (use current date if not provided)
        invoice.setInvoiceDate(request.getInvoiceDate() != null ? request.getInvoiceDate() : LocalDate.now());
        invoice.setDueDate(request.getDueDate());
        invoice.setPaidDate(request.getPaidDate());
        invoice.setStatus(request.getStatus() != null ? request.getStatus() : InvoiceStatus.DRAFT);
        invoice.setNotes(request.getNotes());
        invoice.setPaymentMethod(request.getPaymentMethod());
        invoice.setDocumentUrl(request.getDocumentUrl());

        // Associer le firm via le client (multi-tenant)
        if (client.getFirm() != null) {
            invoice.setFirm(client.getFirm());
        }

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
        // Ensure invoiceDate is never null (keep existing if not provided)
        invoice.setInvoiceDate(request.getInvoiceDate() != null ? request.getInvoiceDate() : invoice.getInvoiceDate());
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
     * SEC-IDOR FIX : vérification ownership via rôle SecurityContext
     * @param invoiceId ID de la facture
     * @param requesterId ID de l'utilisateur demandeur (utilisé pour vérifier ownership)
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(String invoiceId, String requesterId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new RuntimeException("Facture non trouvée"));
        
        // SÉCURITÉ : vérification ownership stricte
        if (requesterId == null) {
            throw new SecurityException("Identifiant utilisateur requis pour accéder à une facture");
        }
        
        // Vérifier le rôle via la base de données (pas de magic string)
        User requester = userRepository.findById(requesterId).orElse(null);
        if (requester != null && requester.isAdmin()) {
            // Admin : accès complet
            return convertToResponse(invoice);
        }
        
        // Client : vérifier que la facture lui appartient
        if (requester != null && requester.isClient()) {
            if (invoice.getClient() != null && invoice.getClient().getClientUser() != null
                    && invoice.getClient().getClientUser().getId().equals(requesterId)) {
                return convertToResponse(invoice);
            }
            throw new SecurityException("Accès non autorisé à cette facture");
        }
        
        // Lawyer : vérifier que la facture appartient à un de ses clients
        if (invoice.getClient() != null && invoice.getClient().getLawyer() != null
                && !invoice.getClient().getLawyer().getId().equals(requesterId)) {
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
        // SÉCURITÉ SVC-01 FIX : vérification ownership stricte — lawyerId obligatoire
        if (lawyerId == null) {
            throw new SecurityException("Identifiant avocat requis pour accéder aux factures");
        }
        if (!invoices.isEmpty()) {
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

        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc, PageSize.A4);
        doc.setMargins(70, 55, 55, 55);

        // ── Polices iText 7 ──
        PdfFont helvetica = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont helveticaBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        // ── Couleurs ──
        DeviceRgb navyColor = new DeviceRgb(13, 27, 42);
        DeviceRgb goldColor = new DeviceRgb(184, 149, 42);
        DeviceRgb mutedColor = new DeviceRgb(107, 114, 128);
        DeviceRgb slateColor = new DeviceRgb(44, 62, 80);

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
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1f}))
            .useAllAvailableWidth()
            .setMarginBottom(20);

        Cell logoCell = new Cell()
            .setBorder(null)
            .setPaddingBottom(8);
        logoCell.add(new Paragraph("GED AVOCAT")
            .setFont(helveticaBold)
            .setFontSize(18)
            .setFontColor(navyColor));
        logoCell.add(new Paragraph("Cabinet de gestion documentaire")
            .setFont(helvetica)
            .setFontSize(9)
            .setFontColor(mutedColor));
        headerTable.addCell(logoCell);

        Cell numCell = new Cell()
            .setBorder(null)
            .setTextAlignment(TextAlignment.RIGHT)
            .setPaddingBottom(8);
        numCell.add(new Paragraph("FACTURE")
            .setFont(helveticaBold)
            .setFontSize(9)
            .setFontColor(goldColor)
            .setTextAlignment(TextAlignment.RIGHT));
        numCell.add(new Paragraph(num)
            .setFont(helveticaBold)
            .setFontSize(16)
            .setFontColor(navyColor)
            .setTextAlignment(TextAlignment.RIGHT));
        headerTable.addCell(numCell);
        doc.add(headerTable);

        // Ligne doree
        SolidLine line1 = new SolidLine(1f);
        line1.setColor(goldColor);
        LineSeparator separator1 = new LineSeparator(line1);
        separator1.setMarginBottom(10);
        doc.add(separator1);

        // ── Infos client + dates ──
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1f}))
            .useAllAvailableWidth()
            .setMarginTop(12)
            .setMarginBottom(20);

        Cell clientCell = new Cell()
            .setBorder(null)
            .setPaddingBottom(6);
        clientCell.add(new Paragraph("CLIENT")
            .setFont(helveticaBold)
            .setFontSize(7)
            .setFontColor(goldColor));
        clientCell.add(new Paragraph(clientName)
            .setFont(helveticaBold)
            .setFontSize(10)
            .setFontColor(navyColor));
        infoTable.addCell(clientCell);

        Cell dateCell = new Cell()
            .setBorder(null)
            .setTextAlignment(TextAlignment.RIGHT);
        dateCell.add(new Paragraph("Date : " + dateFacture)
            .setFont(helvetica)
            .setFontSize(9)
            .setFontColor(slateColor)
            .setTextAlignment(TextAlignment.RIGHT));
        dateCell.add(new Paragraph("Échéance : " + dateEcheance)
            .setFont(helvetica)
            .setFontSize(9)
            .setFontColor(slateColor)
            .setTextAlignment(TextAlignment.RIGHT));
        dateCell.add(new Paragraph("Statut : " + statut)
            .setFont(helveticaBold)
            .setFontSize(9)
            .setFontColor(navyColor)
            .setTextAlignment(TextAlignment.RIGHT));
        infoTable.addCell(dateCell);
        doc.add(infoTable);

        // ── Tableau des prestations ──
        Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{4f, 1f, 1.5f, 1f, 1.5f}))
            .useAllAvailableWidth()
            .setMarginBottom(16);

        // En-têtes de colonnes
        String[] cols = {"Description", "Qté", "Prix unit. HT", "TVA", "Total HT"};
        TextAlignment[] aligns = {TextAlignment.LEFT, TextAlignment.CENTER, TextAlignment.RIGHT,
                                  TextAlignment.CENTER, TextAlignment.RIGHT};

        for (int i = 0; i < cols.length; i++) {
            Cell c = new Cell()
                .add(new Paragraph(cols[i])
                    .setFont(helveticaBold)
                    .setFontSize(8)
                    .setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(navyColor)
                .setPadding(7)
                .setBorder(null)
                .setTextAlignment(aligns[i]);
            itemsTable.addHeaderCell(c);
        }

        DeviceRgb rowAltColor = new DeviceRgb(250, 248, 243);
        DeviceRgb borderColor = new DeviceRgb(220, 215, 205);

        List<InvoiceItem> itemList = invoice.getItems();
        if (itemList != null && !itemList.isEmpty()) {
            int rowIdx = 0;
            for (InvoiceItem item : itemList) {
                Color bgColor = (rowIdx % 2 == 0) ? ColorConstants.WHITE : rowAltColor;
                double qty = item.getQuantity() != null ? item.getQuantity().doubleValue() : 0;
                double price = item.getUnitPriceHT() != null ? item.getUnitPriceHT().doubleValue() : 0;
                double tvaRate = item.getTvaRate() != null ? item.getTvaRate().doubleValue() : 0;
                double lineHT = qty * price;

                String[] vals = {
                    item.getDescription() != null ? item.getDescription() : "—",
                    String.format("%.2f", qty),
                    String.format("%.2f €", price),
                    String.format("%.1f %%", tvaRate),
                    String.format("%.2f €", lineHT)
                };

                for (int i = 0; i < vals.length; i++) {
                    Cell c = new Cell()
                        .add(new Paragraph(vals[i])
                            .setFont(helvetica)
                            .setFontSize(9)
                            .setFontColor(slateColor))
                        .setBackgroundColor(bgColor)
                        .setPadding(7)
                        .setBorder(new SolidBorder(borderColor, 0.5f))
                        .setTextAlignment(aligns[i]);
                    itemsTable.addCell(c);
                }
                rowIdx++;
            }
        } else {
            // Ligne vide si aucune prestation
            Cell empty = new Cell(1, 5)
                .add(new Paragraph("Aucune prestation")
                    .setFont(helvetica)
                    .setFontSize(9)
                    .setFontColor(mutedColor))
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(new SolidBorder(borderColor, 0.5f));
            itemsTable.addCell(empty);
        }
        doc.add(itemsTable);

        // ── Totaux ──
        Table totauxTable = new Table(UnitValue.createPercentArray(new float[]{1.6f, 1f}))
            .setWidth(UnitValue.createPercentValue(38))
            .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT)
            .setMarginBottom(20);

        // Total HT
        totauxTable.addCell(new Cell()
            .add(new Paragraph("Total HT")
                .setFont(helvetica)
                .setFontSize(9)
                .setFontColor(slateColor))
            .setBackgroundColor(ColorConstants.WHITE)
            .setPadding(6)
            .setBorder(new SolidBorder(borderColor, 0.5f)));

        totauxTable.addCell(new Cell()
            .add(new Paragraph(String.format("%.2f €", ht))
                .setFont(helvetica)
                .setFontSize(9)
                .setFontColor(slateColor))
            .setBackgroundColor(ColorConstants.WHITE)
            .setPadding(6)
            .setBorder(new SolidBorder(borderColor, 0.5f))
            .setTextAlignment(TextAlignment.RIGHT));

        // TVA
        totauxTable.addCell(new Cell()
            .add(new Paragraph("TVA")
                .setFont(helvetica)
                .setFontSize(9)
                .setFontColor(slateColor))
            .setBackgroundColor(ColorConstants.WHITE)
            .setPadding(6)
            .setBorder(new SolidBorder(borderColor, 0.5f)));

        totauxTable.addCell(new Cell()
            .add(new Paragraph(String.format("%.2f €", tva))
                .setFont(helvetica)
                .setFontSize(9)
                .setFontColor(slateColor))
            .setBackgroundColor(ColorConstants.WHITE)
            .setPadding(6)
            .setBorder(new SolidBorder(borderColor, 0.5f))
            .setTextAlignment(TextAlignment.RIGHT));

        // Total TTC (highlighted)
        DeviceRgb highlightBg = new DeviceRgb(245, 243, 238);
        totauxTable.addCell(new Cell()
            .add(new Paragraph("Total TTC")
                .setFont(helveticaBold)
                .setFontSize(13)
                .setFontColor(navyColor))
            .setBackgroundColor(highlightBg)
            .setPadding(6)
            .setBorder(new SolidBorder(borderColor, 0.5f)));

        totauxTable.addCell(new Cell()
            .add(new Paragraph(String.format("%.2f €", ttc))
                .setFont(helveticaBold)
                .setFontSize(13)
                .setFontColor(goldColor))
            .setBackgroundColor(highlightBg)
            .setPadding(6)
            .setBorder(new SolidBorder(borderColor, 0.5f))
            .setTextAlignment(TextAlignment.RIGHT));

        doc.add(totauxTable);

        // ── Notes ──
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            SolidLine line2 = new SolidLine(0.5f);
            line2.setColor(borderColor);
            LineSeparator separator2 = new LineSeparator(line2);
            separator2.setMarginTop(8);
            doc.add(separator2);

            doc.add(new Paragraph("Conditions de paiement")
                .setFont(helveticaBold)
                .setFontSize(10)
                .setFontColor(navyColor)
                .setMarginTop(8));

            doc.add(new Paragraph(invoice.getNotes())
                .setFont(helvetica)
                .setFontSize(8)
                .setFontColor(mutedColor)
                .setMarginTop(4));
        }

        doc.close();
        return baos.toByteArray();

    } catch (Exception e) {
        log.error("Erreur génération PDF pour facture {}", invoiceId, e);
        throw new RuntimeException("Impossible de générer le PDF", e);
    }
}
}
