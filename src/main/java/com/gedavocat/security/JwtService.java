package com.gedavocat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service JWT HMAC-HS256 — DÉSACTIVÉ (SEC FIX F-02)
 * Remplacé par JwtServiceRS256 (RSA-4096, ANSSI niveau bancaire).
 * Conservé pour référence. Ne pas réactiver.
 */
// @Service — retiré : JwtServiceRS256 utilisé à la place dans tout le pipeline auth
public class JwtService {
    
    private static final String JWT_ISSUER = "docavocat.fr";
    private static final String JWT_AUDIENCE = "docavocat-api";
    
    @Value("${jwt.secret}") // gitleaks:allow
    private String secretKey;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private final JwtBlacklistService jwtBlacklistService;

    public JwtService(JwtBlacklistService jwtBlacklistService) {
        this.jwtBlacklistService = jwtBlacklistService;
    }

    /**
     * Extrait le nom d'utilisateur (email) du token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Extrait une claim spécifique du token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * Génère un token pour un utilisateur
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }
    
    /**
     * Génère un token avec des claims supplémentaires
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }
    
    /**
     * Construit le token JWT
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuer(JWT_ISSUER)
                .audience().add(JWT_AUDIENCE).and()
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }
    
    /**
     * Valide un token (vérifie signature, expiration, et blacklist)
     * SEC-01 FIX : ajout vérification blacklist pour révocation
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (jwtBlacklistService.isBlacklisted(token)) {
            return false;
        }
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }
    
    /**
     * Vérifie si le token est expiré
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    /**
     * Extrait la date d'expiration du token (public pour le logout/blacklist)
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    /**
     * Extrait toutes les claims du token
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(getSignInKey())
                .requireIssuer(JWT_ISSUER)
                .requireAudience(JWT_AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Récupère la clé de signature
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
