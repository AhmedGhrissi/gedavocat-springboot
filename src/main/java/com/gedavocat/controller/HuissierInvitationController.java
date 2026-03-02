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

/**
 * Contrôleur d'invitation pour les huissiers.
 * Similaire à CollaboratorInvitationController mais crée des utilisateurs avec le rôle HUISSIER.
 */
@Controller
@RequestMapping("/huissiers")
@RequiredArgsConstructor
public class HuissierInvitationController {

    private static final Logger log = LoggerFactory.getLogger(HuissierInvitationController.class);

    private final CaseShareService caseShareService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    private final CollaboratorInvitationService collaboratorInvitationService;

    @GetMapping("/accept-invitation")
    public String acceptInvitationForm(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            return "redirect:/my-cases-huissier";
        }

        Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
        CaseShareLink link = null;
        if (entry.isEmpty()) {
            try {
                link = caseShareService.getLinkByToken(token);
                if (link == null || !link.isValid()) {
                    model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
                    return "clients/invitation-expired";
                }
                model.addAttribute("token", token);
                model.addAttribute("email", link.getRecipientEmail());
                model.addAttribute("caseName", link.getSharedCase() != null ? link.getSharedCase().getName() : null);
                return "huissier-portal/accept-invitation";
            } catch (Exception e) {
                model.addAttribute("error", "Ce lien d'invitation est invalide ou a expiré.");
                return "clients/invitation-expired";
            }
        }

        link = caseShareService.getLinkByToken(token);
        model.addAttribute("token", token);
        model.addAttribute("email", entry.get().email());
        model.addAttribute("caseName", link.getSharedCase() != null ? link.getSharedCase().getName() : null);
        return "huissier-portal/accept-invitation";
    }

    @PostMapping("/accept-invitation")
    public String processAcceptInvitation(@RequestParam String token,
                                           @RequestParam String password,
                                           @RequestParam String confirmPassword,
                                           @RequestParam(required = false) String email,
                                           @RequestParam(required = false) String firstName,
                                           @RequestParam(required = false) String lastName,
                                           @RequestParam(required = false) String officeNumber,
                                           RedirectAttributes redirectAttributes,
                                           Model model) {
        if (!password.equals(confirmPassword)) {
            String resolvedEmail = resolveEmail(token, email);
            model.addAttribute("token", token);
            model.addAttribute("email", resolvedEmail);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("error", "Les mots de passe ne correspondent pas.");
            return "huissier-portal/accept-invitation";
        }
        if (!PasswordValidator.isValid(password)) {
            String resolvedEmail = resolveEmail(token, email);
            model.addAttribute("token", token);
            model.addAttribute("email", resolvedEmail);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("error", PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE);
            return "huissier-portal/accept-invitation";
        }

        try {
            Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
            String resolvedEmail = null;
            CaseShareLink link = null;
            if (entry.isEmpty()) {
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

            if (resolvedEmail == null || resolvedEmail.isBlank()) {
                model.addAttribute("token", token);
                model.addAttribute("error", "Email destinataire non présent sur le lien d'invitation.");
                return "huissier-portal/accept-invitation";
            }
            if (userRepository.findByEmail(resolvedEmail).isPresent()) {
                redirectAttributes.addFlashAttribute("message", "Un compte existe déjà pour cet email. Connectez-vous.");
                return "redirect:/login";
            }

            User user = new User();
            user.setId(java.util.UUID.randomUUID().toString());
            user.setEmail(resolvedEmail);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(User.UserRole.HUISSIER);
            user.setEmailVerified(true);
            user.setAccountEnabled(true);

            if (officeNumber != null && !officeNumber.trim().isEmpty()) {
                user.setBarNumber(officeNumber.trim());
            }

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

            // Grant permission to the shared case
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
            p.setExpiresAt(null);
            Permission savedPerm = permissionRepository.save(p);

            try {
                log.info("[HuissierInvite] Permission saved id={} caseId={} huissierId={} grantedById={}",
                        savedPerm.getId(),
                        savedPerm.getCaseEntity() != null ? savedPerm.getCaseEntity().getId() : null,
                        savedPerm.getLawyer() != null ? savedPerm.getLawyer().getId() : null,
                        savedPerm.getGrantedBy() != null ? savedPerm.getGrantedBy().getId() : null);
            } catch (Exception e) {
                log.warn("[HuissierInvite] Permission saved but failed to log fields: {}", e.getMessage());
            }

            try {
                caseShareService.revokeByToken(token);
                collaboratorInvitationService.removeToken(token);
            } catch (Exception ignored) {}

            redirectAttributes.addFlashAttribute("message", "Compte huissier créé avec succès ! Connectez-vous.");
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Erreur : " + e.getMessage());
            return "huissier-portal/accept-invitation";
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

    private String resolveEmail(String token, String fallbackEmail) {
        try {
            Optional<CollaboratorInvitationService.InvitationEntry> entry = collaboratorInvitationService.validateToken(token);
            if (entry.isPresent()) return entry.get().email();
            CaseShareLink link = caseShareService.getLinkByToken(token);
            if (link != null) return link.getRecipientEmail();
        } catch (Exception ignore) {}
        return fallbackEmail;
    }
}
