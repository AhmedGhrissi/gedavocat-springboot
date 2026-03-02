package com.gedavocat.security;

import com.gedavocat.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;

/**
 * Filtre Hibernate pour isolation multi-tenant automatique
 * 
 * Ce filtre active automatiquement un filtre Hibernate qui ajoute
 * une clause WHERE firm_id = :firmId à TOUTES les requêtes JPA.
 * 
 * Architecture:
 * 1. Récupère l'utilisateur authentifié (JWT)
 * 2. Extrait son firmId
 * 3. Active le filtre Hibernate "firmFilter" avec ce firmId
 * 4. Toutes les requêtes JPA sont automatiquement filtrées
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 VULN-01
 * 
 * @author Gedavocat Security Team
 * @version 1.0
 */
@Component
@Slf4j
public class MultiTenantFilter extends OncePerRequestFilter {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            // Récupérer l'utilisateur authentifié depuis le contexte Spring Security
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() 
                    && authentication.getPrincipal() instanceof User) {
                
                User currentUser = (User) authentication.getPrincipal();
                
                // Récupérer le firmId de l'utilisateur
                if (currentUser.getFirm() != null && currentUser.getFirm().getId() != null) {
                    String firmId = currentUser.getFirm().getId();
                    
                    // Activer le filtre Hibernate pour ce firmId
                    Session session = entityManager.unwrap(Session.class);
                    session.enableFilter("firmFilter")
                           .setParameter("firmId", firmId);
                    
                    log.debug("Multi-tenant filter activated for firmId: {} (user: {})", 
                             firmId, currentUser.getEmail());
                } else {
                    // Utilisateur sans cabinet (ADMIN système)
                    // Pas de filtre appliqué - accès total
                    log.debug("Multi-tenant filter skipped for admin user: {}", 
                             currentUser.getEmail());
                }
            }
            
        } catch (Exception e) {
            log.error("Error activating multi-tenant filter: {}", e.getMessage(), e);
            // Ne pas bloquer la requête en cas d'erreur
        }
        
        // Continuer la chaîne de filtres
        filterChain.doFilter(request, response);
    }
}
