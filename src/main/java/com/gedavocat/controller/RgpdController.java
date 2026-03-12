package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.AuditLog;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.AuditLogRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.service.StripeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Contrôleur RGPD — Droits des personnes (Articles 15-20 RGPD)
 * - Droit d'accès (Art. 15)
 * - Droit à la portabilité (Art. 20) : export JSON
 * - Droit à l'effacement (Art. 17) : suppression du compte
 */
@Controller
@RequestMapping("/rgpd")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class RgpdController {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final AuditLogRepository auditLogRepository;
    private final DocumentRepository documentRepository;
    private final StripeService stripeService;

    /**
     * Art. 20 RGPD — Export de toutes les données personnelles au format JSON
     */
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<byte[]> exportMyData(Authentication authentication) {
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

            Map<String, Object> exportData = new LinkedHashMap<>();
            exportData.put("export_date", LocalDateTime.now().toString());
            exportData.put("export_type", "RGPD Art. 20 - Droit à la portabilité");

            // Données utilisateur (sans mot de passe)
            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("firstName", user.getFirstName());
            userData.put("lastName", user.getLastName());
            userData.put("email", user.getEmail());
            userData.put("phone", user.getPhone());
            userData.put("role", user.getRole() != null ? user.getRole().name() : null);
            userData.put("barNumber", user.getBarNumber());
            userData.put("subscriptionPlan", user.getSubscriptionPlan() != null ? user.getSubscriptionPlan().name() : null);
            userData.put("subscriptionStatus", user.getSubscriptionStatus() != null ? user.getSubscriptionStatus().name() : null);
            userData.put("gdprConsentAt", user.getGdprConsentAt() != null ? user.getGdprConsentAt().toString() : null);
            userData.put("termsAcceptedAt", user.getTermsAcceptedAt() != null ? user.getTermsAcceptedAt().toString() : null);
            userData.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            exportData.put("user", userData);

            // Dossiers
            List<Map<String, Object>> casesData = new ArrayList<>();
            try {
                List<Case> cases = caseRepository.findByLawyerId(user.getId());
                for (Case c : cases) {
                    Map<String, Object> caseMap = new LinkedHashMap<>();
                    caseMap.put("id", c.getId());
                    caseMap.put("name", c.getName());
                    caseMap.put("description", c.getDescription());
                    caseMap.put("status", c.getStatus() != null ? c.getStatus().name() : null);
                    caseMap.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                    casesData.add(caseMap);
                }
            } catch (Exception e) {
                log.warn("Export RGPD : erreur lors de la récupération des dossiers pour {}", user.getEmail(), e);
                exportData.put("cases_warning", "Export incomplet : erreur lors de la récupération des dossiers");
            }
            exportData.put("cases", casesData);

            // Clients (si avocat)
            List<Map<String, Object>> clientsData = new ArrayList<>();
            try {
                List<Client> clients = clientRepository.findByLawyerId(user.getId());
                for (Client cl : clients) {
                    Map<String, Object> clientMap = new LinkedHashMap<>();
                    clientMap.put("id", cl.getId());
                    clientMap.put("name", cl.getName());
                    clientMap.put("email", cl.getEmail());
                    clientMap.put("phone", cl.getPhone());
                    clientMap.put("createdAt", cl.getCreatedAt() != null ? cl.getCreatedAt().toString() : null);
                    clientsData.add(clientMap);
                }
            } catch (Exception e) {
                log.warn("Export RGPD : erreur lors de la récupération des clients pour {}", user.getEmail(), e);
                exportData.put("clients_warning", "Export incomplet : erreur lors de la récupération des clients");
            }
            exportData.put("clients", clientsData);

            // Logs d'audit
            List<Map<String, Object>> logsData = new ArrayList<>();
            try {
                List<AuditLog> logs = auditLogRepository.findByUserId(user.getId(), PageRequest.of(0, 1000)).getContent();
                for (AuditLog log : logs) {
                    Map<String, Object> logMap = new LinkedHashMap<>();
                    logMap.put("action", log.getAction());
                    logMap.put("details", log.getDetails());
                    logMap.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
                    logsData.add(logMap);
                }
            } catch (Exception e) {
                log.warn("Export RGPD : erreur lors de la récupération des logs d'audit pour {}", user.getEmail(), e);
                exportData.put("auditLogs_warning", "Export incomplet : erreur lors de la récupération des logs d'audit");
            }
            exportData.put("auditLogs", logsData);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            byte[] jsonBytes = mapper.writeValueAsBytes(exportData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gedavocat-export-rgpd.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBytes);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Art. 17 RGPD — Demande de suppression du compte
     */
    @PostMapping("/delete-account")
    @PreAuthorize("isAuthenticated()")
    @org.springframework.transaction.annotation.Transactional
    public String deleteMyAccount(
            Authentication authentication,
            @RequestParam(required = false) String confirmEmail,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Vérification par email pour confirmer
        if (confirmEmail == null || !confirmEmail.equals(user.getEmail())) {
            redirectAttributes.addFlashAttribute("error",
                    "Veuillez confirmer votre email pour supprimer votre compte.");
            return "redirect:/settings";
        }

        // SEC-02 FIX : annuler l'abonnement Stripe avant anonymisation
        try {
            if (user.getStripeSubscriptionId() != null && !user.getStripeSubscriptionId().isBlank()) {
                stripeService.cancelSubscription(user.getStripeSubscriptionId());
                log.info("Abonnement Stripe annulé pour suppression RGPD de {}", user.getEmail());
            } else if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
                stripeService.cancelSubscriptionAtPeriodEnd(user.getStripeCustomerId());
            }
        } catch (Exception stripeEx) {
            log.warn("Impossible d'annuler l'abonnement Stripe lors de la suppression RGPD: {}", stripeEx.getMessage());
        }

        // SEC-02 FIX : anonymiser les logs d'audit
        try {
            List<AuditLog> logs = auditLogRepository.findByUserId(user.getId(), PageRequest.of(0, 10000)).getContent();
            for (AuditLog auditLog : logs) {
                auditLog.setIpAddress("0.0.0.0");
                auditLog.setDetails("ANONYMIZED_RGPD");
            }
            auditLogRepository.saveAll(logs);
        } catch (Exception e) {
            log.warn("Erreur anonymisation audit logs RGPD: {}", e.getMessage());
        }

        // SEC-02 FIX : anonymiser les clients associés
        try {
            List<Client> clients = clientRepository.findByLawyerId(user.getId());
            for (Client client : clients) {
                client.setName("Client anonymisé");
                client.setEmail("anonymized-" + client.getId() + "@deleted.local");
                client.setPhone(null);
                client.setAddress(null);
            }
            clientRepository.saveAll(clients);
        } catch (Exception e) {
            log.warn("Erreur anonymisation clients RGPD: {}", e.getMessage());
        }

        // SEC-02 FIX : anonymiser les documents (métadonnées)
        try {
            List<Case> cases = caseRepository.findByLawyerId(user.getId());
            for (Case c : cases) {
                // SEC FIX F-08 : anonymiser aussi le nom et la description du dossier
                c.setName("Dossier supprimé");
                c.setDescription(null);
                var docs = documentRepository.findByCaseIdAndNotDeleted(c.getId());
                for (var doc : docs) {
                    doc.setOriginalName("deleted_document");
                }
                documentRepository.saveAll(docs);
            }
            caseRepository.saveAll(cases);
        } catch (Exception e) {
            log.warn("Erreur anonymisation documents RGPD: {}", e.getMessage());
        }

        // Anonymiser l'utilisateur (obligations légales de conservation)
        user.setName("Utilisateur supprimé");
        user.setFirstName(null);
        user.setLastName(null);
        user.setEmail("deleted-" + user.getId() + "@anonymized.local");
        user.setPhone(null);
        user.setBarNumber(null);
        user.setPassword("ACCOUNT_DELETED_" + UUID.randomUUID());
        user.setAccountEnabled(false);
        userRepository.save(user);

        // SEC-RGPD FIX : invalider la session HTTP immédiatement après suppression
        try {
            request.getSession().invalidate();
        } catch (Exception ignored) {}
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        return "redirect:/login?accountDeleted=true";
    }
}
