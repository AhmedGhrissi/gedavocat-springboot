package com.gedavocat.service;

import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des clients
 */
@Service
@RequiredArgsConstructor
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    
    /**
     * Récupère tous les clients d'un avocat
     */
    public List<Client> getClientsByLawyer(String lawyerId) {
        return clientRepository.findByLawyerId(lawyerId);
    }

    /**
     * Trouve le Client associé à un User CLIENT
     */
    public java.util.Optional<Client> findByClientUser(String userId) {
        return clientRepository.findByClientUserId(userId);
    }
    
    /**
     * Récupère un client par ID
     */
    @Transactional(readOnly = true)
    public Client getClientById(String clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        // Initialiser les collections lazy pour éviter LazyInitializationException
        client.getCases().size(); // Force le chargement des cases
        return client;
    }
    
    /**
     * Crée un nouveau client
     */
    @Transactional
    public Client createClient(Client client, String lawyerId) {
        System.out.println("=== DEBUG ClientService.createClient START ===");
        System.out.println("LawyerId: " + lawyerId);
        System.out.println("Client name: " + client.getName());
        System.out.println("Client email: " + client.getEmail());
        
        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new RuntimeException("Avocat non trouvé"));
        
        System.out.println("Avocat trouvé: " + lawyer.getEmail());
        
        // Vérifier si l'email existe déjà pour cet avocat
        if (clientRepository.existsByLawyerIdAndEmail(lawyerId, client.getEmail())) {
            throw new RuntimeException("Un client avec cet email existe déjà");
        }
        
        // Vérifier la limite de clients selon l'abonnement
        long clientCount = clientRepository.countByLawyerId(lawyerId);
        System.out.println("Nombre de clients existants: " + clientCount);
        if (lawyer.getMaxClients() != null && clientCount >= lawyer.getMaxClients()) {
            throw new RuntimeException("Limite de clients atteinte pour votre abonnement");
        }
        
        // Générer un nouvel ID si nécessaire
        if (client.getId() == null || client.getId().isEmpty()) {
            client.setId(UUID.randomUUID().toString());
        }
        
        client.setLawyer(lawyer);
        client.setCreatedAt(LocalDateTime.now());
        
        System.out.println("Avant save - Client ID: " + client.getId());
        
        Client savedClient = clientRepository.save(client);
        
        System.out.println("Après save - Client ID: " + savedClient.getId());
        System.out.println("=== Client enregistré en base de données ===");
        
        // Audit
        try {
            auditService.log("CLIENT_CREATED", "Client", savedClient.getId(), 
                "Création du client: " + savedClient.getName(), lawyerId);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'audit: " + e.getMessage());
            // Ne pas bloquer la création si l'audit échoue
        }
        
        System.out.println("=== DEBUG ClientService.createClient END ===");
        return savedClient;
    }
    
    /**
     * Met à jour un client
     */
    @Transactional
    public Client updateClient(String clientId, Client updatedClient, String lawyerId) {
        System.out.println("=== DEBUG ClientService.updateClient START ===");
        System.out.println("ClientId: " + clientId);
        System.out.println("Updated name: " + updatedClient.getName());
        
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
        
        System.out.println("Avant save - Client ID: " + client.getId());
        
        Client saved = clientRepository.save(client);
        
        System.out.println("Après save - Client ID: " + saved.getId());
        System.out.println("=== Client mis à jour en base de données ===");
        
        // Audit
        try {
            auditService.log("CLIENT_UPDATED", "Client", saved.getId(), 
                "Modification du client: " + saved.getName(), lawyerId);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'audit: " + e.getMessage());
            // Ne pas bloquer la mise à jour si l'audit échoue
        }
        
        System.out.println("=== DEBUG ClientService.updateClient END ===");
        return saved;
    }
    
    /**
     * Supprimer un client par ID seul (interne / admin / tests)
     */
    @Transactional
    public void deleteClient(String clientId) {
        Client client = getClientById(clientId);
        clientRepository.delete(client);
    }

    /**
     * Supprimer un client avec vérification d'appartenance à l'avocat
     */
    @Transactional
    public void deleteClient(String clientId, String lawyerId) {
        Client client = getClientById(clientId);

        if (!client.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce client");
        }

        String clientName = client.getName();
        clientRepository.delete(client);

        auditService.log("CLIENT_DELETED", "Client", clientId,
            "Suppression du client: " + clientName, lawyerId);
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
