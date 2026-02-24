package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.CaseShareLink;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseShareLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de partage de dossier entre avocats via lien temporaire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseShareService {

    private final CaseShareLinkRepository shareLinkRepository;
    private final CaseService caseService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@docavocat.fr}")
    private String fromEmail;

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    /**
     * Crée un lien de partage pour un dossier.
     *
     * @param caseId       ID du dossier
     * @param owner        Avocat propriétaire
     * @param description  Note pour le destinataire
     * @param expiresAt    Date d'expiration (null = sans expiration)
     * @param maxAccessCount Nombre max d'accès (null = illimité)
     * @param emailTo      Email du destinataire (optionnel, pour envoi automatique)
     */
    @Transactional
    public CaseShareLink createShareLink(
            String caseId,
            User owner,
            String description,
            LocalDateTime expiresAt,
            Integer maxAccessCount,
            String emailTo
    ) {
        Case caseEntity = caseService.getCaseById(caseId);

        // Vérifier que l'avocat est propriétaire du dossier
        if (!caseEntity.getLawyer().getId().equals(owner.getId())) {
            throw new RuntimeException("Accès non autorisé : vous n'êtes pas propriétaire de ce dossier");
        }

        CaseShareLink link = new CaseShareLink();
        link.setSharedCase(caseEntity);
        link.setOwner(owner);
        link.setDescription(description);
        link.setExpiresAt(expiresAt);
        link.setMaxAccessCount(maxAccessCount);

        CaseShareLink saved = shareLinkRepository.save(link);
        log.info("[CaseShare] Lien créé pour dossier {} par avocat {}", caseId, owner.getEmail());

        // Envoyer par email si destinataire fourni
        if (emailTo != null && !emailTo.isBlank()) {
            sendShareEmail(emailTo, owner, caseEntity, saved.getToken(), description);
        }

        return saved;
    }

    /**
     * Récupère un lien de partage par son token (lecture seule, sans incrémenter le compteur).
     */
    @Transactional(readOnly = true)
    public CaseShareLink getLinkByToken(String token) {
        return shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien de partage introuvable"));
    }

    /**
     * Révoque un lien de partage par son token.
     */
    @Transactional
    public void revokeByToken(String token) {
        CaseShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien de partage introuvable"));
        link.setRevoked(true);
        shareLinkRepository.save(link);
        log.info("[CaseShare] Lien révoqué par token pour dossier {}", link.getSharedCase().getId());
    }

    /**
     * Accède à un dossier partagé via token.
     * Incrémente le compteur d'accès.
     */
    @Transactional
    public CaseShareLink accessByToken(String token) {
        CaseShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien de partage introuvable"));

        if (!link.isValid()) {
            throw new RuntimeException("Ce lien de partage est expiré ou révoqué");
        }

        link.setAccessCount(link.getAccessCount() + 1);
        return shareLinkRepository.save(link);
    }

    /**
     * Récupère tous les liens de partage d'un dossier.
     */
    @Transactional(readOnly = true)
    public List<CaseShareLink> getLinksForCase(String caseId) {
        return shareLinkRepository.findByCaseId(caseId);
    }

    /**
     * Révoque un lien de partage.
     */
    @Transactional
    public void revokeLink(String linkId, User owner) {
        CaseShareLink link = shareLinkRepository.findById(linkId)
            .orElseThrow(() -> new RuntimeException("Lien introuvable"));
        if (!link.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Accès non autorisé");
        }
        link.setRevoked(true);
        shareLinkRepository.save(link);
    }

    /**
     * Construit l'URL publique d'accès pour un lien de partage.
     *
     * @param token   Token du lien
     * @param emailTo Email du destinataire (pour déterminer le type d'URL)
     * @return URL publique complète
     */
    public String buildPublicUrl(String token, String emailTo) {
        if (emailTo != null && !emailTo.isBlank()) {
            return baseUrl + "/collaborators/accept-invitation?token=" + token;
        }
        return baseUrl + "/cases/shared?token=" + token;
    }

    // =========================================================================
    // Email
    // =========================================================================

    private void sendShareEmail(String to, User owner, Case caseEntity, String token, String description) {
        try {
            String link = baseUrl + "/cases/shared?token=" + token;
            String lawyerName = owner.getFirstName() + " " + owner.getLastName();

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject("Dossier partagé par " + lawyerName + " — DocAvocat");
            msg.setText(
                "Bonjour,\n\n" +
                "Maître " + lawyerName + " vous partage l'accès au dossier « " + caseEntity.getName() + " ».\n\n" +
                (description != null && !description.isBlank() ? "Note : " + description + "\n\n" : "") +
                "Accédez au dossier en cliquant sur ce lien :\n" + link + "\n\n" +
                "L'équipe DocAvocat\n" + baseUrl
            );
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("[CaseShare] Impossible d'envoyer l'email à {} : {}", to, e.getMessage());
        }
    }
}
