package com.gedavocat.controller;

import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.ClientArchiveToken;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.CaseService;
import com.gedavocat.service.ClientArchiveService;
import com.gedavocat.service.ClientInvitationService;
import com.gedavocat.service.ClientService;
import com.gedavocat.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Contrôleur de gestion des clients
 */
@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'AVOCAT_ADMIN')")
public class ClientController {

    private final ClientService clientService;
    private final UserRepository userRepository;
    private final ClientInvitationService invitationService;
    private final CaseService caseService;
    private final DocumentService documentService;
    private final ClientArchiveService clientArchiveService;

    /**
     * SEC-MASS-ASSIGN FIX CTL-01 : restreindre les champs bindables
     * Empêche la manipulation des champs sensibles (lawyer, clientUser, cases, invoices, etc.)
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("firstName", "lastName", "name", "email", "phone", "address",
                "clientType", "companyName", "siret");
    }

    /**
     * Liste des clients
     */
    @GetMapping
    public String listClients(
            @RequestParam(required = false) String search,
            Model model,
            Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        List<Client> clients;
        if (search != null && !search.isEmpty()) {
            clients = clientService.searchClients(user.getId(), search);
        } else {
            clients = clientService.getClientsByLawyer(user.getId());
        }

        // Force-initialiser les cases pour éviter LazyInitializationException
        for (Client client : clients) {
            if (client.getCases() != null) {
                client.getCases().size();
            }
        }

        model.addAttribute("clients", clients);
        model.addAttribute("search", search);
        model.addAttribute("totalClients", clients.size());

        return "clients/list";
    }

    /**
     * Formulaire de création
     */
    @GetMapping("/new")
    public String newClientForm(Model model) {
        model.addAttribute("client", new Client());
        return "clients/form";
    }

    /**
     * Créer un client
     */
    @PostMapping
    public String createClient(
            @Valid @ModelAttribute Client client,
            BindingResult result,
            @RequestParam(value = "sendInvitation", required = false, defaultValue = "true") boolean sendInvitation,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (result.hasErrors()) {
            model.addAttribute("client", client);
            return "clients/form";
        }

        try {
            User user = getCurrentUser(authentication);

            Client savedClient = clientService.createClient(client, user.getId());

            if (sendInvitation) {
                String lawyerFullName = user.getFirstName() + " " + user.getLastName();
                try {
                    invitationService.sendInvitation(savedClient, lawyerFullName);
                    redirectAttributes.addFlashAttribute("message", "Client créé avec succès. Une invitation a été envoyée à " + savedClient.getEmail() + ".");
                } catch (Exception emailEx) {
                    log.warn("[ClientController] Client créé mais email non envoyé pour {} : {}", savedClient.getEmail(), emailEx.getMessage());
                    redirectAttributes.addFlashAttribute("warning", "Client créé, mais l'envoi de l'email d'invitation a échoué. Vérifiez la configuration SMTP.");
                }
            } else {
                redirectAttributes.addFlashAttribute("message", "Client créé avec succès");
            }
            return "redirect:/clients";
        } catch (Exception e) {
            model.addAttribute("client", client);
            model.addAttribute("error", "Erreur lors de la création du client");
            return "clients/form";
        }
    }

    /**
     * Détails d'un client
     */
    @GetMapping("/{id}")
    public String viewClient(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Client client = clientService.getClientById(id);

        // Vérifier l'accès
        if (client.getLawyer() == null || !client.getLawyer().getId().equals(user.getId())) {
            throw new AccessDeniedException("Accès non autorisé");
        }

        model.addAttribute("client", client);
        return "clients/view";
    }

    /**
     * Formulaire d'édition
     */
    @GetMapping("/{id}/edit")
    public String editClientForm(@PathVariable String id, Model model, Authentication authentication) {
        User user = getCurrentUser(authentication);
        Client client = clientService.getClientById(id);

        if (client.getLawyer() == null || !client.getLawyer().getId().equals(user.getId())) {
            throw new AccessDeniedException("Accès non autorisé");
        }

        model.addAttribute("client", client);
        return "clients/form";
    }

    /**
     * Mettre à jour un client
     */
    @PostMapping("/{id}")
    public String updateClient(
            @PathVariable String id,
            @Valid @ModelAttribute Client client,
            BindingResult result,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (result.hasErrors()) {
            model.addAttribute("client", client);
            return "clients/form";
        }

        try {
            User user = getCurrentUser(authentication);
            clientService.updateClient(id, client, user.getId());
            
            redirectAttributes.addFlashAttribute("message", "Client modifié avec succès");
            return "redirect:/clients/" + id;
        } catch (Exception e) {
            model.addAttribute("client", client);
            model.addAttribute("error", "Erreur lors de la mise à jour du client");
            return "clients/form";
        }
    }

