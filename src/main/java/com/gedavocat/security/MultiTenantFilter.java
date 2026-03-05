package com.gedavocat.security;

import com.gedavocat.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;

/**
 * Filtre Hibernate pour isolation multi-tenant automatique (NIVEAU MILITAIRE/BANCAIRE)
 * 
 * SEC-CRITICAL FIX : Le principal Spring Security est un UserDetails (Spring), 
 * pas l'entité User JPA. On résout maintenant l'entité via le repository
 * pour activer correctement le filtre Hibernate.
 * 
 * Ce filtre active automatiquement un filtre Hibernate qui ajoute
 * une clause WHERE firm_id = :firmId à TOUTES les requêtes JPA.
 * 
 * Architecture:
 * 1. Récupère l'email depuis l'authentification Spring Security
 * 2. Résout l'entité User JPA via le repository
 * 3. Extrait son firmId
 * 4. Active le filtre Hibernate "firmFilter" avec ce firmId
 * 5. Toutes les requêtes JPA sont automatiquement filtrées
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 VULN-01
 * 
 * @author Gedavocat Security Team
 * @version 2.0 (SEC-CRITICAL FIX - tenant isolation)
 */
@Component
@Slf4j
public class MultiTenantFilter extends OncePerRequestFilter {

    @PersistenceContext
    private EntityManager entityManager;

    private final UserRepository userRepository;

    public MultiTenantFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() 
                    && authentication.getPrincipal() instanceof UserDetails) {
                
                // SEC-CRITICAL FIX : Extraire l'email depuis UserDetails (Spring Security)
                // puis résoudre l'entité User JPA pour obtenir le firmId
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String email = userDetails.getUsername();
                
                // Vérifier si c'est un ADMIN système (pas de filtre tenant)
                boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                
                if (isAdmin) {
                    log.debug("Multi-tenant filter skipped for ADMIN user: {}", email);
                } else {
                    // Résoudre l'entité User pour obtenir le firmId
                    userRepository.findByEmail(email).ifPresent(user -> {
                        if (user.getFirm() != null && user.getFirm().getId() != null) {
                            String firmId = user.getFirm().getId();
                            
                            // Activer le filtre Hibernate pour ce firmId
                            Session session = entityManager.unwrap(Session.class);
                            session.enableFilter("firmFilter")
                                   .setParameter("firmId", firmId);
                            
                            log.debug("Multi-tenant filter activated for firmId: {} (user: {})", 
                                     firmId, email);
                        } else {
                            // Utilisateur sans cabinet — REFUSER l'accès par sécurité
                            // (sauf ADMIN déjà filtré ci-dessus)
                            log.warn("SEC-ALERT: User {} has no firm but is not ADMIN — tenant filter NOT applied. "
                                   + "Data access may be unrestricted.", email);
                        }
                    });
                }
            }
            
        } catch (Exception e) {
            log.error("SEC-CRITICAL: Error activating multi-tenant filter: {}", e.getMessage(), e);
            // En mode sécurité maximale : bloquer la requête si le filtre ne s'active pas
            // pour éviter toute fuite inter-tenant
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Erreur de sécurité — isolation des données compromise");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
