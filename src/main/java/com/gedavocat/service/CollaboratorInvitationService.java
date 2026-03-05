package com.gedavocat.service;

import com.gedavocat.model.CaseShareLink;
import com.gedavocat.repository.CaseShareLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Service léger pour gérer les invitations de collaborateurs (similaire à ClientInvitationService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaboratorInvitationService {

    private final CaseShareLinkRepository caseShareLinkRepository;

    private final Map<String, InvitationEntry> pending = new ConcurrentHashMap<>();

    private static final int TOKEN_EXPIRY_HOURS = 72;

    public void registerInvitation(CaseShareLink link) {
        if (link == null || link.getToken() == null) return;
        if (link.getInvitedAt() == null) return; // require invitedAt like clients
        String token = link.getToken();
        LocalDateTime expiry = link.getInvitedAt().plusHours(TOKEN_EXPIRY_HOURS);
        pending.put(token, new InvitationEntry(link.getId(), link.getRecipientEmail(), expiry));
        log.info("[CollaboratorInvitation] Registered token {} for link {}", token, link.getId());
    }

    public Optional<InvitationEntry> validateToken(String token) {
        InvitationEntry entry = pending.get(token);
        if (entry != null) {
            if (LocalDateTime.now().isAfter(entry.expiry())) {
                pending.remove(token);
                return Optional.empty();
            }
            return Optional.of(entry);
        }
        // Fallback to DB
        Optional<CaseShareLink> maybe = caseShareLinkRepository.findByToken(token);
        if (maybe.isEmpty()) return Optional.empty();
        CaseShareLink link = maybe.get();
        // Mirror client behavior: require invitedAt to be present and within expiry window
        LocalDateTime expiry;
        if (link.getInvitedAt() != null) {
            expiry = link.getInvitedAt().plusHours(TOKEN_EXPIRY_HOURS);
        } else if (link.getRecipientEmail() != null && link.getCreatedAt() != null) {
            // tolerate older links: use createdAt as invitedAt fallback if recipientEmail exists
            expiry = link.getCreatedAt().plusHours(TOKEN_EXPIRY_HOURS);
        } else {
            return Optional.empty();
        }
        if (LocalDateTime.now().isAfter(expiry)) return Optional.empty();
        InvitationEntry rebuilt = new InvitationEntry(link.getId(), link.getRecipientEmail(), expiry);
        pending.put(token, rebuilt);
        return Optional.of(rebuilt);
    }

    public void removeToken(String token) {
        if (token == null) return;
        pending.remove(token);
        log.info("[CollaboratorInvitation] Removed token {} from memory", token);
    }

    public record InvitationEntry(String linkId, String email, LocalDateTime expiry) {}

    /** Mémoire: nettoyage périodique des invitations expirées */
    @Scheduled(fixedRate = 3600000) // 1 heure
    public void cleanupExpiredInvitations() {
        LocalDateTime now = LocalDateTime.now();
        pending.entrySet().removeIf(e -> now.isAfter(e.getValue().expiry()));
    }
}