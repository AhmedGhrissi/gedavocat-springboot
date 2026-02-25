package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.CaseShareLink;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseShareLinkRepository;
import com.gedavocat.repository.PermissionRepository;
import com.gedavocat.repository.UserRepository;
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
    private final CollaboratorInvitationService collaboratorInvitationService;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

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

        // New: prevent sharing to the same collaborator twice
        if (emailTo != null && !emailTo.isBlank()) {
            // Trim/normalize email
            String normalized = emailTo.trim().toLowerCase();

            // If a user account exists for this email and already has read access, reject
            try {
                java.util.Optional<User> maybeUser = userRepository.findByEmail(normalized);
                if (maybeUser.isPresent()) {
                    User existingUser = maybeUser.get();
                    if (permissionRepository.hasReadAccess(caseId, existingUser.getId())) {
                        throw new RuntimeException("Impossible : ce collaborateur a déjà accès à ce dossier.");
                    }
                }
            } catch (RuntimeException re) {
                throw re; // rethrow explicit business errors
            } catch (Exception e) {
                log.warn("[CaseShare] Erreur lors de la vérification des permissions pour {} : {}", emailTo, e.getMessage());
            }

            // Also ensure there isn't already an active invite for this email on this case
            try {
                List<CaseShareLink> existing = shareLinkRepository.findActiveByCaseIdAndRecipientEmail(caseId, normalized);
                if (existing != null && !existing.isEmpty()) {
                    throw new RuntimeException("Un lien d'invitation actif existe déjà pour cet email.");
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                log.warn("[CaseShare] Erreur lors de la vérification des invitations actives pour {} : {}", emailTo, e.getMessage());
            }

            // replace emailTo by normalized form to store/send
            emailTo = normalized;
        }

        CaseShareLink link = new CaseShareLink();
        link.setSharedCase(caseEntity);
        link.setOwner(owner);
        link.setDescription(description);
        link.setExpiresAt(expiresAt);
        link.setMaxAccessCount(maxAccessCount);

        // If an email recipient is provided, store it and mark invitedAt so collaborator flow works
        if (emailTo != null && !emailTo.isBlank()) {
            link.setRecipientEmail(emailTo);
            link.setInvitedAt(LocalDateTime.now());
        }

        CaseShareLink saved = shareLinkRepository.save(link);
        log.info("[CaseShare] Lien créé pour dossier {} par avocat {}", caseId, owner.getEmail());

        // Register in the collaborator invitation service so in-memory validation works
        try {
            if (emailTo != null && !emailTo.isBlank()) {
                collaboratorInvitationService.registerInvitation(saved);
            }
        } catch (Exception e) {
            log.warn("[CaseShare] Impossible d'enregistrer l'invitation en mémoire: {}", e.getMessage());
        }

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
        CaseShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien de partage introuvable"));
        // Initialize lazy associations while still in transaction/session to avoid LazyInitializationException
        try {
            if (link.getSharedCase() != null) {
                link.getSharedCase().getName();
                if (link.getSharedCase().getClient() != null) link.getSharedCase().getClient().getName();
                if (link.getSharedCase().getLawyer() != null) link.getSharedCase().getLawyer().getName();
            }
            if (link.getOwner() != null) {
                link.getOwner().getId();
                link.getOwner().getName();
                link.getOwner().getEmail();
            }
        } catch (Exception ignore) {}
        return link;
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
     * Initialise les proxies lazy pour usage hors transaction.
     */
    @Transactional
    public CaseShareLink accessByToken(String token) {
        CaseShareLink link = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien de partage introuvable"));

        if (!link.isValid()) {
            throw new RuntimeException("Ce lien de partage est expiré ou révoqué");
        }

        link.setAccessCount(link.getAccessCount() + 1);
        link = shareLinkRepository.save(link);

        // Initialiser les proxies lazy (OSIV=false)
        if (link.getSharedCase() != null) {
            link.getSharedCase().getName();
            if (link.getSharedCase().getClient() != null) link.getSharedCase().getClient().getName();
            if (link.getSharedCase().getLawyer() != null) link.getSharedCase().getLawyer().getName();
        }
        if (link.getOwner() != null) {
            link.getOwner().getFirstName();
            link.getOwner().getLastName();
        }
        if (link.getRecipientEmail() != null) {
            // already a plain field, no lazy init needed
        }

        return link;
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
            // If an email recipient was provided, this is an invitation to join as a collaborator
            // -> point them to the collaborator accept-invitation page so they can create an account
            String link;
            if (to != null && !to.isBlank()) {
                link = baseUrl + "/collaborators/accept-invitation?token=" + token;
            } else {
                // otherwise, send the public shared-case view
                link = baseUrl + "/cases/shared?token=" + token;
            }

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