package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.ClientArchiveToken;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientArchiveTokenRepository;
import com.gedavocat.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gestion des archives légales de suppression de client.
 *
 * Workflow :
 *   1. Génère un ZIP (metadonnées client + documents éventuels)
 *   2. Stocke dans MinIO sous "client-archives/<key>.zip"
 *   3. Crée un ClientArchiveToken avec expiry = 5 ans (délai légal avocat)
 *   4. Envoie un email à l'avocat ET au client avec le lien de téléchargement
 */
@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ClientArchiveService {

    /** Durée légale de conservation pour un avocat (art. 11 RIN). */
    static final int LEGAL_RETENTION_YEARS = 5;

    /**
     * Préfixe de clé MinIO pour les archives.
     * On réutilise le bucket documents pour éviter la création d'un nouveau bucket.
     */
    private static final String BUCKET = "docavocat-documents";
    private static final String KEY_PREFIX = "client-archives/";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StorageService storageService;
    private final ClientArchiveTokenRepository archiveTokenRepository;
    private final DocumentService documentService;
    private final CaseService caseService;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // API publique
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Génère l'archive ZIP, la stocke, crée le token et envoie les emails.
     * Doit être appelé AVANT la suppression effective du client.
     *
     * @param client Le client à archiver (encore en base)
     * @param lawyer L'avocat qui initie la suppression
     * @return Le token persisté (avec son UUID de téléchargement)
     */
    @Transactional
    public ClientArchiveToken archiveBeforeDeletion(Client client, User lawyer) {
        String storageKey = KEY_PREFIX + "archive_" + client.getId() + "_" + UUID.randomUUID() + ".zip";

        // Générer le ZIP
        byte[] zipBytes = generateClientZip(client, lawyer);

        // Stocker dans MinIO
        storageService.storeBytes(BUCKET, storageKey, zipBytes, "application/zip");
        log.info("[ClientArchive] Archive stockée pour le client {} ({} octets)", client.getId(), zipBytes.length);

        // Créer le token
        String token = UUID.randomUUID().toString();
        ClientArchiveToken archiveToken = new ClientArchiveToken();
        archiveToken.setId(UUID.randomUUID().toString());
        archiveToken.setToken(token);
        archiveToken.setClientId(client.getId());
        archiveToken.setClientName(client.getName());
        archiveToken.setClientEmail(client.getEmail());
        archiveToken.setLawyerId(lawyer.getId());
        archiveToken.setLawyerEmail(lawyer.getEmail());
        archiveToken.setStorageKey(storageKey);
        archiveToken.setExpiresAt(LocalDateTime.now().plusYears(LEGAL_RETENTION_YEARS));

        ClientArchiveToken saved = archiveTokenRepository.save(archiveToken);

        // Envoyer les emails — échec SMTP non bloquant (token+archive déjà persistés)
        String downloadUrl = baseUrl + "/clients/archive/" + token + "/download";
        try {
            sendEmailToLawyer(lawyer, client.getName(), downloadUrl);
        } catch (Exception ex) {
            log.warn("[ClientArchive] Email avocat non envoyé ({}) : {}",
                lawyer.getEmail(), ex.getMessage());
        }
        if (client.getEmail() != null && !client.getEmail().isBlank()) {
            try {
                sendEmailToClient(client, lawyer, downloadUrl);
            } catch (Exception ex) {
                log.warn("[ClientArchive] Email client non envoyé ({}) : {}",
                    client.getEmail(), ex.getMessage());
            }
        }

        auditService.log("CLIENT_ARCHIVE_CREATED", "ClientArchiveToken", saved.getId(),
            "Archive légale créée pour le client supprimé : " + client.getName()
            + " | expiry : " + saved.getExpiresAt().format(FMT), lawyer.getId());

        return saved;
    }

    /**
     * Retourne les bytes du ZIP après vérification de validité du token.
     */
    public byte[] downloadArchive(String token) {
        ClientArchiveToken archiveToken = findValidToken(token);
        try {
            byte[] bytes = storageService.getBytes(BUCKET, archiveToken.getStorageKey());
            archiveToken.setDownloadCount(archiveToken.getDownloadCount() + 1);
            archiveTokenRepository.save(archiveToken);
            return bytes;
        } catch (Exception e) {
            log.error("[ClientArchive] Erreur lecture archive {} : {}", archiveToken.getStorageKey(), e.getMessage());
            throw new RuntimeException("Impossible de récupérer l'archive", e);
        }
    }

    /**
     * Retourne les informations du token pour affichage (sans télécharger).
     */
    public ClientArchiveToken findValidToken(String token) {
        ClientArchiveToken archiveToken = archiveTokenRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien de téléchargement invalide"));
        if (LocalDateTime.now().isAfter(archiveToken.getExpiresAt())) {
            throw new RuntimeException("Lien de téléchargement expiré");
        }
        return archiveToken;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Génération du ZIP
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] generateClientZip(Client client, User lawyer) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1 — Fiche récapitulative du client
            addClientMetadataFile(zos, client, lawyer);

            // 2 — Documents des dossiers (safety net : normalement aucun si les dossiers
            //     ont été supprimés avant le client comme le workflow l'exige)
            List<Case> cases = caseService.getCasesByClient(client.getId());
            for (Case caseEntity : cases) {
                String folderName = sanitizeEntry(
                    caseEntity.getName() != null ? caseEntity.getName() : caseEntity.getId());
                List<Document> docs = documentService.getLatestVersions(caseEntity.getId());
                for (Document doc : docs) {
                    try {
                        byte[] fileBytes = documentService.downloadDocument(doc.getId(), lawyer.getId());
                        String filename = sanitizeEntry(
                            doc.getOriginalName() != null ? doc.getOriginalName() : doc.getId());
                        ZipEntry entry = new ZipEntry(folderName + "/" + filename);
                        zos.putNextEntry(entry);
                        zos.write(fileBytes);
                        zos.closeEntry();
                    } catch (Exception ex) {
                        log.warn("[ClientArchive] Document {} ignoré lors de l'archivage : {}",
                            doc.getId(), ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du ZIP d'archive", e);
        }
        return baos.toByteArray();
    }

    private void addClientMetadataFile(ZipOutputStream zos, Client client, User lawyer)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DOSSIER CLIENT — ARCHIVE LÉGALE ===\n");
        sb.append("Généré le             : ").append(LocalDateTime.now().format(FMT)).append("\n");
        sb.append("Durée de conservation : ").append(LEGAL_RETENTION_YEARS).append(" ans (art. 11 RIN)\n\n");

        sb.append("--- INFORMATIONS CLIENT ---\n");
        sb.append("Nom complet   : ").append(nvl(client.getName())).append("\n");
        sb.append("Prénom        : ").append(nvl(client.getFirstName())).append("\n");
        sb.append("Nom           : ").append(nvl(client.getLastName())).append("\n");
        sb.append("Email         : ").append(nvl(client.getEmail())).append("\n");
        sb.append("Téléphone     : ").append(nvl(client.getPhone())).append("\n");
        sb.append("Adresse       : ").append(nvl(client.getAddress())).append("\n");
        if (client.getCreatedAt() != null) {
            sb.append("Créé le       : ").append(client.getCreatedAt().format(FMT)).append("\n");
        }
        sb.append("\n");

        sb.append("--- AVOCAT RESPONSABLE ---\n");
        sb.append("Nom           : ").append(nvl(lawyer.getFirstName()))
          .append(" ").append(nvl(lawyer.getLastName())).append("\n");
        sb.append("Email         : ").append(nvl(lawyer.getEmail())).append("\n");
        if (lawyer.getBarNumber() != null && !lawyer.getBarNumber().isBlank()) {
            sb.append("Barreau n°    : ").append(lawyer.getBarNumber()).append("\n");
        }
        sb.append("\n");

        sb.append("--- SUPPRESSION ---\n");
        sb.append("Date          : ").append(LocalDateTime.now().format(FMT)).append("\n");
        sb.append("Responsable   : ").append(nvl(lawyer.getFirstName())).append(" ")
          .append(nvl(lawyer.getLastName())).append(" (").append(nvl(lawyer.getEmail())).append(")\n");
        sb.append("Confirmation  : L'avocat a certifié avoir pris connaissance des données\n");
        sb.append("               et prend l'entière responsabilité de conservation.\n");

        String filename = "CLIENT_" + sanitizeEntry(nvl(client.getName())) + "_INFORMATIONS.txt";
        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Emails
    // ─────────────────────────────────────────────────────────────────────────

    private void sendEmailToLawyer(User lawyer, String clientName, String downloadUrl) {
        String lawyerDisplay = "Me\u00a0" + lawyer.getFirstName() + " " + lawyer.getLastName();
        String html = buildArchiveEmailHtml(
            "Confirmation de suppression — Dossier client " + escHtml(clientName),
            "<p style='color:#374151;font-size:15px;line-height:1.7;margin:0 0 16px'>"
            + "Vous avez procédé à la <strong>suppression définitive</strong> du dossier client "
            + "<strong>" + escHtml(clientName) + "</strong> de votre espace DocAvocat.</p>"
            + "<p style='color:#374151;font-size:15px;line-height:1.7;margin:0 0 16px'>"
            + "Conformément à l'article\u00a011 du R\u00e8glement Int\u00e9rieur National, "
            + "une archive de ce dossier a été générée et est disponible en téléchargement "
            + "pendant <strong>" + LEGAL_RETENTION_YEARS + " ans</strong>.</p>",
            downloadUrl, clientName);
        emailService.sendHtmlEmail(lawyer.getEmail(),
            "Confirmation de suppression — Dossier client " + clientName, html);
    }

    private void sendEmailToClient(Client client, User lawyer, String downloadUrl) {
        String lawyerDisplay = nvl(lawyer.getFirstName()) + " " + nvl(lawyer.getLastName());
        String html = buildArchiveEmailHtml(
            "Clôture de votre dossier client",
            "<p style='color:#374151;font-size:15px;line-height:1.7;margin:0 0 16px'>"
            + "Votre avocat <strong>" + escHtml(lawyerDisplay) + "</strong> a procédé à la "
            + "clôture et suppression de votre dossier client sur DocAvocat.</p>"
            + "<p style='color:#374151;font-size:15px;line-height:1.7;margin:0 0 16px'>"
            + "Une archive de votre dossier a été générée et est disponible en téléchargement "
            + "pendant <strong>" + LEGAL_RETENTION_YEARS + " ans</strong>. "
            + "Conservez ce lien précieusement.</p>",
            downloadUrl, client.getName());
        emailService.sendHtmlEmail(client.getEmail(),
            "Clôture de votre dossier client — Archive disponible", html);
    }

    private String buildArchiveEmailHtml(String title, String intro, String downloadUrl, String clientName) {
        return "<!DOCTYPE html>"
            + "<html lang='fr'><head><meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            + "<body style='margin:0;padding:0;background:#F1F5F9;"
            + "font-family:\"Inter\",\"Helvetica Neue\",Arial,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'"
            + " style='background:#F1F5F9;padding:40px 20px'><tr><td align='center'>"
            + "<table width='600' cellpadding='0' cellspacing='0' border='0'"
            + " style='max-width:600px;width:100%'>"
            // Header
            + "<tr><td style='background:#1E3A5F;border-radius:8px 8px 0 0;padding:28px 40px'>"
            + "<span style='color:#FFFFFF;font-size:20px;font-weight:700'>DocAvocat</span>"
            + "<span style='float:right;color:#C6A75E;font-size:11px;font-weight:600;"
            + "text-transform:uppercase'>Archive légale</span></td></tr>"
            // Body
            + "<tr><td style='background:#FFFFFF;padding:40px'>"
            + "<h1 style='margin:0 0 24px;color:#0F172A;font-size:18px;font-weight:700;"
            + "border-bottom:2px solid #F1F5F9;padding-bottom:16px'>" + escHtml(title) + "</h1>"
            + intro
            // Download button
            + "<div style='margin:28px 0;text-align:center'>"
            + "<a href='" + downloadUrl + "' "
            + "style='display:inline-block;background:#2563EB;color:#FFFFFF;text-decoration:none;"
            + "padding:14px 28px;border-radius:8px;font-size:15px;font-weight:600;"
            + "letter-spacing:0.01em'>"
            + "&#8659;&nbsp; Télécharger l'archive ZIP</a>"
            + "</div>"
            + "<p style='color:#94A3B8;font-size:12px;line-height:1.6;margin:16px 0 0'>"
            + "Ce lien est valable pendant " + LEGAL_RETENTION_YEARS + " ans à compter de ce jour. "
            + "Vous pouvez y accéder à tout moment sans identification préalable.</p>"
            + "<p style='color:#94A3B8;font-size:12px;margin:8px 0 0;word-break:break-all'>"
            + "Lien direct&nbsp;: " + escHtml(downloadUrl) + "</p>"
            + "</td></tr>"
            // Footer
            + "<tr><td style='background:#F8FAFC;border:1px solid #E2E8F0;"
            + "border-top:3px solid #C6A75E;border-radius:0 0 8px 8px;padding:20px 40px'>"
            + "<p style='margin:0;color:#94A3B8;font-size:11px;line-height:1.6'>"
            + "Ce message est confidentiel et destiné exclusivement à son destinataire. "
            + "Toute divulgation non autorisée est interdite.</p>"
            + "</td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String nvl(String v) {
        return v != null && !v.isBlank() ? v : "-";
    }

    private String sanitizeEntry(String name) {
        if (name == null || name.isBlank()) return "inconnu";
        String s = name.replace("\\", "/");
        int last = s.lastIndexOf('/');
        if (last >= 0) s = s.substring(last + 1);
        return s.replaceAll("[\\r\\n\"<>|?*:/]", "_").trim().isEmpty() ? "inconnu"
             : s.replaceAll("[\\r\\n\"<>|?*:/]", "_").trim();
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
