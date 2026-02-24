package com.gedavocat.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Service d'ajout de filigrane (watermark) sur des documents PDF.
 * Les filigranes sont appliqués de manière persistante au moment de l'upload :
 *
 * - "COPIE"          : document déposé par un client
 * - "CONFIDENTIEL"   : document déposé par un avocat
 */
@Slf4j
@Service
public class WatermarkService {

    public static final String WATERMARK_COPIE = "COPIE";
    public static final String WATERMARK_CONFIDENTIEL = "CONFIDENTIEL";

    /**
     * Ajoute un filigrane diagonal en gris semi-transparent sur toutes les pages d'un PDF.
     *
     * @param inputStream  flux d'entrée du PDF original
     * @param watermarkText texte du filigrane (ex: "COPIE" ou "CONFIDENTIEL")
     * @return octets du PDF avec filigrane
     */
    public byte[] addWatermark(InputStream inputStream, String watermarkText) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(inputStream);
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(reader, writer);
            Document layoutDoc = new Document(pdfDoc);

            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            int numberOfPages = pdfDoc.getNumberOfPages();

            for (int i = 1; i <= numberOfPages; i++) {
                PdfPage page = pdfDoc.getPage(i);
                Rectangle pageSize = page.getPageSizeWithRotation();

                PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);

                // Position centrale
                float x = pageSize.getWidth() / 2;
                float y = pageSize.getHeight() / 2;

                canvas.saveState();
                canvas.setFillColor(new DeviceGray(0.75f));
                canvas.setLineWidth(0.5f);

                // Rotation 45° centré sur la page
                canvas.concatMatrix(
                    Math.cos(Math.toRadians(45)), Math.sin(Math.toRadians(45)),
                    -Math.sin(Math.toRadians(45)), Math.cos(Math.toRadians(45)),
                    x, y
                );

                // Texte du filigrane
                canvas.beginText();
                canvas.setFontAndSize(font, 60);
                canvas.setTextMatrix(0, 0);
                canvas.showText(watermarkText);
                canvas.endText();

                canvas.restoreState();
            }

            layoutDoc.close();
            log.debug("[Watermark] Filigrane '{}' ajouté sur {} page(s)", watermarkText, numberOfPages);
            return baos.toByteArray();

        } catch (Exception e) {
            log.warn("[Watermark] Impossible d'ajouter le filigrane '{}' : {}", watermarkText, e.getMessage());
            // En cas d'erreur, on retourne null pour que l'appelant gère le fallback
            return null;
        }
    }

    /**
     * Vérifie si le fichier est un PDF en regardant les premiers octets (magic bytes).
     */
    public boolean isPdf(byte[] data) {
        return data != null && data.length > 4
            && data[0] == 0x25 // %
            && data[1] == 0x50 // P
            && data[2] == 0x44 // D
            && data[3] == 0x46; // F
    }
}
