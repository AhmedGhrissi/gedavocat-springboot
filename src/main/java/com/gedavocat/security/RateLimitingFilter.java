package com.gedavocat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEC-RATE-LIMIT : Filtre de rate limiting par IP.
 * 
 * Limites :
 * - Auth endpoints (POST) : 10 req/min par IP
 * - Email resend (POST)   : 3 req/min par IP
 * - API globale            : 60 req/min par IP
 * - Pages web globales     : 200 req/min par IP
 * 
 * Protège contre brute-force, email bombing, DoS, scraping.
 */
@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_MAX = 10;
    private static final int EMAIL_MAX = 3;
    private static final int API_MAX = 60;
    private static final int GLOBAL_MAX = 200;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, RateBucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, RateBucket> emailBuckets = new ConcurrentHashMap<>();
    private final Map<String, RateBucket> apiBuckets = new ConcurrentHashMap<>();
    private final Map<String, RateBucket> globalBuckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Ne pas filtrer les assets statiques
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.startsWith("/images/") || path.startsWith("/webjars/")
                || path.startsWith("/favicon") || path.equals("/robots.txt");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean isPost = "POST".equalsIgnoreCase(method);
        
        // 1. Email resend : limite ultra-stricte (3/min)
        if (isPost && (path.equals("/verify-email/resend") || path.equals("/verify-email"))) {
            RateBucket bucket = emailBuckets.computeIfAbsent(clientIp, k -> new RateBucket(EMAIL_MAX));
            if (!bucket.tryConsume()) {
                log.warn("SEC-RATE-LIMIT: email bombing bloqué pour IP {}", clientIp);
                sendTooManyRequests(response);
                return;
            }
        }
        
        // 2. Auth endpoints (POST) : limite stricte (10/min)
        if (isPost && isAuthEndpoint(path)) {
            RateBucket bucket = authBuckets.computeIfAbsent(clientIp, k -> new RateBucket(AUTH_MAX));
            if (!bucket.tryConsume()) {
                log.warn("SEC-RATE-LIMIT: brute-force bloqué pour IP {} sur {}", clientIp, path);
                sendTooManyRequests(response);
                return;
            }
        }
        
        // 3. API globale (toutes méthodes) : 60/min
        if (path.startsWith("/api/")) {
            RateBucket bucket = apiBuckets.computeIfAbsent(clientIp, k -> new RateBucket(API_MAX));
            if (!bucket.tryConsume()) {
                log.warn("SEC-RATE-LIMIT: API rate limit atteint pour IP {}", clientIp);
                sendTooManyRequests(response);
                return;
            }
        }
        
        // 4. Global : 200/min
        RateBucket globalBucket = globalBuckets.computeIfAbsent(clientIp, k -> new RateBucket(GLOBAL_MAX));
        if (!globalBucket.tryConsume()) {
            log.warn("SEC-RATE-LIMIT: global rate limit atteint pour IP {}", clientIp);
            sendTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }
    
    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Trop de requêtes. Réessayez dans une minute.\"}");
    }
    
    private boolean isAuthEndpoint(String path) {
        return path.equals("/login") || path.equals("/register")
                || path.equals("/forgot-password") || path.equals("/reset-password")
                || path.startsWith("/api/auth/")
                || path.startsWith("/collaborators/accept-invitation")
                || path.startsWith("/huissiers/accept-invitation")
                || path.startsWith("/clients/accept-invitation");
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // Accepter X-Real-IP / X-Forwarded-For seulement depuis le reverse proxy local
        if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty() && realIp.matches("^[0-9a-fA-F.:]+$")) {
                return realIp.trim();
            }
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                String ip = xff.split(",")[0].trim();
                if (ip.matches("^[0-9a-fA-F.:]+$")) return ip;
            }
        }
        return remoteAddr;
    }
    
    /**
     * Nettoyage périodique des buckets expirés (toutes les 5 minutes).
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupBuckets() {
        long now = System.currentTimeMillis();
        long expiry = WINDOW_MILLIS * 5;
        authBuckets.entrySet().removeIf(e -> now - e.getValue().windowStart > expiry);
        emailBuckets.entrySet().removeIf(e -> now - e.getValue().windowStart > expiry);
        apiBuckets.entrySet().removeIf(e -> now - e.getValue().windowStart > expiry);
        globalBuckets.entrySet().removeIf(e -> now - e.getValue().windowStart > expiry);
    }

    private static class RateBucket {
        volatile long windowStart = System.currentTimeMillis();
        private int count = 0;
        private final int maxRequests;
        
        RateBucket(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MILLIS) {
                windowStart = now;
                count = 1;
                return true;
            }
            count++;
            return count <= maxRequests;
        }
    }
}
