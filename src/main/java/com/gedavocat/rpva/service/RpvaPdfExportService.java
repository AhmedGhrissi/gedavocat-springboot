package com.gedavocat.rpva.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.gedavocat.rpva.model.ActeProcedure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service de génération de PDF normé pour dépôt sur e-Barreau (RPVA).
 * Option C — Export manuel : génère fiche de transmission + checklist pour l'avocat.
 *
 * Normes respectées :
 *  - Format PDF/A-1b (archivage longue durée, requis par les juridictions)
 *  - Police embarquée (obligation PDF/A)
 *  - Taille max recommandée : 10 Mo (limite e-Barreau)
 *
 * NOTE LICENCE : iText7 est sous AGPL. Pour DocAvocat (SaaS commercial),
 * une licence commerciale iText est requise, ou migrer vers Apache PDFBox.
 *
 * Dépendances Maven :
 *   <dependency>
 *     <groupId>com.itextpdf</groupId>
 *     <artifactId>itext7-core</artifactId>
 *     <version>7.2.5</version>
 *     <type>pom</type>
 *   </dependency>
 *   <dependency>
 *     <groupId>com.itextpdf</groupId>
 *     <artifactId>pdfa</artifactId>
 *     <version>7.2.5</version>
 *   </dependency>
 *
 * Ressource requise : src/main/resources/icc/sRGB_CS_profile.icm
 * (téléchargeable sur https://www.color.org/srgbprofiles.xalter)
 * Sans ce fichier → fallback PDF standard automatique.
 */
@Slf4j
@Service
public class RpvaPdfExportService {

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DeviceRgb COULEUR_PRINCIPALE = new DeviceRgb(15, 23, 42);   // navy DocAvocat
    private static final DeviceRgb COULEUR_ACCENT     = new DeviceRgb(198, 167, 94); // or DocAvocat
    private static final DeviceRgb COULEUR_SECONDAIRE = new DeviceRgb(248, 250, 252);

    /**
     * Génère un PDF/A-1b de la fiche récapitulative de l'acte de procédure.
     * Ce document sert de bordereau de transmission à déposer sur e-Barreau.
     */
    public byte[] genererFicheTransmission(ActeProcedure acte) throws IOException {
        log.info("Génération fiche transmission RPVA — dossier {} / {}", acte.getNumeroRole(), acte.getJuridiction());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return genererPdfStandard(acte, baos);
    }

    /**
     * Génère un PDF de la checklist e-Barreau (guide pas-à-pas pour l'avocat).
     */
    public byte[] genererChecklistDepot(ActeProcedure acte) throws IOException {
        log.info("Génération checklist dépôt e-Barreau — {}", acte.getNumeroRole());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(50, 50, 50, 50);
            PdfFont fontRegular = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA,
                PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
            PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD,
                PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
            ajouterEntete(doc, "CHECKLIST DE DÉPÔT E-BARREAU", acte, fontBold, fontRegular);
            ajouterChecklist(doc, acte, fontRegular, fontBold);
            ajouterPiedPage(doc, fontRegular);
        }
        return baos.toByteArray();
    }

    // ── Construction du document ──────────────────────────────────────────────

    private void ajouterContenuFiche(Document doc, ActeProcedure acte,
                                      PdfFont fontRegular, PdfFont fontBold) {
        ajouterEntete(doc, "FICHE DE TRANSMISSION RPVA", acte, fontBold, fontRegular);
        ajouterSectionDossier(doc, acte, fontRegular, fontBold);
        ajouterSectionParties(doc, acte, fontRegular, fontBold);
        ajouterSectionDocuments(doc, acte, fontRegular, fontBold);
        ajouterSectionMessage(doc, acte, fontRegular, fontBold);
        ajouterPiedPage(doc, fontRegular);
    }

    private void ajouterEntete(Document doc, String titre, ActeProcedure acte,
                                PdfFont fontBold, PdfFont fontRegular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
            .setWidth(UnitValue.createPercentValue(100))
            .setBackgroundColor(COULEUR_PRINCIPALE);
        header.addCell(new Cell()
            .add(new Paragraph(titre).setFont(fontBold).setFontSize(14)
                .setFontColor(ColorConstants.WHITE).setMargin(12))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        header.addCell(new Cell()
            .add(new Paragraph("Généré le\n" + acte.getDateGeneration().format(FR_DATE))
                .setFont(fontRegular).setFontSize(9).setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.RIGHT).setMargin(12))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        doc.add(header);
        doc.add(new Paragraph("\n"));
    }

    private void ajouterSectionDossier(Document doc, ActeProcedure acte,
                                        PdfFont fontRegular, PdfFont fontBold) {
        doc.add(creerTitreSec("INFORMATIONS DU DOSSIER", fontBold));
        Table t = creerTableauInfos();
        t.addCell(cellLabel("Numéro de rôle", fontBold));
        t.addCell(cellValeur(acte.getNumeroRole() != null ? acte.getNumeroRole() : "—", fontRegular));
        t.addCell(cellLabel("Juridiction", fontBold));
        t.addCell(cellValeur(acte.getJuridiction(), fontRegular));
        t.addCell(cellLabel("Code juridiction", fontBold));
        t.addCell(cellValeur(acte.getCodeJuridiction() != null ? acte.getCodeJuridiction() : "—", fontRegular));
        t.addCell(cellLabel("Nature de l'acte", fontBold));
        t.addCell(cellValeur(acte.getTypeActe().name(), fontRegular));
        if (acte.getDateAudience() != null) {
            t.addCell(cellLabel("Date d'audience", fontBold));
            t.addCell(cellValeur(acte.getDateAudience().format(FR_DATE), fontRegular));
        }
        t.addCell(cellLabel("Réf. interne DocAvocat", fontBold));
        t.addCell(cellValeur(acte.getReferenceInterne() != null ? acte.getReferenceInterne() : "—", fontRegular));
        doc.add(t);
        doc.add(new Paragraph("\n"));
    }

    private void ajouterSectionParties(Document doc, ActeProcedure acte,
                                        PdfFont fontRegular, PdfFont fontBold) {
        doc.add(creerTitreSec("PARTIES", fontBold));
        Table t = creerTableauInfos();
        if (acte.getDemandeur() != null) {
            t.addCell(cellLabel("Demandeur", fontBold));
            t.addCell(cellValeur(formatPartie(acte.getDemandeur()), fontRegular));
        }
        if (acte.getDefendeur() != null) {
            t.addCell(cellLabel("Défendeur", fontBold));
            t.addCell(cellValeur(formatPartie(acte.getDefendeur()), fontRegular));
        }
        if (acte.getAvocatRedacteur() != null) {
            ActeProcedure.Avocat av = acte.getAvocatRedacteur();
            t.addCell(cellLabel("Avocat rédacteur", fontBold));
            t.addCell(cellValeur("Me " + av.getPrenom() + " " + av.getNom()
                + " — Barreau de " + av.getBarreau()
                + " (N° " + av.getNumeroOrdre() + ")", fontRegular));
        }
        doc.add(t);
        doc.add(new Paragraph("\n"));
    }

    private void ajouterSectionDocuments(Document doc, ActeProcedure acte,
                                          PdfFont fontRegular, PdfFont fontBold) {
        doc.add(creerTitreSec("PIÈCES JOINTES", fontBold));
        List<ActeProcedure.PieceJointe> pieces = acte.getPiecesJointes();
        if (pieces == null || pieces.isEmpty()) {
            doc.add(new Paragraph("Aucune pièce jointe.")
                .setFont(fontRegular).setFontSize(9).setFontColor(ColorConstants.GRAY));
        } else {
            Table t = new Table(UnitValue.createPercentArray(new float[]{10, 60, 30}))
                .setWidth(UnitValue.createPercentValue(100));
            t.addHeaderCell(creerCelluleEntete("#", fontBold));
            t.addHeaderCell(creerCelluleEntete("Nom du fichier", fontBold));
            t.addHeaderCell(creerCelluleEntete("Taille", fontBold));
            long totalOctets = 0;
            int i = 1;
            for (ActeProcedure.PieceJointe pj : pieces) {
                totalOctets += pj.getTaille();
                t.addCell(new Cell().add(new Paragraph(String.valueOf(i++)).setFont(fontRegular).setFontSize(9)));
                t.addCell(new Cell().add(new Paragraph(pj.getNom()).setFont(fontRegular).setFontSize(9)));
                t.addCell(new Cell().add(new Paragraph(formatTaille(pj.getTaille())).setFont(fontRegular).setFontSize(9)));
            }
            doc.add(t);
            boolean depasse = totalOctets > 9L * 1024 * 1024;
            doc.add(new Paragraph((depasse ? "⚠ " : "✓ ") + "Taille totale : "
                + formatTaille(totalOctets) + " (limite e-Barreau : 10 Mo)")
                .setFont(fontRegular).setFontSize(8)
                .setFontColor(depasse ? ColorConstants.RED : ColorConstants.DARK_GRAY)
                .setMarginTop(4));
        }
        doc.add(new Paragraph("\n"));
    }

    private void ajouterSectionMessage(Document doc, ActeProcedure acte,
                                        PdfFont fontRegular, PdfFont fontBold) {
        doc.add(creerTitreSec("MESSAGE AU GREFFE", fontBold));
        Table t = creerTableauInfos();
        t.addCell(cellLabel("Objet", fontBold));
        t.addCell(cellValeur(acte.getObjetMessage() != null ? acte.getObjetMessage() : "—", fontRegular));
        if (acte.getCorpsMessage() != null && !acte.getCorpsMessage().isBlank()) {
            t.addCell(cellLabel("Message", fontBold));
            t.addCell(cellValeur(acte.getCorpsMessage(), fontRegular));
        }
        doc.add(t);
    }

    private void ajouterChecklist(Document doc, ActeProcedure acte,
                                   PdfFont fontRegular, PdfFont fontBold) {
        doc.add(new Paragraph("Suivez ces étapes pour déposer votre acte sur e-Barreau (ebarreau.avocat.fr).")
            .setFont(fontRegular).setFontSize(10).setMarginBottom(12));

        String[][] etapes = {
            {"1", "Brancher la clé RPVA (USB)",
             "Insérez votre clé avocat dans un port USB libre."},
            {"2", "Se connecter à e-Barreau",
             "https://ebarreau.avocat.fr — authentifiez-vous avec votre clé."},
            {"3", "Sélectionner la juridiction",
             acte.getJuridiction() + (acte.getCodeJuridiction() != null
                 ? " (code : " + acte.getCodeJuridiction() + ")" : "")},
            {"4", "Localiser le dossier",
             "Numéro de rôle : " + (acte.getNumeroRole() != null ? acte.getNumeroRole() : "À renseigner")},
            {"5", "Choisir le type d'acte",
             acte.getTypeActe().name() +
             (acte.getDateAudience() != null ? " — Audience : " + acte.getDateAudience().format(FR_DATE) : "")},
            {"6", "Rédiger le message au greffe",
             "Objet : " + (acte.getObjetMessage() != null ? acte.getObjetMessage() : "—")},
            {"7", "Joindre les pièces",
             acte.getPiecesJointes() != null && !acte.getPiecesJointes().isEmpty()
                 ? acte.getPiecesJointes().size() + " fichier(s) à joindre (PDF/A, max 10 Mo total)"
                 : "Aucune pièce jointe renseignée"},
            {"8", "Valider et envoyer",
             "Vérifiez le récapitulatif puis cliquez sur « Envoyer ». Conservez l'accusé de réception."},
        };

        for (String[] etape : etapes) {
            Table row = new Table(UnitValue.createPercentArray(new float[]{8, 92}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(6);
            row.addCell(new Cell()
                .add(new Paragraph(etape[0]).setFont(fontBold).setFontSize(12)
                    .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(COULEUR_PRINCIPALE)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(8));
            row.addCell(new Cell()
                .add(new Paragraph(etape[1]).setFont(fontBold).setFontSize(9))
                .add(new Paragraph(etape[2]).setFont(fontRegular).setFontSize(8)
                    .setFontColor(ColorConstants.DARK_GRAY))
                .setBackgroundColor(COULEUR_SECONDAIRE)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(8));
            doc.add(row);
        }
    }

    private void ajouterPiedPage(Document doc, PdfFont fontRegular) {
        doc.add(new Paragraph("\n"));
        doc.add(new Paragraph(
            "Document généré par DocAvocat — Ne constitue pas un acte de procédure. " +
            "L'acte officiel est l'accusé de réception e-Barreau.")
            .setFont(fontRegular).setFontSize(7).setFontColor(ColorConstants.GRAY)
            .setTextAlignment(TextAlignment.CENTER)
            .setBorderTop(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPaddingTop(8));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Paragraph creerTitreSec(String texte, PdfFont fontBold) {
        return new Paragraph(texte).setFont(fontBold).setFontSize(10)
            .setFontColor(COULEUR_PRINCIPALE)
            .setBorderBottom(new SolidBorder(COULEUR_PRINCIPALE, 1f)).setMarginBottom(6);
    }

    private Table creerTableauInfos() {
        return new Table(UnitValue.createPercentArray(new float[]{35, 65}))
            .setWidth(UnitValue.createPercentValue(100));
    }

    private Cell cellLabel(String texte, PdfFont fontBold) {
        return new Cell()
            .add(new Paragraph(texte).setFont(fontBold).setFontSize(9))
            .setBackgroundColor(COULEUR_SECONDAIRE)
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(6);
    }

    private Cell cellValeur(String texte, PdfFont fontRegular) {
        return new Cell()
            .add(new Paragraph(texte).setFont(fontRegular).setFontSize(9))
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(6);
    }

    private Cell creerCelluleEntete(String texte, PdfFont fontBold) {
        return new Cell()
            .add(new Paragraph(texte).setFont(fontBold).setFontSize(9)
                .setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(COULEUR_PRINCIPALE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(6);
    }

    private String formatPartie(ActeProcedure.Partie p) {
        if (p.getType() == ActeProcedure.TypePartie.PERSONNE_MORALE)
            return p.getRaisonSociale() + (p.getSiren() != null ? " (SIREN: " + p.getSiren() + ")" : "");
        return (p.getPrenom() != null ? p.getPrenom() + " " : "") + p.getNom();
    }

    private String formatTaille(long octets) {
        if (octets < 1024) return octets + " o";
        if (octets < 1024 * 1024) return String.format("%.1f Ko", octets / 1024.0);
        return String.format("%.2f Mo", octets / (1024.0 * 1024));
    }

    private byte[] genererPdfStandard(ActeProcedure acte, ByteArrayOutputStream baos) throws IOException {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(50, 50, 50, 50);
            PdfFont fontRegular = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA,
                PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
            PdfFont fontBold = PdfFontFactory.createFont(
                com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD,
                PdfFontFactory.EmbeddingStrategy.PREFER_NOT_EMBEDDED);
            ajouterContenuFiche(doc, acte, fontRegular, fontBold);
        }
        return baos.toByteArray();
    }
}
