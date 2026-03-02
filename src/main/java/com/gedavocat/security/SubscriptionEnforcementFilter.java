package com.gedavocat.security;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filtre de vérification d'abonnement actif.
 * 
 * Bloque l'accès aux routes payantes (dossiers, clients, documents, 
 * signatures, factures, RPVA) si l'utilisateur LAWYER n'a pas 
 * d'abonnement actif. Redirige vers la page de tarification.
 * 
 * Les ADMINs, CLIENTs, HUISSIERS et LAWYER_SECONDARY ne sont pas concernés
 * car ils accèdent via l'abonnement du LAWYER principal.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    /**
     * Préfixes d'URL nécessitant un abonnement actif pour les LAWYER.
     */
    private static final Set<String> PAID_PREFIXES = Set.of(
        "/dashboard",
        "/clients",
        "/cases",
        "/documents",
        "/signatures",
        "/rpva",
        "/invoices",
        "/permissions",
        "/api/clients",
        "/api/cases",
        "/api/documents",
        "/api/invoices"
    );

    /**
     * Routes explicitement exclues du paywall (pages de paiement, profil, etc.)
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/subscription",
        "/payment",
        "/subscription/pricing",
        "/payment/pricing",
        "/payment/success",
        "/payment/cancel",
        "/profile",
        "/settings",
        "/api/auth",
        "/logout"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Ne pas filtrer les ressources statiques
        if (path.startsWith("/css/") || path.startsWith("/js/") || 
            path.startsWith("/images/") || path.startsWith("/img/") ||
            path.startsWith("/webjars/") || path.startsWith("/favicon")) {
            return true;
        }
        
        // Ne pas filtrer les routes explicitement exclues
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        
        // Ne filtrer que les routes payantes
        boolean isPaidRoute = false;
        for (String prefix : PAID_PREFIXES) {
            if (path.startsWith(prefix)) {
                isPaidRoute = true;
                break;
            }
        }
        return !isPaidRoute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Si pas authentifié, laisser Spring Security gérer
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Vérifier le rôle — seuls les LAWYER sont soumis au paywall
        boolean isLawyer = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LAWYER"));
        
        if (!isLawyer) {
            // ADMIN, CLIENT, HUISSIER, LAWYER_SECONDARY : pas de paywall
            filterChain.doFilter(request, response);
            return;
        }

        // Récupérer l'utilisateur et vérifier l'abonnement
        String email = null;
        if (auth.getPrincipal() instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = auth.getName();
        }

        try {
            User user = userRepository.findByEmail(email).orElse(null);
            
            // SEC-FAILCLOSED FIX : Si l'utilisateur n'est pas trouvé en DB, bloquer l'accès (fail-closed)
            if (user == null) {
                log.warn("Utilisateur {} non trouvé en base — accès refusé (fail-closed)", email);
                if (request.getRequestURI().startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Utilisateur non trouvé\"}");
                    return;
                }
                response.sendRedirect("/login?error=true");
                return;
            }
            
            if (user.hasActiveSubscription()) {
                // Abonnement actif — accès autorisé
                filterChain.doFilter(request, response);
                return;
            }

            // Abonnement inactif ou expiré — redirection vers pricing
            log.warn("Accès refusé pour {} - abonnement inactif/expiré. Redirection vers /subscription/pricing", email);
            
            // Pour les requêtes API, retourner 403
            if (request.getRequestURI().startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Abonnement requis\", \"redirect\": \"/subscription/pricing\"}");
                return;
            }
            
            // Pour les pages web, redirection
            response.sendRedirect("/subscription/pricing?expired=true");
            
        } catch (Exception e) {
            log.error("Erreur lors de la vérification d'abonnement pour {}: {}", email, e.getMessage());
            // SEC-FAILCLOSED FIX : En cas d'erreur technique, bloquer l'accès (fail-closed)
            if (request.getRequestURI().startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Service temporairement indisponible\"}");
                return;
            }
            response.sendRedirect("/login?error=true");
        }
    }
}
