package com.gedavocat.controller;

import com.gedavocat.model.CaseShareLink;
import com.gedavocat.model.Permission;
import com.gedavocat.model.User;
import com.gedavocat.repository.PermissionRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseShareService;
import com.gedavocat.service.CollaboratorInvitationService;
import com.gedavocat.util.PasswordValidator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/collaborators")
@RequiredArgsConstructor
public class CollaboratorInvitationController {

    private static final Logger log = LoggerFactory.getLogger(CollaboratorInvitationController.class);

    private final CaseShareService caseShareService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    private final CollaboratorInvitationService collaboratorInvitationService;

    @GetMapping("/accept-invitation")
    public String acceptInvitationForm(@RequestParam(required = false) String token, Model model) {
        // If no token provided, redirect collaborators to their portal — prevents MissingServletRequestParameterException
        if (token == null || token.isBlank()) {
            return "redirect:/my-cases-collab";
        }
        // Use the collaborator invitation service (in-memory map + DB fallback) like clients
        Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
        CaseShareLink link = null;
        if (entry.isEmpty()) {
            // fallback: try to load link directly from DB and check validity
            try {
                link = caseShareService.getLinkByToken(token);
                if (link == null || !link.isValid()) {
                    model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
                    return "clients/invitation-expired";
                }
                model.addAttribute("token", token);
                model.addAttribute("email", link.getRecipientEmail());
                model.addAttribute("caseName", link.getSharedCase() != null ? link.getSharedCase().getName() : null);
                return "collaborators/accept-invitation";
            } catch (Exception e) {
                model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
                return "clients/invitation-expired";
            }
        }
        // fetch link for case details (shared case name)
        link = caseShareService.getLinkByToken(token);
        model.addAttribute("token", token);
        model.addAttribute("email", entry.get().email());
        model.addAttribute("caseName", link.getSharedCase() != null ? link.getSharedCase().getName() : null);
        return "collaborators/accept-invitation";
    }

