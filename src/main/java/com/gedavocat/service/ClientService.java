package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.AppointmentRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des clients
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AppointmentRepository appointmentRepository;
    private final CaseRepository caseRepository;
    private final LABFTService labftService;
    
    /**
     * Récupère tous les clients d'un avocat
     */
    public List<Client> getClientsByLawyer(String lawyerId) {
        return clientRepository.findByLawyerId(lawyerId);
    }

    /**
     * Récupère tous les clients (usage admin)
     */
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    /**
     * Trouve le Client associé à un User CLIENT
     */
    public java.util.Optional<Client> findByClientUser(String userId) {
        return clientRepository.findByClientUserId(userId);
    }
    
    /**
     * Récupère un client par ID avec vérification ownership
     * SEC-IDOR FIX : exige lawyerId pour vérifier l'appartenance
     */
    @Transactional(readOnly = true)
    public Client getClientById(String clientId, String lawyerId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        // SEC-IDOR FIX : vérifier ownership
        if (lawyerId != null && client.getLawyer() != null 
                && !client.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce client");
        }
        // Initialiser les collections lazy pour éviter LazyInitializationException
        client.getCases().size(); // Force le chargement des cases
        return client;
    }

    /**
     * Récupère un client par ID (usage interne/admin uniquement)
     */
    @Transactional(readOnly = true)
    public Client getClientById(String clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        client.getCases().size();
        return client;
    }
    
    /**
     * Crée un nouveau client
     */
    @Transactional
    public Client createClient(Client client, String lawyerId) {
        log.debug("Création d'un client pour l'avocat: {}", lawyerId);
        
        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new RuntimeException("Avocat non trouvé"));
        
        // Si l'avocat a un cabinet, le transmettre au client (sinon laisser null)
        
        // Vérifier si l'email existe déjà pour cet avocat
        if (clientRepository.existsByLawyerIdAndEmail(lawyerId, client.getEmail())) {
            throw new RuntimeException("Un client avec cet email existe déjà");
        }
        
        // Vérifier la limite de clients selon l'abonnement
        long clientCount = clientRepository.countByLawyerId(lawyerId);
        if (lawyer.getMaxClients() != null && clientCount >= lawyer.getMaxClients()) {
            throw new RuntimeException("Limite de clients atteinte pour votre abonnement");
        }
        
        // Générer un nouvel ID si nécessaire
        if (client.getId() == null || client.getId().isEmpty()) {
            client.setId(UUID.randomUUID().toString());
        }
        
        client.setLawyer(lawyer);
        client.setFirm(lawyer.getFirm()); // Hériter du cabinet de l'avocat
        client.setCreatedAt(LocalDateTime.now());
        
        Client savedClient = clientRepository.save(client);
        
        log.info("Client créé: {} ({})", savedClient.getName(), savedClient.getId());
        
        // Contrôles LAB-FT automatiques (conformité ACPR)
        try {
            labftService.performAutoClientChecks(savedClient);
        } catch (Exception e) {
            log.error("Erreur lors des contrôles LAB-FT du client: {}", e.getMessage());
        }
        
        // Audit
        try {
            auditService.log("CLIENT_CREATED", "Client", savedClient.getId(), 
                "Création du client: " + savedClient.getName(), lawyerId);
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de création du client: {}", e.getMessage());
            // Ne pas bloquer la création si l'audit échoue
        }
        
        return savedClient;
    }
    
    /**
     * Met à jour un client
     */
    @Transactional
    public Client updateClient(String clientId, Client updatedClient, String lawyerId) {
        log.debug("Mise à jour du client: {}", clientId);
        
        Client client = getClientById(clientId);
        
        // Vérifier que le client appartient à l'avocat
        if (!client.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce client");
        }
        
        client.setName(updatedClient.getName());
        client.setEmail(updatedClient.getEmail());
        client.setPhone(updatedClient.getPhone());
        client.setAddress(updatedClient.getAddress());
        client.setAccessEndsAt(updatedClient.getAccessEndsAt());
        client.setUpdatedAt(LocalDateTime.now());
        
        Client saved = clientRepository.save(client);
        
        log.info("Client mis à jour: {} ({})", saved.getName(), saved.getId());
        
        // Audit
        try {
            auditService.log("CLIENT_UPDATED", "Client", saved.getId(), 
                "Modification du client: " + saved.getName(), lawyerId);
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de mise à jour du client: {}", e.getMessage());
            // Ne pas bloquer la mise à jour si l'audit échoue
        }
        
        return saved;
    }
    
    /**
     * Supprimer un client par ID seul (interne / admin / tests).
     * Les dossiers liés sont archivés (pas supprimés).
     */
    @Transactional
    public void deleteClient(String clientId) {
        Client client = getClientById(clientId);
        // Archiver les dossiers liés au lieu de les supprimer
        List<Case> cases = caseRepository.findByClientId(clientId);
        for (Case c : cases) {
            c.setStatus(Case.CaseStatus.ARCHIVED);
            c.setUpdatedAt(LocalDateTime.now());
            caseRepository.save(c);
        }
        // Supprimer les références restantes dans les rendez-vous
        appointmentRepository.clearClientByClientId(clientId);
        clientRepository.delete(client);
    }

    /**
     * Supprimer un client avec vérification d'appartenance à l'avocat.
     * Les dossiers liés sont archivés (pas supprimés).
     */
    @Transactional
    public void deleteClient(String clientId, String lawyerId) {
        Client client = getClientById(clientId);

        if (!client.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce client");
        }

        String clientName = client.getName();
        // Archiver les dossiers liés au lieu de les supprimer
        List<Case> cases = caseRepository.findByClientId(clientId);
        for (Case c : cases) {
            c.setStatus(Case.CaseStatus.ARCHIVED);
            c.setUpdatedAt(LocalDateTime.now());
            caseRepository.save(c);
        }
        // Supprimer les références restantes dans les rendez-vous
        appointmentRepository.clearClientByClientId(clientId);
        clientRepository.delete(client);

        auditService.log("CLIENT_DELETED", "Client", clientId,
            "Suppression du client: " + clientName + " (" + cases.size() + " dossier(s) archivé(s))", lawyerId);
    }
    
    /**
     * Recherche des clients
     */
    public List<Client> searchClients(String lawyerId, String search) {
        return clientRepository.searchByLawyerAndNameOrEmail(lawyerId, search);
    }
    
    /**
     * Compte le nombre de clients d'un avocat
     */
    public long countClientsByLawyer(String lawyerId) {
        return clientRepository.countByLawyerId(lawyerId);
    }
}
