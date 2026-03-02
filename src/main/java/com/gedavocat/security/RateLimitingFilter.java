package com.gedavocat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre de rate limiting pour les endpoints d'authentification.
 * Protège contre les attaques par force brute sur /login, /register, /api/auth/**.
 * Limite : 10 requêtes par minute par IP.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    // IP → bucket
    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // SEC FIX M-14 : rate limiting étendu aux invitations et webhooks
        return !path.startsWith("/login") && !path.startsWith("/register")
                && !path.startsWith("/api/auth/")
                && !path.startsWith("/forgot-password")
                && !path.startsWith("/reset-password")
                && !path.startsWith("/verify-email")
                && !path.startsWith("/collaborators/accept-invitation")
                && !path.startsWith("/huissiers/accept-invitation")
                && !path.startsWith("/subscription/webhook")
                && !path.startsWith("/payment/webhook")
                && !path.startsWith("/api/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Seulement les POST (tentatives de soumission)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        RateBucket bucket = buckets.computeIfAbsent(clientIp, k -> new RateBucket());

        if (!bucket.tryConsume()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Trop de tentatives. Veuillez réessayer dans une minute.\"}");
            return;
        }

        filterChain.doFilter(request, response);

        // Nettoyage périodique des vieux buckets (1 sur 100 requêtes)
        if (Math.random() < 0.01) {
            long now = System.currentTimeMillis();
            buckets.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MILLIS * 5);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        // SÉCURITÉ : ne faire confiance à X-Forwarded-For que si le proxy est de confiance
        // On préfère l'IP directe pour éviter le spoofing
        String remoteAddr = request.getRemoteAddr();
        // Accepter X-Real-IP seulement depuis le reverse proxy local
        if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private static class RateBucket {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        // SEC FIX : synchronize pour éviter la race condition sur le reset de fenêtre
        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MILLIS) {
                // Reset window
                windowStart = now;
                count = 1;
                return true;
            }
            count++;
            return count <= MAX_REQUESTS_PER_MINUTE;
        }
    }
}
