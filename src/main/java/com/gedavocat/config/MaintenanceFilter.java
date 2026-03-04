package com.gedavocat.config;

import com.gedavocat.service.MaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre HTTP qui redirige vers /maintenance si le mode maintenance est actif,
 * sauf pour les admins, les assets statiques et la page maintenance elle-même.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceService maintenanceService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!maintenanceService.isMaintenanceEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Bypass : assets statiques, page maintenance, login, toggle admin
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Bypass : tout utilisateur authentifié (admin, avocat, client)
        if (isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Tout le reste => page maintenance
        response.sendRedirect(request.getContextPath() + "/maintenance");
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/favicon.ico")
                || path.equals("/maintenance")
                || path.startsWith("/api/admin/maintenance")
                || path.equals("/login")
                || path.equals("/register")
                || path.startsWith("/subscription")
                || path.equals("/logout");
    }

    private boolean isAuthenticated() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        // Exclure l'utilisateur anonyme
        return !auth.getPrincipal().equals("anonymousUser");
    }
}
