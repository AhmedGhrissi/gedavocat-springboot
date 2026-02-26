package com.gedavocat.service;

import com.gedavocat.model.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@docavocat.fr}")
    private String fromEmail;

    @Value("${app.name:GedAvocat}")
    private String appName;

    public void sendEmail(String to, String subject, String body) {
        String html = buildSimpleHtml(subject, body);
        sendHtmlEmail(to, subject, html);
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email envoyé à {} : {}", to, subject);
        } catch (Exception e) {
            log.error("Erreur envoi email à {} : {}", to, e.getMessage());
        }
    }

    public void sendEmailFromLawyer(String to, String subject, String contentHtml, User lawyer) {
        String html = buildLawyerEmail(subject, contentHtml, lawyer);
        sendHtmlEmail(to, subject, html);
    }

    public String buildLawyerEmail(String title, String contentHtml, User lawyer) {
        String lawyerName = buildLawyerName(lawyer);
        String phone = lawyer.getPhone() != null ? lawyer.getPhone() : "";
        String barNumber = lawyer.getBarNumber() != null ? "Barreau n\u00b0\u00a0" + lawyer.getBarNumber() : "";
        String email = lawyer.getEmail() != null ? lawyer.getEmail() : "";

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
            + "<html lang='fr'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>" + escapeHtml(title) + "</title></head>"
            + "<body style='margin:0;padding:0;background-color:#F1F5F9;font-family:\"Inter\",\"Helvetica Neue\",Arial,sans-serif'>"
            + "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='background:#F1F5F9;padding:40px 20px'>"
            + "<tr><td align='center'>"
            + "<table width='600' cellpadding='0' cellspacing='0' border='0' style='max-width:600px;width:100%'>"
            + "<tr><td style='background:#1E3A5F;border-radius:8px 8px 0 0;padding:28px 40px'>"
            + "<table width='100%' cellpadding='0' cellspacing='0'><tr>"
            + "<td><span style='color:#FFFFFF;font-size:20px;font-weight:700;letter-spacing:-0.02em'>"
            + escapeHtml(appName) + "</span></td>"
            + "<td align='right'><span style='color:#C6A75E;font-size:11px;font-weight:600;letter-spacing:0.1em;text-transform:uppercase'>Communication officielle</span></td>"
            + "</tr></table></td></tr>"
            + "<tr><td style='background:#FFFFFF;padding:40px'>"
            + "<h1 style='margin:0 0 24px;color:#0F172A;font-size:18px;font-weight:700;line-height:1.3;"
            + "border-bottom:2px solid #F1F5F9;padding-bottom:16px'>"
            + escapeHtml(title) + "</h1>"
            + contentHtml
            + "</td></tr>"
            + "<tr><td style='background:#F8FAFC;border:1px solid #E2E8F0;border-top:3px solid #C6A75E;"
            + "border-radius:0 0 8px 8px;padding:28px 40px'>"
            + "<p style='margin:0 0 12px;color:#94A3B8;font-size:10px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase'>De la part de</p>"
            + "<div>" + signatureBlock + "</div>"
            + "<hr style='border:none;border-top:1px solid #E2E8F0;margin:20px 0'>"
            + "<p style='margin:0;color:#94A3B8;font-size:11px;line-height:1.6'>"
            + "Ce message est confidentiel et destin\u00e9 exclusivement \u00e0 son destinataire. "
            + "Toute divulgation, copie ou distribution non autoris\u00e9e est strictement interdite.</p>"
            + "</td></tr>"
            + "</table>"
            + "</td></tr></table>"
            + "</body></html>";
    }

    private String buildDefaultSignature(String name, String phone, String barNumber, String email) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p style='margin:0;color:#0F172A;font-size:15px;font-weight:700'>").append(escapeHtml(name)).append("</p>");
        sb.append("<p style='margin:3px 0 0;color:#64748B;font-size:13px'>Avocat au Barreau</p>");
        if (!barNumber.isEmpty()) {
            sb.append("<p style='margin:6px 0 0;color:#64748B;font-size:13px'>").append(escapeHtml(barNumber)).append("</p>");
        }
        if (!phone.isEmpty()) {
            sb.append("<p style='margin:4px 0 0;color:#64748B;font-size:13px'>T\u00e9l. : ").append(escapeHtml(phone)).append("</p>");
        }
        if (!email.isEmpty()) {
            sb.append("<p style='margin:4px 0 0;font-size:13px'><a href='mailto:").append(escapeHtml(email))
              .append("' style='color:#1E3A5F;text-decoration:none'>").append(escapeHtml(email)).append("</a></p>");
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

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
