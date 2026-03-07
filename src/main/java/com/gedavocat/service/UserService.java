package com.gedavocat.service;

import com.gedavocat.model.User;
import com.gedavocat.model.User.UserRole;
import com.gedavocat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service pour la gestion des utilisateurs (admin)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    private final AppointmentRepository appointmentRepository;
    private final CaseShareLinkRepository caseShareLinkRepository;
    private final PaymentRepository paymentRepository;
    private final SignatureRepository signatureRepository;
    private final RpvaCommunicationRepository rpvaCommunicationRepository;
    private final ClientRepository clientRepository;

    /**
     * Récupère tous les utilisateurs
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Récupère un utilisateur par son ID
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    /**
     * Récupère un utilisateur par son email
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Met à jour un utilisateur
     */
    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Supprime un utilisateur et toutes ses données liées.
     * Nettoie les FK non cascadées avant de supprimer.
     */
    @Transactional
    public void deleteUser(String id) {
        log.info("[Admin] Suppression de l'utilisateur {}", id);
        try {
            // 1. Clean up FK references that are NOT cascaded from User
            permissionRepository.deleteByLawyerId(id);
            permissionRepository.deleteByGrantedById(id);
            appointmentRepository.deleteByLawyerId(id);
            caseShareLinkRepository.deleteByOwnerId(id);
            paymentRepository.deleteByUserId(id);
            signatureRepository.deleteByRequestedById(id);
            rpvaCommunicationRepository.deleteBySentById(id);
            // Clear nullable FK (Client.clientUser)
            clientRepository.clearClientUserById(id);
        } catch (Exception e) {
            log.warn("[Admin] Erreur nettoyage des données liées pour {} : {}", id, e.getMessage());
        }
        // 2. Delete user (cascades to Case, Client, AuditLog)
        userRepository.deleteById(id);
        log.info("[Admin] Utilisateur {} supprimé avec succès", id);
    }

    /**
     * Compte le nombre d'utilisateurs par rôle
     */
    @Transactional(readOnly = true)
    public long countByRole(String role) {
        return userRepository.findAll().stream()
            .filter(u -> role.equals(u.getRole() != null ? u.getRole().name() : ""))
            .count();
    }

    /**
     * Recherche des utilisateurs par nom ou email
     */
    @Transactional(readOnly = true)
    public List<User> searchUsers(String keyword) {
        return userRepository.findByFirstNameContainingOrLastNameContainingOrEmailContaining(keyword);
    }

    /**
     * Active ou bloque un utilisateur (accountEnabled)
     */
    @Transactional
    public User toggleUserStatus(String id, boolean enabled) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setAccountEnabled(enabled);
        return userRepository.save(user);
    }

    /**
     * Bloque ou débloque un utilisateur
     */
    @Transactional
    public User blockUser(String id, boolean block) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + id));
        user.setAccountEnabled(!block);
        log.info("Utilisateur {} : accountEnabled={}", id, !block);
        return userRepository.save(user);
    }

    /**
     * Filtre les utilisateurs (admin) par recherche, rôle, statut
     */
    @Transactional(readOnly = true)
    public List<User> findWithFilters(String search, String role, String status) {
        UserRole userRole = (role != null && !role.isBlank()) ? UserRole.valueOf(role) : null;
        Boolean accountEnabled = null;
        if ("active".equals(status))   accountEnabled = true;
        if ("inactive".equals(status)) accountEnabled = false;
        String searchParam = (search != null && !search.isBlank()) ? search : null;
        return userRepository.findWithFilters(searchParam, userRole, accountEnabled);
    }

    /**
     * Met à jour les informations d'un utilisateur depuis l'interface admin
     */
    @Transactional
    public User updateUserAdmin(String id, String firstName, String lastName, String email,
                                String role, boolean accountEnabled) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé : " + id));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName((firstName + " " + lastName).trim());
        user.setEmail(email);
        user.setRole(UserRole.valueOf(role));
        user.setAccountEnabled(accountEnabled);
        log.info("Admin mise à jour utilisateur {} : {} {}, rôle={}, enabled={}", id, firstName, lastName, role, accountEnabled);
        return userRepository.save(user);
    }

    /**
     * Vérifie si un email existe déjà
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    /**
     * Crée un nouvel utilisateur (admin)
     */
    @Transactional
    public User createUser(String firstName, String lastName, String email, String password, String role) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Un utilisateur avec cet email existe déjà");
        }

        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName((firstName + " " + lastName).trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.valueOf(role));
        user.setEmailVerified(true);
        user.setAccountEnabled(true);
        user.setTermsAcceptedAt(LocalDateTime.now());
        user.setGdprConsentAt(LocalDateTime.now());

        // Pour les avocats (LAWYER et AVOCAT_ADMIN), définir un abonnement par défaut
        if (UserRole.valueOf(role) == UserRole.LAWYER || UserRole.valueOf(role) == UserRole.AVOCAT_ADMIN) {
            user.setSubscriptionPlan(User.SubscriptionPlan.ESSENTIEL);
            user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
            user.setMaxClients(10);
            user.setSubscriptionStartDate(LocalDateTime.now());
        }
        
        // ADMIN n'a pas de cabinet (firm_id reste NULL)
        // Les autres rôles (LAWYER, CLIENT, etc.) devront avoir un firm_id assigné ailleurs si nécessaire
        user.setFirm(null); // Explicitement NULL pour tous les utilisateurs créés par l'admin

        log.info("Création utilisateur par admin: {} {} ({}), rôle: {}, firm_id: {}", 
                 firstName, lastName, email, role, user.getFirm() != null ? user.getFirm().getId() : "NULL");
        return userRepository.save(user);
    }
}
