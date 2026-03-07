package com.gedavocat.security;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
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
 * - Abonnement actif (ACTIVE/TRIAL/CANCELLED non expiré) : accès complet
 * - Abonnement expiré/inactif mais avec un plan : accès LECTURE SEULE
 *   (GET autorisé sauf création, POST/PUT/DELETE bloqués)
 * - Jamais eu d'abonnement : redirection vers pricing
 * 
 * Les ADMINs, CLIENTs, HUISSIERS et LAWYER_SECONDARY ne sont pas concernés
 * car ils accèdent via l'abonnement du LAWYER principal.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionEnforcementFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final Environment environment;

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
        "/firm",
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

    /**
     * Suffixes d'URL de création/modification bloqués en mode lecture seule.
     * Les GET sur ces suffixes (formulaires) sont aussi bloqués.
     */
    private static final Set<String> WRITE_SUFFIXES = Set.of(
        "/new", "/create", "/edit", "/delete", "/save",
        "/upload", "/sign", "/send", "/generate"
    );

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Désactiver complètement le filtre en mode test
        String[] profiles = environment.getActiveProfiles();
        for (String profile : profiles) {
            if ("test".equals(profile)) {
                return true;
            }
        }
        
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
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Si pas authentifié, laisser Spring Security gérer
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Vérifier le rôle — seuls les LAWYER et AVOCAT_ADMIN sont soumis au paywall
        boolean isLawyer = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LAWYER") || a.getAuthority().equals("ROLE_AVOCAT_ADMIN"));
        
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
                // Abonnement actif (ACTIVE/TRIAL/CANCELLED non expiré) — accès complet
                filterChain.doFilter(request, response);
                return;
            }

            // ── Mode lecture seule pour les abonnements expirés ──
            if (user.hasReadOnlyAccess()) {
                String method = request.getMethod();
                String path = request.getRequestURI();
                
                // Autoriser les téléchargements (GET /documents/.../download)
                if ("GET".equals(method) && path.contains("/download")) {
                    request.setAttribute("readOnlyMode", true);
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Bloquer toutes les écritures (POST, PUT, DELETE, PATCH)
                if (!"GET".equals(method)) {
                    log.warn("Écriture bloquée en mode lecture seule pour {} : {} {}", email, method, path);
                    if (path.startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Abonnement expiré — accès en lecture seule\"}");
                        return;
                    }
                    response.sendRedirect("/subscription/pricing?expired=true");
                    return;
                }
                
                // Bloquer les pages de création/modification même en GET
                boolean isWritePage = false;
                for (String suffix : WRITE_SUFFIXES) {
                    if (path.endsWith(suffix)) {
                        isWritePage = true;
                        break;
                    }
                }
                if (isWritePage) {
                    log.warn("Page de création bloquée en lecture seule pour {} : {}", email, path);
                    if (path.startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Abonnement expiré — accès en lecture seule\"}");
                        return;
                    }
                    response.sendRedirect("/subscription/pricing?expired=true");
                    return;
                }
                
                // GET en lecture seule autorisé (consultation, listes, etc.)
                request.setAttribute("readOnlyMode", true);
                filterChain.doFilter(request, response);
                return;
            }

            // Jamais eu d'abonnement — redirection vers pricing
            log.warn("Accès refusé pour {} - pas d'abonnement. Redirection vers /subscription/pricing", email);
            
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
