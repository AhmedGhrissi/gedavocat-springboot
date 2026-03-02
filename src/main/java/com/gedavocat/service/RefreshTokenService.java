package com.gedavocat.service;

import com.gedavocat.model.RefreshToken;
import com.gedavocat.model.User;
import com.gedavocat.repository.RefreshTokenRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.JwtServiceRS256;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de gestion des Refresh Tokens
 * 
 * Fonctionnalités:
 * - Création de refresh tokens
 * - Validation et renouvellement
 * - Révocation (logout, changement mot de passe)
 * - Nettoyage automatique des tokens expirés
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtServiceRS256 jwtService;

    /**
     * Créer un nouveau refresh token pour un utilisateur
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, HttpServletRequest request) {
        // Générer le JWT refresh token
        String tokenValue = jwtService.generateRefreshToken(user);
        
        // Calculer la date d'expiration
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusSeconds(jwtService.getRefreshTokenExpiration() / 1000);
        
        // Créer l'entité RefreshToken
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID().toString());
        refreshToken.setToken(tokenValue);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(expiresAt);
        
        // Device fingerprint (user-agent + IP)
        if (request != null) {
            refreshToken.setDeviceFingerprint(request.getHeader("User-Agent"));
            refreshToken.setIpAddress(getClientIp(request));
        }
        
        log.info("Created refresh token for user: {} (expires: {})", user.getEmail(), expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Trouver un refresh token par sa valeur
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Vérifier et obtenir un refresh token valide
     */
    public Optional<RefreshToken> verifyRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
            .filter(RefreshToken::isValid);
    }

    /**
     * Renouveler un access token à partir d'un refresh token
     */
    @Transactional
    public String refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = verifyRefreshToken(refreshTokenValue)
            .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide ou expiré"));
        
        User user = refreshToken.getUser();
        log.debug("Refreshing access token for user: {}", user.getEmail());
        
        return jwtService.generateAccessToken(user);
    }

    /**
     * Révoquer un refresh token spécifique
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.revoke();
            refreshTokenRepository.save(rt);
            log.info("Revoked refresh token for user: {}", rt.getUser().getEmail());
        });
    }

    /**
     * Révoquer tous les refresh tokens d'un utilisateur
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());
        log.info("Revoked all refresh tokens for user: {}", user.getEmail());
    }

    /**
     * Révoquer tous les refresh tokens d'un utilisateur sauf celui en cours
     */
    @Transactional
    public void revokeAllUserTokensExcept(User user, String currentToken) {
        refreshTokenRepository.findByUser(user).forEach(rt -> {
            if (!rt.getToken().equals(currentToken) && !rt.isRevoked()) {
                rt.revoke();
                refreshTokenRepository.save(rt);
            }
        });
        log.info("Revoked all refresh tokens for user {} except current", user.getEmail());
    }

    /**
     * Nettoyage automatique des tokens expirés (tous les jours à 3h)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        
        // Supprimer les tokens expirés
        refreshTokenRepository.deleteExpiredTokens(now);
        
        // Supprimer les tokens révoqués depuis plus de 30 jours
        refreshTokenRepository.deleteRevokedTokensOlderThan(now.minusDays(30));
        
        log.info("Cleaned up expired and old revoked refresh tokens");
    }

    /**
     * Extraire l'IP du client depuis la requête
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Prendre la première IP si plusieurs (proxies)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
