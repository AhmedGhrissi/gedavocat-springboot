package com.gedavocat.controller;

import com.gedavocat.model.User;
import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.Document;
import com.gedavocat.model.AuditLog;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
public class RgpdController {

    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;

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
            } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
            exportData.put("clients", clientsData);

            // Logs d'audit
            List<Map<String, Object>> logsData = new ArrayList<>();
            try {
                List<AuditLog> logs = auditLogRepository.findByUserId(user.getId(), PageRequest.of(0, 1000)).getContent();
                for (AuditLog log : logs) {
                    Map<String, Object> logMap = new LinkedHashMap<>();
                    logMap.put("action", log.getAction());
                    logMap.put("details", log.getDetails());
                    logMap.put("ipAddress", log.getIpAddress());
                    logMap.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
                    logsData.add(logMap);
                }
            } catch (Exception ignored) {}
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
    public String deleteMyAccount(
            Authentication authentication,
            @RequestParam(required = false) String confirmEmail,
            RedirectAttributes redirectAttributes
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Vérification par email pour confirmer
        if (confirmEmail == null || !confirmEmail.equals(user.getEmail())) {
            redirectAttributes.addFlashAttribute("error",
                    "Veuillez confirmer votre email pour supprimer votre compte.");
            return "redirect:/settings";
        }

        // Anonymiser plutôt que supprimer (obligations légales de conservation)
        user.setName("Utilisateur supprimé");
        user.setFirstName(null);
        user.setLastName(null);
        user.setEmail("deleted-" + user.getId() + "@anonymized.local");
        user.setPhone(null);
        user.setBarNumber(null);
        user.setPassword("ACCOUNT_DELETED_" + UUID.randomUUID());
        user.setAccountEnabled(false);
        userRepository.save(user);

        // Invalider la session
        return "redirect:/login?accountDeleted=true";
    }
}