    @PostMapping("/accept-invitation")
    public String processAcceptInvitation(@RequestParam String token,
                                           @RequestParam String password,
                                           @RequestParam String confirmPassword,
                                           @RequestParam(required = false) String email,
                                           @RequestParam(required = false) String firstName,
                                           @RequestParam(required = false) String lastName,
                                           @RequestParam(required = false) String barNumber,
                                           RedirectAttributes redirectAttributes,
                                           Model model) {
        if (!password.equals(confirmPassword)) {
            // Resolve email to re-display (prefer in-memory entry, fallback DB); preserve provided names
            String resolvedEmail = null;
            try {
                Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
                if (entry.isPresent()) resolvedEmail = entry.get().email();
                else {
                    try {
                        CaseShareLink link = caseShareService.getLinkByToken(token);
                        if (link != null) resolvedEmail = link.getRecipientEmail();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignore) {}

            model.addAttribute("token", token);
            model.addAttribute("email", resolvedEmail != null ? resolvedEmail : email);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("error", "Les mots de passe ne correspondent pas.");
            return "collaborators/accept-invitation";
        }
        // SEC FIX H-08 : validation stricte du mot de passe avec PasswordValidator
        if (!PasswordValidator.isValid(password)) {
            // Preserve email and names as above
            String resolvedEmail = null;
            try {
                Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
                if (entry.isPresent()) resolvedEmail = entry.get().email();
                else {
                    try {
                        CaseShareLink link = caseShareService.getLinkByToken(token);
                        if (link != null) resolvedEmail = link.getRecipientEmail();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignore) {}

            model.addAttribute("token", token);
            model.addAttribute("email", resolvedEmail != null ? resolvedEmail : email);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("error", PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE);
            return "collaborators/accept-invitation";
        }

        try {
            // Validate token using collaboratorInvitationService (in-memory + db fallback)
            Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
            String resolvedEmail = null;
            CaseShareLink link = null;
            if (entry.isEmpty()) {
                // fallback: try db
                try {
                    link = caseShareService.getLinkByToken(token);
                    if (link == null || !link.isValid()) {
                        model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
                        return "clients/invitation-expired";
                    }
                    resolvedEmail = link.getRecipientEmail();
                } catch (Exception e) {
                    model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
                    return "clients/invitation-expired";
                }
            } else {
                resolvedEmail = entry.get().email();
            }

            // SEC-IDOR FIX : ne PAS permettre l'override de l'email par le formulaire
            // L'email doit provenir exclusivement du token d'invitation

            if (resolvedEmail == null || resolvedEmail.isBlank()) {
                model.addAttribute("token", token);
                model.addAttribute("error", "Email destinataire non présent sur le lien d'invitation.");
                return "collaborators/accept-invitation";
            }
            if (userRepository.findByEmail(resolvedEmail).isPresent()) {
                redirectAttributes.addFlashAttribute("message", "Un compte existe déjà pour cet email. Connectez-vous.");
                return "redirect:/login";
            }

            User user = new User();
            user.setId(java.util.UUID.randomUUID().toString());
            user.setEmail(resolvedEmail);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(User.UserRole.LAWYER_SECONDARY);
            user.setEmailVerified(true);
            user.setAccountEnabled(true);

            // set bar number if provided
            if (barNumber != null && !barNumber.trim().isEmpty()) {
                user.setBarNumber(barNumber.trim());
            }

            // set names: prefer provided firstName/lastName, else derive from email local-part
            String fn = (firstName != null ? firstName.trim() : "");
            String ln = (lastName != null ? lastName.trim() : "");
            if (fn.isEmpty() && ln.isEmpty()) {
                String local = resolvedEmail.split("@")[0];
                fn = local;
                ln = "";
            }
            user.setFirstName(fn);
            user.setLastName(ln);
            String fullName = (fn + " " + ln).trim();
            if (fullName.isEmpty()) fullName = resolvedEmail;
            user.setName(fullName);

            User saved = userRepository.save(user);

            // grant permission to the shared case
            if (link == null) {
                link = caseShareService.getLinkByToken(token);
            }
            Permission p = new Permission();
            p.setCaseEntity(link.getSharedCase());
            p.setLawyer(saved);
            p.setGrantedBy(link.getOwner());
            p.setCanRead(true);
            p.setCanWrite(false);
            p.setCanUpload(false);
            p.setIsActive(true);
            // Ne PAS copier la date d'expiration du lien de partage vers la permission.
            // La permission du collaborateur doit rester active indéfiniment (ou jusqu'à révocation manuelle).
            // L'expiration du lien de partage ne concerne que l'invitation, pas l'accès au dossier.
            p.setExpiresAt(null);
            Permission savedPerm = permissionRepository.save(p);
            // Diagnostic logging: ensure permission persisted and IDs recorded
            try {
                log.info("[Invite] Permission saved id={} caseId={} lawyerId={} grantedById={}",
                        savedPerm.getId(),
                        savedPerm.getCaseEntity() != null ? savedPerm.getCaseEntity().getId() : null,
                        savedPerm.getLawyer() != null ? savedPerm.getLawyer().getId() : null,
                        savedPerm.getGrantedBy() != null ? savedPerm.getGrantedBy().getId() : null);
            } catch (Exception e) {
                log.warn("[Invite] Permission saved but failed to log fields: {}", e.getMessage());
            }

            // Optionally revoke the link to prevent reuse
            try {
                // persist revocation and remove in-memory token
                caseShareService.revokeByToken(token);
                collaboratorInvitationService.removeToken(token);
            } catch (Exception ignored) {}

            redirectAttributes.addFlashAttribute("message", "Compte collaborateur créé avec succès ! Connectez-vous.");
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Erreur : " + e.getMessage());
            return "collaborators/accept-invitation";
        }
    }

    /**
     * SEC FIX : endpoint supprimé — fuite d'information (emails, tokens) sans authentification
     */
    @GetMapping("/invitation-info")
    @ResponseBody
    public ResponseEntity<?> invitationInfo(@RequestParam String token) {
        return ResponseEntity.status(404).body(Map.of("error", "Endpoint désactivé"));
    }

}