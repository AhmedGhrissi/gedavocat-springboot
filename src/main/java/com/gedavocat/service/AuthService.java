package com.gedavocat.service;

import com.gedavocat.dto.AuthRequest;
import com.gedavocat.dto.AuthResponse;
import com.gedavocat.dto.RegisterRequest;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Un utilisateur avec cet email existe déjà");
        }
        
        // Vérifier la confirmation du mot de passe
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Les mots de passe ne correspondent pas");
        }
        
        // Vérifier les conditions d'utilisation
        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new RuntimeException("Vous devez accepter les conditions d'utilisation");
        }
        
        if (!Boolean.TRUE.equals(request.getGdprConsent())) {
            throw new RuntimeException("Vous devez accepter le traitement de vos données personnelles");
        }
        
        // Créer le nouvel utilisateur
        User user = new User();
        
        // Construire le nom complet à partir de firstName et lastName
        String fullName = request.getName();
        if (fullName == null || fullName.trim().isEmpty()) {
            fullName = (request.getFirstName() + " " + request.getLastName()).trim();
        }
        user.setName(fullName);
        
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        // Définir le rôle (par défaut LAWYER si non spécifié)
        String role = request.getRole();
        if (role == null || role.trim().isEmpty()) {
            role = "LAWYER";
        }
        user.setRole(User.UserRole.valueOf(role));
        
        user.setTermsAcceptedAt(LocalDateTime.now());
        user.setGdprConsentAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        
        // Générer le token JWT
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);
        
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }
    
    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);
        
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }
    
    public AuthResponse refreshToken(String oldToken) {
        String userEmail = jwtService.extractUsername(oldToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        
        if (jwtService.isTokenValid(oldToken, userDetails)) {
            String newToken = jwtService.generateToken(userDetails);
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            return AuthResponse.builder()
                    .token(newToken)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .name(user.getName())
                    .role(user.getRole().name())
                    .build();
        }
        
        throw new RuntimeException("Token invalide");
    }
}
