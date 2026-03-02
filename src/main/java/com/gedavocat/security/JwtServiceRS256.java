package com.gedavocat.security;

import com.gedavocat.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service de gestion des tokens JWT avec RS256 (RSA)
 * 
 * Architecture de sécurité renforcée :
 * - RS256 (RSA-SHA256) au lieu de HS256
 * - Clés privée/publique asymétriques (2048 bits)
 * - Access Token + Refresh Token
 * - Rotation automatique des clés (optionnel)
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.3
 * 
 * @author Gedavocat Security Team
 * @version 2.0 (RS256)
 */
@Service
@Slf4j
public class JwtServiceRS256 {
    
    @Value("${jwt.expiration:3600000}") // 1 heure par défaut
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh.expiration:604800000}") // 7 jours par défaut
    private long refreshTokenExpiration;
    
    @Value("${jwt.keys.private-key-path:config/keys/private_key.pem}")
    private String privateKeyPath;
    
    @Value("${jwt.keys.public-key-path:config/keys/public_key.pem}")
    private String publicKeyPath;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            loadOrGenerateKeys();
            log.info("JWT RS256 keys loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load/generate JWT keys: {}", e.getMessage());
            throw new IllegalStateException("JWT initialization failed", e);
        }
    }

    /**
     * Charge les clés RSA ou les génère si elles n'existent pas
     */
    private void loadOrGenerateKeys() throws Exception {
        File privateKeyFile = new File(privateKeyPath);
        File publicKeyFile = new File(publicKeyPath);
        
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            log.warn("JWT keys not found. Generating new RSA key pair...");
            generateAndSaveKeys();
        }
        
        // Charger la clé privée
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        String privateKeyPEM = new String(privateKeyBytes)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] privateKeyDecoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyDecoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        privateKey = keyFactory.generatePrivate(privateKeySpec);
        
        // Charger la clé publique
        byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());
        String publicKeyPEM = new String(publicKeyBytes)
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] publicKeyDecoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyDecoded);
        publicKey = keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * Génère et sauvegarde une nouvelle paire de clés RSA
     */
    private void generateAndSaveKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        
        // Créer le répertoire si nécessaire
        File keysDir = new File(privateKeyPath).getParentFile();
        if (!keysDir.exists()) {
            keysDir.mkdirs();
        }
        
        // Sauvegarder la clé privée
        try (FileOutputStream fos = new FileOutputStream(privateKeyPath)) {
            fos.write("-----BEGIN PRIVATE KEY-----\n".getBytes());
            fos.write(Base64.getEncoder().encode(keyPair.getPrivate().getEncoded()));
            fos.write("\n-----END PRIVATE KEY-----\n".getBytes());
        }
        
        // Sauvegarder la clé publique
        try (FileOutputStream fos = new FileOutputStream(publicKeyPath)) {
            fos.write("-----BEGIN PUBLIC KEY-----\n".getBytes());
            fos.write(Base64.getEncoder().encode(keyPair.getPublic().getEncoded()));
            fos.write("\n-----END PUBLIC KEY-----\n".getBytes());
        }
        
        log.info("Generated new RSA key pair at: {}", keysDir.getAbsolutePath());
        
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }
    
    // ==========================================
    // ACCESS TOKEN (JWT principal)
    // ==========================================
    
    /**
     * Génère un access token (JWT) pour un utilisateur
     */
    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(new HashMap<>(), userDetails);
    }
    
    /**
     * Génère un access token pour un User (Gedavocat entity)
     */
    public String generateAccessToken(User user) {
        return generateAccessToken(new UserPrincipal(user));
    }
    
    /**
     * Génère un access token avec claims personnalisées
     */
    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        extraClaims.put("type", "access");
        return buildToken(extraClaims, userDetails, accessTokenExpiration);
    }
    
    // ==========================================
    // REFRESH TOKEN (renouvellement)
    // ==========================================
    
    /**
     * Génère un refresh token (longue durée de vie)
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userDetails, refreshTokenExpiration);
    }
    
    /**
     * Génère un refresh token pour un User (Gedavocat entity)
     */
    public String generateRefreshToken(User user) {
        return generateRefreshToken(new UserPrincipal(user));
    }
    
    /**
     * Vérifie si le token est un refresh token valide
     */
    public boolean isRefreshToken(String token) {
        try {
            String type = extractClaim(token, claims -> claims.get("type", String.class));
            return "refresh".equals(type);
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==========================================
    // CONSTRUCTION & VALIDATION
    // ==========================================
    
    /**
     * Construit le token JWT signé avec RS256
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
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
     * Extrait toutes les claims du token (vérifié avec la clé publique)
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * Valide un token
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Vérifie si le token est expiré
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    /**
     * Extrait la date d'expiration du token
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    // ==========================================
    // GETTERS
    // ==========================================
    
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
    
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
