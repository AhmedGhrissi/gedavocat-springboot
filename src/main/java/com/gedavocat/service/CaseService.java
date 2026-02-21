package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.Case.CaseStatus;
import com.gedavocat.model.Client;
import com.gedavocat.repository.AppointmentRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.CaseShareLinkRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.RpvaCommunicationRepository;
import com.gedavocat.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des dossiers
 */
@Service
@RequiredArgsConstructor
public class CaseService {
    
    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    private final AppointmentRepository appointmentRepository;
    private final RpvaCommunicationRepository rpvaCommunicationRepository;
    private final CaseShareLinkRepository caseShareLinkRepository;
    private final SignatureRepository signatureRepository;
    
    /**
     * Récupère tous les dossiers d'un avocat
     */
    public List<Case> getCasesByLawyer(String lawyerId) {
        return caseRepository.findAllByLawyerIdWithClient(lawyerId);
    }
    
    /**
     * Récupère tous les dossiers accessibles (incluant permissions)
     */
    public List<Case> getAccessibleCases(String lawyerId) {
        return caseRepository.findAccessibleCases(lawyerId);
    }
    
    /**
     * Récupère un dossier par ID
     */
    public Case getCaseById(String caseId) {
        return caseRepository.findByIdWithClient(caseId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));
    }
    
    /**
     * Récupère les dossiers d'un client
     */
    public List<Case> getCasesByClient(String clientId) {
        return caseRepository.findByClientId(clientId);
    }
    
    /**
     * Récupère les dossiers par statut
     */
    public List<Case> getCasesByStatus(String lawyerId, CaseStatus status) {
        return caseRepository.findByLawyerIdAndStatusWithClient(lawyerId, status);
    }
    
    /**
     * Crée un nouveau dossier
     */
    @Transactional
    public Case createCase(Case caseEntity, String lawyerId) {
        System.out.println("=== DEBUG CaseService.createCase START ===");
        System.out.println("LawyerId: " + lawyerId);
        System.out.println("Case name: " + caseEntity.getName());
        System.out.println("Client ID: " + (caseEntity.getClient() != null ? caseEntity.getClient().getId() : "null"));
        
        Client client = clientRepository.findById(caseEntity.getClient().getId())
                .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        
        System.out.println("Client trouvé: " + client.getName());
        
        // Vérifier que le client appartient à l'avocat
        if (!client.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Ce client ne vous appartient pas");
        }
        
        // Générer un nouvel ID si nécessaire
        if (caseEntity.getId() == null || caseEntity.getId().isEmpty()) {
            caseEntity.setId(UUID.randomUUID().toString());
        }
        
        caseEntity.setLawyer(client.getLawyer());
        caseEntity.setClient(client);
        caseEntity.setStatus(CaseStatus.OPEN);
        caseEntity.setCreatedAt(LocalDateTime.now());
        
        System.out.println("Avant save - Case ID: " + caseEntity.getId());
        System.out.println("Avant save - Status: " + caseEntity.getStatus());
        
        Case savedCase = caseRepository.save(caseEntity);
        
        System.out.println("Après save - Case ID: " + savedCase.getId());
        System.out.println("=== Dossier enregistré en base de données ===");
        
        // Audit
        try {
            auditService.log("CASE_CREATED", "Case", savedCase.getId(), 
                "Création du dossier: " + savedCase.getName(), lawyerId);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'audit: " + e.getMessage());
            // Ne pas bloquer la création si l'audit échoue
        }
        
        System.out.println("=== DEBUG CaseService.createCase END ===");
        return savedCase;
    }
    
    /**
     * Met à jour un dossier
     */
    @Transactional
    public Case updateCase(String caseId, Case updatedCase, String lawyerId) {
        Case caseEntity = getCaseById(caseId);
        
        // Vérifier l'accès
        if (!caseEntity.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce dossier");
        }
        
        caseEntity.setName(updatedCase.getName());
        caseEntity.setDescription(updatedCase.getDescription());
        
        // Mettre à jour le client si changé
        if (updatedCase.getClient() != null) {
            Client newClient = clientRepository.findById(updatedCase.getClient().getId())
                    .orElseThrow(() -> new RuntimeException("Client non trouvé"));
            if (!newClient.getLawyer().getId().equals(lawyerId)) {
                throw new RuntimeException("Ce client ne vous appartient pas");
            }
            caseEntity.setClient(newClient);
        }
        
        // Mettre à jour le statut seulement si fourni
        if (updatedCase.getStatus() != null) {
            caseEntity.setStatus(updatedCase.getStatus());
        }
        
        caseEntity.setUpdatedAt(LocalDateTime.now());
        
        Case saved = caseRepository.save(caseEntity);
        
        // Audit
        auditService.log("CASE_UPDATED", "Case", saved.getId(), 
            "Modification du dossier: " + saved.getName(), lawyerId);
        
        return saved;
    }
    
    /**
     * Ferme un dossier
     */
    @Transactional
    public Case closeCase(String caseId, String lawyerId) {
        Case caseEntity = getCaseById(caseId);
        
        if (!caseEntity.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce dossier");
        }
        
        caseEntity.setStatus(CaseStatus.CLOSED);
        caseEntity.setUpdatedAt(LocalDateTime.now());
        
        Case saved = caseRepository.save(caseEntity);
        
        // Audit
        auditService.log("CASE_CLOSED", "Case", saved.getId(), 
            "Fermeture du dossier: " + saved.getName(), lawyerId);
        
        return saved;
    }
    
    /**
     * Archive un dossier
     */
    @Transactional
    public Case archiveCase(String caseId, String lawyerId) {
        Case caseEntity = getCaseById(caseId);
        
        if (!caseEntity.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce dossier");
        }
        
        caseEntity.setStatus(CaseStatus.ARCHIVED);
        caseEntity.setUpdatedAt(LocalDateTime.now());
        
        Case saved = caseRepository.save(caseEntity);
        
        // Audit
        auditService.log("CASE_ARCHIVED", "Case", saved.getId(), 
            "Archivage du dossier: " + saved.getName(), lawyerId);
        
        return saved;
    }
    
    /**
     * Supprime un dossier
     */
    @Transactional
    public void deleteCase(String caseId, String lawyerId) {
        Case caseEntity = getCaseById(caseId);
        
        if (!caseEntity.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Vous n'avez pas accès à ce dossier");
        }
        
        String caseName = caseEntity.getName();
        // Supprimer toutes les références FK avant suppression du dossier
        signatureRepository.deleteAllByCaseId(caseId);
        caseShareLinkRepository.deleteAllByCaseId(caseId);
        rpvaCommunicationRepository.deleteByCaseId(caseId);
        appointmentRepository.clearRelatedCaseByCaseId(caseId);
        caseRepository.delete(caseEntity);
        
        // Audit
        auditService.log("CASE_DELETED", "Case", caseId, 
            "Suppression du dossier: " + caseName, lawyerId);
    }
    
    /**
     * Recherche de dossiers
     */
    public List<Case> searchCases(String lawyerId, String search) {
        return caseRepository.searchByLawyerAndNameOrDescriptionWithClient(lawyerId, search);
    }
    
    /**
     * Statistiques
     */
    public long countByStatus(String lawyerId, CaseStatus status) {
        return caseRepository.countByLawyerIdAndStatus(lawyerId, status);
    }
}
