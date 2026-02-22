package com.gedavocat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Listener d'événements d'authentification — ANSSI/OWASP
 * Journalise toutes les tentatives de connexion (succès et échecs).
 * Requis par ANSSI RGS et OWASP ASVS 2.2.
 */
@Component
@Slf4j
public class SecurityAuditListener {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String ip = extractIp(event.getAuthentication().getDetails());
        log.info("SECURITY_AUDIT: LOGIN_SUCCESS user={} ip={}", username, ip);
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String ip = extractIp(event.getAuthentication().getDetails());
        String reason = event.getException().getClass().getSimpleName();
        log.warn("SECURITY_AUDIT: LOGIN_FAILURE user={} ip={} reason={}", username, ip, reason);
    }

    private String extractIp(Object details) {
        if (details instanceof WebAuthenticationDetails) {
            return ((WebAuthenticationDetails) details).getRemoteAddress();
        }
        return "unknown";
    }
}
