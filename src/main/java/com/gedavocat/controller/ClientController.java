package com.gedavocat.controller;

import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.ClientInvitationService;
import com.gedavocat.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

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
     * Supprimer un client
     */
    @PostMapping("/{id}/delete")
    public String deleteClient(
            @PathVariable String id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = getCurrentUser(authentication);
            clientService.deleteClient(id, user.getId());
            redirectAttributes.addFlashAttribute("message", "Client supprimé avec succès");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la suppression du client");
        }
        return "redirect:/clients";
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
