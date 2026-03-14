package com.gedavocat.service;

import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Service d'invitation des clients au portail GedAvocat.
 * Génère un token d'invitation, le stocke et envoie un email avec
 * un lien permettant au client de créer son mot de passe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ClientInvitationService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@docavocat.fr}")
    private String fromEmail;

    @Value("${app.base-url:https://docavocat.fr}")
    private String baseUrl;

    /** Map token → InvitationEntry */
    private final Map<String, InvitationEntry> pendingInvitations = new ConcurrentHashMap<>();

    private static final int TOKEN_EXPIRY_HOURS = 72; // 3 jours

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Génère un token d'invitation pour un client et envoie l'email.
     */
    public void sendInvitation(Client client, String lawyerFullName) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);
        pendingInvitations.put(token, new InvitationEntry(
            client.getId(),
            client.getEmail(),
            expiry
        ));

        // Stocker le token et la date d'envoi dans le client (pour référence et statut)
        client.setInvitationId(token);
        client.setInvitedAt(LocalDateTime.now());
        clientRepository.save(client);

        sendInvitationEmail(client.getEmail(), client.getName(), lawyerFullName, token);
        log.info("[ClientInvitation] Invitation envoyée (email/token masqués pour RGPD)");
    }

    /**
     * Vérifie si un token est valide.
     * Cherche d'abord en mémoire, puis en base (après redémarrage serveur).
     */
    public Optional<InvitationEntry> validateToken(String token) {
        // 1. Cherche en mémoire
        InvitationEntry entry = pendingInvitations.get(token);
        if (entry != null) {
            if (LocalDateTime.now().isAfter(entry.expiry())) {
                pendingInvitations.remove(token);
                return Optional.empty();
            }
            return Optional.of(entry);
        }
        // 2. Fallback DB après redémarrage
        return clientRepository.findByInvitationId(token)
            .filter(c -> c.getInvitedAt() != null
                      && LocalDateTime.now().isBefore(c.getInvitedAt().plusHours(TOKEN_EXPIRY_HOURS))
                      && c.getClientUser() == null)
            .map(c -> {
                InvitationEntry rebuilt = new InvitationEntry(
                    c.getId(), c.getEmail(), c.getInvitedAt().plusHours(TOKEN_EXPIRY_HOURS));
                pendingInvitations.put(token, rebuilt); // restaurer en mémoire
                return rebuilt;
            });
    }

    /**
     * Crée le compte utilisateur client et lie les deux entités.
     */
    @Transactional
    public User acceptInvitation(String token, String password) {
        // 1. Cherche en mémoire
        InvitationEntry entry = pendingInvitations.get(token);

        // 2. Fallback DB si absent (après redémarrage)
        if (entry == null) {
            Client dbClient = clientRepository.findByInvitationId(token)
                .orElseThrow(() -> new RuntimeException("Token d'invitation invalide ou expiré"));
            if (dbClient.getInvitedAt() == null
                    || LocalDateTime.now().isAfter(dbClient.getInvitedAt().plusHours(TOKEN_EXPIRY_HOURS))) {
                throw new RuntimeException("Token d'invitation expiré");
            }
            if (dbClient.getClientUser() != null) {
                throw new RuntimeException("Un compte existe déjà pour cet email");
            }
            entry = new InvitationEntry(dbClient.getId(), dbClient.getEmail(),
                dbClient.getInvitedAt().plusHours(TOKEN_EXPIRY_HOURS));
            pendingInvitations.put(token, entry);
        } else if (LocalDateTime.now().isAfter(entry.expiry())) {
            pendingInvitations.remove(token);
            throw new RuntimeException("Token d'invitation expiré");
        }

        Client client = clientRepository.findById(entry.clientId())
            .orElseThrow(() -> new RuntimeException("Client non trouvé"));

        // Vérifier qu'un compte n'existe pas déjà
        if (userRepository.findByEmail(client.getEmail()).isPresent()) {
            throw new RuntimeException("Un compte existe déjà avec cet email");
        }

        // Créer le compte utilisateur
        User clientUser = new User();
        clientUser.setId(java.util.UUID.randomUUID().toString());
        clientUser.setEmail(client.getEmail());
        clientUser.setPassword(passwordEncoder.encode(password));
        // Extraire prénom/nom depuis client.name (best effort)
        String[] parts = client.getName().trim().split("\\s+", 2);
        clientUser.setFirstName(parts[0]);
        clientUser.setLastName(parts.length > 1 ? parts[1] : "");
        clientUser.setName(client.getName());
        clientUser.setRole(User.UserRole.CLIENT);
        clientUser.setEmailVerified(true);
        clientUser.setAccountEnabled(true);

        User savedUser = userRepository.save(clientUser);

        // Lier le client à son compte
        client.setClientUser(savedUser);
        clientRepository.save(client);

        pendingInvitations.remove(token);
        log.info("[ClientInvitation] Compte créé pour {} (clientId: {})", client.getEmail(), client.getId());

        return savedUser;
    }

    // =========================================================================
    // Envoi email
    // =========================================================================

    private void sendInvitationEmail(String to, String clientName, String lawyerName, String token) {
        String link = baseUrl + "/clients/accept-invitation?token=" + token;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromEmail);
        msg.setTo(to);
        msg.setSubject("Votre invitation au portail DocAvocat");
        msg.setText(
            "Bonjour " + clientName + ",\n\n" +
            "Votre avocat " + lawyerName + " vous invite à accéder au portail client DocAvocat " +
            "pour consulter vos dossiers et documents.\n\n" +
            "Cliquez sur le lien ci-dessous pour créer votre mot de passe :\n\n" +
            link + "\n\n" +
            "Ce lien est valable " + TOKEN_EXPIRY_HOURS + " heures.\n\n" +
            "L'équipe DocAvocat\n" + baseUrl
        );
        mailSender.send(msg);
    }

    // =========================================================================
    // Record interne
    // =========================================================================

    public record InvitationEntry(String clientId, String email, LocalDateTime expiry) {}

    /** Mémoire: nettoyage périodique des invitations expirées */
    @Scheduled(fixedRate = 3600000) // 1 heure
    public void cleanupExpiredInvitations() {
        LocalDateTime now = LocalDateTime.now();
        pendingInvitations.entrySet().removeIf(e -> now.isAfter(e.getValue().expiry()));
    }
}
