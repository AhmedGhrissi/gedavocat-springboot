package com.gedavocat.controller;

import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.ClientInvitationService;
import com.gedavocat.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * API REST pour la création rapide de clients (utilisée par la modale dans le formulaire de dossier).
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'LAWYER_SECONDARY', 'AVOCAT_ADMIN')")
public class ClientApiController {

    private final ClientService clientService;
    private final UserRepository userRepository;
    private final ClientInvitationService invitationService;

    /**
     * Création rapide d'un client depuis la modale.
     * Retourne {id, name, email} en JSON.
     */
    @PostMapping("/quick-create")
    public ResponseEntity<Map<String, Object>> quickCreate(
            @RequestBody QuickCreateRequest req,
            Authentication authentication
    ) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        Client client = new Client();
        client.setFirstName(req.firstName());
        client.setLastName(req.lastName());
        client.setEmail(req.email());
        client.setClientType(req.clientType() != null ? req.clientType() : Client.ClientType.INDIVIDUAL);
        if (req.phone() != null && !req.phone().isBlank()) {
            client.setPhone(req.phone());
        }

        Client saved = clientService.createClient(client, user.getId());

        boolean emailFailed = false;
        if (req.sendInvitation()) {
            try {
                String lawyerFullName = user.getFirstName() + " " + user.getLastName();
                invitationService.sendInvitation(saved, lawyerFullName);
            } catch (Exception e) {
                log.warn("[ClientApiController] Email non envoyé pour {} : {}", saved.getEmail(), e.getMessage());
                emailFailed = true;
            }
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", saved.getId());
        body.put("name", saved.getName());
        body.put("email", saved.getEmail());
        if (emailFailed) {
            body.put("emailWarning", true);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    public record QuickCreateRequest(
            String firstName,
            String lastName,
            String email,
            Client.ClientType clientType,
            String phone,
            boolean sendInvitation
    ) {}
}
