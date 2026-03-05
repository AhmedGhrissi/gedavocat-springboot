package com.gedavocat.service;

import com.gedavocat.dto.AuthRequest;
import com.gedavocat.dto.AuthResponse;
import com.gedavocat.dto.RegisterRequest;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.gedavocat.model.Firm;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final FirmService firmService;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String emailNormalized = request.getEmail().trim().toLowerCase();
        request.setEmail(emailNormalized);
        
        // Vérifier si l'email existe déjà
        var existingUser = userRepository.findByEmail(emailNormalized);
        if (existingUser.isPresent()) {
            User existing = existingUser.get();
            // Si le compte n'est pas vérifié, supprimer le zombie et permettre la réinscription
            if (!existing.isEmailVerified()) {
                // Supprimer le compte zombie pour permettre la réinscription
                userRepository.delete(existing);
                userRepository.flush();
                log.info("Compte non vérifié supprimé pour réinscription : {}", emailNormalized);
            } else {
                // SEC-08 FIX : Message générique pour éviter l'énumération d'utilisateurs
                throw new RuntimeException("Erreur lors de l'inscription. Vérifiez vos informations.");
            }
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
        // Remplir firstName / lastName pour la table users (schéma exigeant)
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        // SÉCURITÉ : forcer le rôle LAWYER — interdire l'escalade de privilèges
        user.setRole(User.UserRole.LAWYER);
        
        user.setTermsAcceptedAt(LocalDateTime.now());
        user.setGdprConsentAt(LocalDateTime.now());
        user.setEmailVerified(false);  // doit être vérifié par email avant connexion
        user.setAccountEnabled(true);  // compte activé, mais connexion bloquée jusqu'à vérification email
        
        // Synchroniser le plan d'abonnement sur l'utilisateur (TOUJOURS, même sans cabinet)
        try {
            String p = request.getSubscriptionPlan() == null ? "" : request.getSubscriptionPlan().trim().toUpperCase();
            if (p.contains("ESSENTIEL")) {
                user.setSubscriptionPlan(User.SubscriptionPlan.ESSENTIEL);
            } else if (p.contains("PROFESSIONNEL")) {
                user.setSubscriptionPlan(User.SubscriptionPlan.PROFESSIONNEL);
            } else if (p.contains("CABINET_PLUS") || p.contains("CABINET+")) {
                user.setSubscriptionPlan(User.SubscriptionPlan.CABINET_PLUS);
            } else {
                user.setSubscriptionPlan(User.SubscriptionPlan.ESSENTIEL);
            }
            // INACTIVE : l'accès est bloqué tant que le paiement Stripe n'est pas validé
            // Le trial de 14 jours est géré par Stripe (trial_period_days sur les Price)
            user.setSubscriptionStatus(User.SubscriptionStatus.INACTIVE);
        } catch (Exception ignored) {
        }
        
        user = userRepository.save(user);
        
        // Si des informations de cabinet ont été fournies, créer le cabinet et l'associer
        if (request.getFirmName() != null && !request.getFirmName().trim().isEmpty()) {
            Firm firm = new Firm();
            firm.setName(request.getFirmName().trim());
            firm.setSiren(request.getFirmSiren());
            firm.setPhone(request.getFirmPhone());
            firm.setAddress(request.getFirmAddress());

            // Mapper le plan d'abonnement si présent
            String plan = request.getSubscriptionPlan();
            if (plan != null) {
                String p2 = plan.trim().toUpperCase();
                if (p2.contains("CABINET_PLUS") || p2.contains("CABINET+")) {
                    firm.setSubscriptionPlan(Firm.SubscriptionPlan.ENTERPRISE);
                } else if (p2.contains("PROFESSIONNEL")) {
                    firm.setSubscriptionPlan(Firm.SubscriptionPlan.CABINET);
                } else {
                    firm.setSubscriptionPlan(Firm.SubscriptionPlan.SOLO);
                }
            }

            firm.setSubscriptionStatus(Firm.SubscriptionStatus.TRIAL);
            firm.setSubscriptionStartsAt(LocalDateTime.now());
            firm.setSubscriptionEndsAt(LocalDateTime.now().plusDays(14));

            firm = firmService.createFirm(firm);
            user.setFirm(firm);

            // Sauvegarder l'utilisateur avec la référence au cabinet
            user = userRepository.save(user);
        }
        
        // SÉCURITÉ : ne PAS générer de JWT avant la vérification email
        // L'utilisateur doit d'abord vérifier son email via /verify-email
        return AuthResponse.builder()
                .token(null)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .message("Veuillez vérifier votre email avant de vous connecter")
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
        
        // SEC FIX SVC-05 : vérification explicite emailVerified et accountEnabled
        if (!user.isEmailVerified()) {
            throw new RuntimeException("Veuillez vérifier votre email avant de vous connecter");
        }
        if (!user.isAccountEnabled()) {
            throw new RuntimeException("Compte désactivé");
        }
        
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
        
        // SEC FIX : vérifier que le compte est toujours actif avant de renouveler le token
        if (!userDetails.isEnabled()) {
            throw new RuntimeException("Compte désactivé");
        }
        
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
