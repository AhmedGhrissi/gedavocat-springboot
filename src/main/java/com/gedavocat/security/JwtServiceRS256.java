package com.gedavocat.security;

import com.gedavocat.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Service de gestion des tokens JWT avec RS256 (RSA) — NIVEAU MILITAIRE/BANCAIRE
 * 
 * Architecture de sécurité renforcée :
 * - RS256 (RSA-SHA256)
 * - Clés privée/publique asymétriques (4096 bits — ANSSI 2026+)
 * - Access Token (15 min) + Refresh Token (7 jours)
 * - Validation issuer/audience
 * - Blacklist pour révocation
 * - Permissions fichiers restrictives (600)
 * 
 * Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.3
 * 
 * @author Gedavocat Security Team
 * @version 3.0 (SEC-HARDENED : RS256-4096, blacklist, issuer/audience)
 */
@Service
@Slf4j
public class JwtServiceRS256 {
    
    private static final String JWT_ISSUER = "docavocat.fr";
    private static final String JWT_AUDIENCE = "docavocat-api";
    // SEC-HARDENED : RSA 4096 bits minimum (ANSSI recommandation ≥3072 pour 2026+)
    private static final int RSA_KEY_SIZE = 4096;
    
    @Value("${jwt.expiration:900000}") // SEC-HARDENED : 15 minutes par défaut (OWASP)
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh.expiration:604800000}") // 7 jours par défaut
    private long refreshTokenExpiration;
    
    @Value("${jwt.keys.private-key-path:config/keys/private_key.pem}")
    private String privateKeyPath;
    
    @Value("${jwt.keys.public-key-path:config/keys/public_key.pem}")
    private String publicKeyPath;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;
    
    private final JwtBlacklistService jwtBlacklistService;
    
    public JwtServiceRS256(JwtBlacklistService jwtBlacklistService) {
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @PostConstruct
    public void init() {
        try {
            loadOrGenerateKeys();
            // SEC-HARDENED : Valider la taille de la clé RSA
            if (publicKey instanceof java.security.interfaces.RSAPublicKey rsaPub) {
                int keySize = rsaPub.getModulus().bitLength();
                if (keySize < 3072) {
                    log.error("SEC-CRITICAL: RSA key size {} bits is too small. Minimum 3072 bits required (ANSSI). "
                            + "Delete existing keys and restart to auto-generate 4096-bit keys.", keySize);
                    throw new IllegalStateException("RSA key too small: " + keySize + " bits (min 3072)");
                }
                log.info("JWT RS256 keys loaded successfully (RSA-{} bits)", keySize);
            }
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
            log.warn("SEC-WARN: JWT keys not found. Generating new RSA-{} key pair...", RSA_KEY_SIZE);
            generateAndSaveKeys();
        }
        
        // Charger la clé privée
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        String privateKeyPEM = new String(privateKeyBytes)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN OPENSSH PRIVATE KEY-----", "")
            .replace("-----END OPENSSH PRIVATE KEY-----", "")
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
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("-----BEGIN OPENSSH PUBLIC KEY-----", "")
            .replace("-----END OPENSSH PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] publicKeyDecoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyDecoded);
        publicKey = keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * Génère et sauvegarde une nouvelle paire de clés RSA
     * SEC-HARDENED : 4096 bits + permissions fichiers restrictives
     */
    private void generateAndSaveKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        // SEC-HARDENED : 4096 bits (ANSSI recommandation ≥3072 pour 2026+)
        keyGen.initialize(RSA_KEY_SIZE, SecureRandom.getInstanceStrong());
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
        
        // SEC-HARDENED : Définir permissions restrictives sur les fichiers de clés (600)
        try {
            Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(new File(privateKeyPath).toPath(), ownerOnly);
            Files.setPosixFilePermissions(new File(publicKeyPath).toPath(), ownerOnly);
            log.info("SEC: File permissions set to 600 on RSA key files");
        } catch (UnsupportedOperationException e) {
            log.warn("SEC-WARN: Cannot set POSIX permissions (Windows?). Secure key files manually.");
        }
        
        log.info("Generated new RSA-{} key pair at: {}", RSA_KEY_SIZE, keysDir.getAbsolutePath());
        
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
     * SEC-HARDENED : ajout issuer/audience
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
                .signWith(privateKey, Jwts.SIG.RS256)
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
     * SEC-HARDENED : validation issuer/audience obligatoire
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith(publicKey)
                .requireIssuer(JWT_ISSUER)
                .requireAudience(JWT_AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Valide un token
     * SEC-HARDENED : ajout vérification blacklist pour révocation
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            // SEC-HARDENED : vérifier la blacklist avant toute autre validation
            if (jwtBlacklistService.isBlacklisted(token)) {
                log.debug("Token RS256 rejected: blacklisted");
                return false;
            }
            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (Exception e) {
            log.debug("Token RS256 validation failed: {}", e.getMessage());
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
     * Extrait la date d'expiration du token (public pour logout/blacklist)
     */
    public Date extractExpiration(String token) {
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
