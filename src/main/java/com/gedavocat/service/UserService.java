package com.gedavocat.service;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Supprime un utilisateur
     */
    @Transactional
    public void deleteUser(String id) {
        userRepository.deleteById(id);
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
     * Active ou désactive un utilisateur
     */
    @Transactional
    public User toggleUserStatus(String id, boolean enabled) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // À implémenter : ajouter un champ 'enabled' dans User si nécessaire
            return userRepository.save(user);
        }
        throw new RuntimeException("Utilisateur non trouvé");
    }

    /**
     * Vérifie si un email existe déjà
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
