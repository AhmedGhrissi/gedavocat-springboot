package com.gedavocat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SEC-PERMISSIONS-POLICY : Ajoute le header Permissions-Policy sur toutes les réponses.
 * Spring Security 6.4+ a retiré le support natif de ce header.
 * Ce filtre le réinjecte conformément aux recommandations ANSSI/OWASP.
 */
@Component
public class PermissionsPolicyFilter extends OncePerRequestFilter {

    private static final String POLICY = String.join(", ",
            "camera=()",
            "microphone=()",
            "geolocation=()",
            "payment=(self)",
            "usb=()",
            "magnetometer=()",
            "gyroscope=()",
            "accelerometer=()",
            "autoplay=()",
            "fullscreen=(self)",
            "picture-in-picture=()"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Permissions-Policy", POLICY);
        filterChain.doFilter(request, response);
    }
}
