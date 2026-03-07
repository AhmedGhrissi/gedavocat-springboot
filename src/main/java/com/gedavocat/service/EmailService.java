package com.gedavocat.service;

import com.gedavocat.model.User;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EmailService {

    private final JavaMailSender mailSender;

    // ── [FIX 1] Utiliser app.mail.from (domaine vérifié Brevo) ──────────────
    // ❌ Avant : spring.mail.username → adresse de connexion SMTP, pas le From
    // ✅ Après : app.mail.from       → noreply@docavocat.fr (domaine vérifié)
    @Value("${app.mail.from:noreply@docavocat.fr}")
    private String fromEmail;

    @Value("${app.name:DocAvocat}")
    private String appName;

    // ── [FIX 2] Nom affiché dans le From ────────────────────────────────────
    // Brevo recommande un From avec nom : "DocAvocat <noreply@docavocat.fr>"
    // Réduit considérablement le score spam
    @Value("${app.mail.from.name:DocAvocat - Cabinet Juridique}")
    private String fromName;

    // ── [FIX 3] Reply-To séparé du From ─────────────────────────────────────
    @Value("${app.mail.replyto:contact@docavocat.fr}")
    private String replyTo;

    // ────────────────────────────────────────────────────────────────────────
    // API PUBLIQUE
    // ────────────────────────────────────────────────────────────────────────

    public void sendEmail(String to, String subject, String body) {
        String html = buildSimpleHtml(subject, body);
        sendHtmlEmail(to, subject, html);
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            // ── [FIX 1] From avec nom affiché ────────────────────────────────
            helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));

            // ── [FIX 3] Reply-To distinct du From ────────────────────────────
            helper.setReplyTo(replyTo);

            helper.setTo(to);
            helper.setSubject(subject);

            // ── [FIX 4] Fournir TOUJOURS plain text + html ───────────────────
            // Obligatoire : les filtres anti-spam pénalisent les emails html-only
            String plainText = htmlToPlainText(htmlBody);
            helper.setText(plainText, htmlBody);

            mailSender.send(message);
            // ── [FIX 5] Ne pas logger l'adresse email complète (RGPD) ────────
            log.info("Email envoyé [{}] à {}***", subject, maskEmail(to));

        } catch (Exception e) {
            // ── [FIX 6] Ne pas logger l'adresse complète ni le message d'erreur complet
            log.error("Erreur envoi email [{}] à {}*** : {}",
                    subject, maskEmail(to), e.getMessage());
        }
    }

    public void sendEmailFromLawyer(String to, String subject, String contentHtml, User lawyer) {
        String html = buildLawyerEmail(subject, contentHtml, lawyer);
        sendHtmlEmail(to, subject, html);
    }

    // ────────────────────────────────────────────────────────────────────────
    // BUILDERS HTML
    // ────────────────────────────────────────────────────────────────────────

    public String buildLawyerEmail(String title, String contentHtml, User lawyer) {
        String lawyerName = buildLawyerName(lawyer);
        String phone     = lawyer.getPhone()     != null ? lawyer.getPhone()     : "";
        String barNumber = lawyer.getBarNumber() != null ? "Barreau n°\u00a0" + lawyer.getBarNumber() : "";
        String email     = lawyer.getEmail()     != null ? lawyer.getEmail()     : "";

        String signatureBlock;
        if (lawyer.getEmailSignature() != null && !lawyer.getEmailSignature().isBlank()) {
            signatureBlock = "<div style='white-space:pre-line;color:#374151;font-size:14px;line-height:1.7'>"
                    + escapeHtml(lawyer.getEmailSignature()) + "</div>";
        } else {
            signatureBlock = buildDefaultSignature(lawyerName, phone, barNumber, email);
        }

        return buildEmailTemplate(title, contentHtml, signatureBlock);
    }

    private String buildSimpleHtml(String title, String body) {
        String contentHtml = "<p style='color:#374151;font-size:15px;line-height:1.7;margin:0 0 16px'>"
                + escapeHtml(body).replace("\n", "<br>") + "</p>";
        String sig = "<p style='margin:0;color:#64748B;font-size:13px'>" + escapeHtml(appName) + "</p>";
        return buildEmailTemplate(title, contentHtml, sig);
    }

    private String buildEmailTemplate(String title, String contentHtml, String signatureBlock) {
        return "<!DOCTYPE html>"
            + "<html lang='fr'><head>"
            + "<meta charset='UTF-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            // ── [FIX 7] Meta X-Mailer Brevo compatible ───────────────────────
            + "<meta name='x-apple-disable-message-reformatting'>"
            + "<title>" + escapeHtml(title) + "</title></head>"
            + "<body style='margin:0;padding:0;background-color:#F1F5F9;"
            + "font-family:\"Inter\",\"Helvetica Neue\",Arial,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0'"
            + " style='background:#F1F5F9;padding:40px 20px'>"
            + "<tr><td align='center'>"
            + "<table width='600' cellpadding='0' cellspacing='0' border='0'"
            + " style='max-width:600px;width:100%'>"

            // Header
            + "<tr><td style='background:#1E3A5F;border-radius:8px 8px 0 0;padding:28px 40px'>"
            + "<table width='100%' cellpadding='0' cellspacing='0'><tr>"
            + "<td><span style='color:#FFFFFF;font-size:20px;font-weight:700;letter-spacing:-0.02em'>"
            + escapeHtml(appName) + "</span></td>"
            + "<td align='right'><span style='color:#C6A75E;font-size:11px;font-weight:600;"
            + "letter-spacing:0.1em;text-transform:uppercase'>Communication officielle</span></td>"
            + "</tr></table></td></tr>"

            // Corps
            + "<tr><td style='background:#FFFFFF;padding:40px'>"
            + "<h1 style='margin:0 0 24px;color:#0F172A;font-size:18px;font-weight:700;"
            + "line-height:1.3;border-bottom:2px solid #F1F5F9;padding-bottom:16px'>"
            + escapeHtml(title) + "</h1>"
            + contentHtml
            + "</td></tr>"

            // Signature
            + "<tr><td style='background:#F8FAFC;border:1px solid #E2E8F0;"
            + "border-top:3px solid #C6A75E;border-radius:0 0 8px 8px;padding:28px 40px'>"
            + "<p style='margin:0 0 12px;color:#94A3B8;font-size:10px;font-weight:700;"
            + "letter-spacing:0.12em;text-transform:uppercase'>De la part de</p>"
            + "<div>" + signatureBlock + "</div>"
            + "<hr style='border:none;border-top:1px solid #E2E8F0;margin:20px 0'>"
            + "<p style='margin:0;color:#94A3B8;font-size:11px;line-height:1.6'>"
            + "Ce message est confidentiel et destiné exclusivement à son destinataire. "
            + "Toute divulgation, copie ou distribution non autorisée est strictement interdite.</p>"
            + "</td></tr>"

            + "</table>"
            + "</td></tr></table>"
            + "</body></html>";
    }

    private String buildDefaultSignature(String name, String phone, String barNumber, String email) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='margin:0;color:#0F172A;font-size:15px;font-weight:700'>")
          .append(escapeHtml(name)).append("</p>");
        sb.append("<p style='margin:3px 0 0;color:#64748B;font-size:13px'>Avocat au Barreau</p>");
        if (!barNumber.isEmpty()) {
            sb.append("<p style='margin:6px 0 0;color:#64748B;font-size:13px'>")
              .append(escapeHtml(barNumber)).append("</p>");
        }
        if (!phone.isEmpty()) {
            sb.append("<p style='margin:4px 0 0;color:#64748B;font-size:13px'>Tél. : ")
              .append(escapeHtml(phone)).append("</p>");
        }
        if (!email.isEmpty()) {
            sb.append("<p style='margin:4px 0 0;font-size:13px'><a href='mailto:")
              .append(escapeHtml(email))
              .append("' style='color:#1E3A5F;text-decoration:none'>")
              .append(escapeHtml(email)).append("</a></p>");
        }
        return sb.toString();
    }

    private String buildLawyerName(User lawyer) {
        if (lawyer.getFirstName() != null && !lawyer.getFirstName().isBlank()
                && lawyer.getLastName() != null && !lawyer.getLastName().isBlank()) {
            return "Me\u00a0" + lawyer.getFirstName() + " " + lawyer.getLastName();
        }
        if (lawyer.getName() != null && !lawyer.getName().isBlank()) {
            return lawyer.getName();
        }
        return lawyer.getEmail();
    }

    // ────────────────────────────────────────────────────────────────────────
    // UTILITAIRES
    // ────────────────────────────────────────────────────────────────────────

    /**
     * [FIX 4] Génère une version plain text à partir du HTML.
     * Obligatoire pour ne pas être classé spam (html-only pénalisé).
     */
    private String htmlToPlainText(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<p[^>]*>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<li[^>]*>", "\n• ")
                .replaceAll("(?i)<h[1-6][^>]*>", "\n")
                .replaceAll("(?i)</h[1-6]>", "\n")
                .replaceAll("(?i)<hr\\s*/?>", "\n---\n")
                .replaceAll("<[^>]+>", "")           // supprimer tous les autres tags
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\n{3,}", "\n\n")        // max 2 sauts de ligne consécutifs
                .trim();
    }

    /**
     * [FIX 5] Masque l'email pour les logs — conformité RGPD.
     * Ex: ahmed.test@docavocat.fr → ah***@docavocat.fr
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) return "***@" + domain;
        return local.substring(0, 2) + "***@" + domain;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}