package com.gedavocat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates a per-request CSP nonce and sets the Content-Security-Policy header.
 * Templates access the nonce via ${cspNonce} to add nonce="..." to inline scripts/styles.
 *
 * All inline scripts and styles must carry the nonce attribute: th:attr="nonce=${cspNonce}"
 * 'unsafe-inline' is intentionally absent — nonces provide strict CSP enforcement.
 */
@Component
@Order(1)
public class CspNonceFilter extends OncePerRequestFilter {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 16;

    // CDN origins
    private static final String CDN_JSDELIVR = "https://cdn.jsdelivr.net";
    private static final String CDN_CDNJS = "https://cdnjs.cloudflare.com";
    private static final String GOOGLE_FONTS_CSS = "https://fonts.googleapis.com";
    private static final String GOOGLE_FONTS_FILES = "https://fonts.gstatic.com";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        byte[] nonceBytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);

        // Expose nonce to Thymeleaf templates as ${cspNonce}
        request.setAttribute("cspNonce", nonce);

        // Build CSP with per-request nonce
        // 'unsafe-inline' is removed — nonces enforce strict inline policy on CSP L3 browsers
        String csp = "default-src 'self'; " +
                "script-src 'self' 'nonce-" + nonce + "' https://js.stripe.com " + CDN_JSDELIVR + "; " +
                "style-src 'self' 'nonce-" + nonce + "' " + CDN_JSDELIVR + " " + CDN_CDNJS + " " + GOOGLE_FONTS_CSS + "; " +
                "font-src 'self' data: " + CDN_CDNJS + " " + GOOGLE_FONTS_FILES + "; " +
                "img-src 'self' data:; " +
                "connect-src 'self' https://api.stripe.com https://api.payplug.com " + CDN_JSDELIVR + "; " +
                "frame-src 'self' https://js.stripe.com https://hooks.stripe.com; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "frame-ancestors 'self'";

        response.setHeader("Content-Security-Policy", csp);

        filterChain.doFilter(request, response);
    }
}