    /**
     * Supprimer un client.
     * Requiert : (1) aucun dossier rattaché, (2) param confirmed=true (checkbox avocat).
     * Génère une archive légale et envoie un email aux deux parties avant la suppression.
     */
    @PostMapping("/{id}/delete")
    public String deleteClient(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "false") boolean confirmed,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (!confirmed) {
            redirectAttributes.addFlashAttribute("error",
                "Vous devez cocher la case de confirmation pour supprimer ce client.");
            return "redirect:/clients/" + id;
        }
        try {
            User user = getCurrentUser(authentication);
            Client client = clientService.getClientById(id, user.getId());
            // Générer l'archive MinIO + envoyer les emails AVANT la suppression
            clientArchiveService.archiveBeforeDeletion(client, user);
            clientService.deleteClient(id, user.getId());
            redirectAttributes.addFlashAttribute("message",
                "Client supprimé. Un email de confirmation avec le lien d'archive a été envoyé.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/clients/" + id;
        } catch (Exception e) {
            log.error("[ClientController] Erreur suppression client {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression du client");
            return "redirect:/clients/" + id;
        }
        return "redirect:/clients";
    }

    /**
     * Téléchargement de l'archive légale via token (accès public — client sans compte possible).
     * Le token est envoyé par email lors de la suppression du client.
     */
    @GetMapping("/archive/{token}/download")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Resource> downloadClientArchive(@PathVariable String token) {
        try {
            ClientArchiveToken tokenInfo = clientArchiveService.findValidToken(token);
            byte[] bytes = clientArchiveService.downloadArchive(token);
            String clientName = tokenInfo.getClientName() != null ? tokenInfo.getClientName() : "client";
            String zipName = "Archive_" + sanitizeEntry(clientName) + ".zip";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                    .body(new ByteArrayResource(bytes));
        } catch (RuntimeException e) {
            log.warn("[ClientController] Téléchargement archive invalide ou expirée : {}", e.getMessage());
            return ResponseEntity.status(410).build(); // 410 Gone
        } catch (Exception e) {
            log.error("[ClientController] Erreur téléchargement archive", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export ZIP de tous les documents des dossiers d'un client (pour l'avocat)
     */
    @GetMapping("/{id}/export-zip")
    public ResponseEntity<Resource> exportClientZip(
            @PathVariable String id,
            Authentication authentication
    ) {
        try {
            User user = getCurrentUser(authentication);
            Client client = clientService.getClientById(id, user.getId());

            List<Case> cases = caseService.getCasesByClient(id);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int docCount = 0;

            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (Case caseEntity : cases) {
                    List<Document> documents = documentService.getLatestVersions(caseEntity.getId());
                    String folderName = sanitizeEntry(caseEntity.getName() != null ? caseEntity.getName() : caseEntity.getId());

                    for (Document doc : documents) {
                        try {
                            byte[] fileBytes = documentService.downloadDocument(doc.getId(), user.getId());
                            String filename = sanitizeEntry(doc.getOriginalName() != null ? doc.getOriginalName() : doc.getId());
                            ZipEntry entry = new ZipEntry(folderName + "/" + filename);
                            zos.putNextEntry(entry);
                            zos.write(fileBytes);
                            zos.closeEntry();
                            docCount++;
                        } catch (Exception docEx) {
                            log.warn("Impossible d'exporter le document {} du dossier {}: {}", doc.getId(), caseEntity.getId(), docEx.getMessage());
                        }
                    }
                }
            }

            if (docCount == 0) {
                return ResponseEntity.noContent().build();
            }

            String clientName = client.getName() != null ? client.getName() : id;
            String zipName = "Client_" + sanitizeEntry(clientName) + ".zip";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                    .body(new ByteArrayResource(baos.toByteArray()));

        } catch (AccessDeniedException | SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("Erreur export ZIP client {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Sécurise un nom d'entrée ZIP contre le path traversal et les caractères invalides. */
    private String sanitizeEntry(String name) {
        if (name == null || name.isBlank()) return "inconnu";
        String sanitized = name.replace("\\", "/");
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) sanitized = sanitized.substring(lastSlash + 1);
        sanitized = sanitized.replaceAll("[\\r\\n\"<>|?*:/]", "_");
        return sanitized.isBlank() ? "inconnu" : sanitized;
    }

    /**
     * Récupère l'utilisateur connecté
     */
    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    // ===================================================================
    // Gestion des invitations clients (accès public)
    // ===================================================================

}
