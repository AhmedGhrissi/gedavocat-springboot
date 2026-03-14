package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.Case.CaseStatus;
import com.gedavocat.model.Permission;
import com.gedavocat.model.Client;
import com.gedavocat.repository.AppointmentRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.CaseShareLinkRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.RpvaCommunicationRepository;
import com.gedavocat.repository.SignatureRepository;
import com.gedavocat.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Service de gestion des dossiers
 */
@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CaseService {
    
    private final CaseRepository caseRepository;
    private final PermissionRepository permissionRepository;
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    private final AppointmentRepository appointmentRepository;
    private final RpvaCommunicationRepository rpvaCommunicationRepository;
    private final CaseShareLinkRepository caseShareLinkRepository;
    private final SignatureRepository signatureRepository;
    private final DocumentRepository documentRepository;
    
    /**
     * Récupère les dossiers pour lesquels un collaborateur (lawyer) a une permission active
     */
    @Transactional(readOnly = true)
    public List<Case> getCasesByCollaborator(String collaboratorId) {
        List<Permission> perms = permissionRepository.findActiveByLawyerId(collaboratorId);
        // Collect case IDs while still inside the transaction/session to avoid touching proxies later
        List<String> caseIds = perms.stream()
                .map(Permission::getCaseEntity)
                .filter(Objects::nonNull)
                .map(Case::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        // Load full case entities (with client) from repository
        List<Case> cases = caseIds.stream()
                .map(id -> caseRepository.findByIdWithClient(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        // Force-initialize lawyer and client while we are still inside the transaction
        try {
            for (Case c : cases) {
                if (c.getLawyer() != null) {
                    c.getLawyer().getId();
                    c.getLawyer().getName();
                }
                if (c.getClient() != null) {
                    c.getClient().getId();
                    c.getClient().getName();
                }
            }
        } catch (Exception ignore) {}
        return cases;
    }

    /**
     * Vérifie si un collaborateur a accès en lecture à un dossier
     */
    public boolean hasCollaboratorAccess(String caseId, String collaboratorId) {
        return permissionRepository.hasReadAccess(caseId, collaboratorId);
    }
    
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
    @Transactional(readOnly = true)
    public Case getCaseById(String caseId) {
        Case c = caseRepository.findByIdWithClient(caseId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));
        // Force-initialize relations while inside transaction to avoid LazyInitializationException later
        try {
            if (c.getLawyer() != null) {
                c.getLawyer().getId();
                c.getLawyer().getName();
                c.getLawyer().getEmail();
            }
            if (c.getFirm() != null) {
                c.getFirm().getId();
                c.getFirm().getName();
            }
            if (c.getClient() != null) {
                c.getClient().getId();
                c.getClient().getName();
                if (c.getClient().getFirm() != null) {
                    c.getClient().getFirm().getId();
                }
            }
        } catch (Exception ignore) {}
        return c;
    }
    
    /**
     * SEC FIX M-12 : Récupère un dossier par ID avec vérification de propriété
     */
    @Transactional(readOnly = true)
    public Case getCaseById(String caseId, String lawyerId) {
        Case c = getCaseById(caseId);
        if (c.getLawyer() == null || !c.getLawyer().getId().equals(lawyerId)) {
            throw new SecurityException("Accès non autorisé à ce dossier");
        }
        return c;
    }
    
    /**
     * Récupère les dossiers d'un client
     */
    public List<Case> getCasesByClient(String clientId) {
        return caseRepository.findByClientIdWithLawyerAndClient(clientId);
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
    public Case createCase(Case caseEntity, String lawyerId, String clientId) {
        log.debug("Création d'un dossier pour l'avocat: {}", lawyerId);
        
        // Charger le client avec ses relations dans cette transaction
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client non trouvé"));
        
        // Vérifier que le client appartient à l'avocat
        if (client.getLawyer() == null || !client.getLawyer().getId().equals(lawyerId)) {
            throw new RuntimeException("Ce client ne vous appartient pas");
        }
        
        // Générer un nouvel ID si nécessaire
        if (caseEntity.getId() == null || caseEntity.getId().isEmpty()) {
            caseEntity.setId(UUID.randomUUID().toString());
        }
        
        // Générer une référence unique si non fournie
        if (caseEntity.getReference() == null || caseEntity.getReference().isEmpty()) {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String uniquePart = caseEntity.getId().substring(0, 5).toUpperCase();
            caseEntity.setReference("DOS-" + dateStr + "-" + uniquePart);
        }
        
        caseEntity.setLawyer(client.getLawyer());
        caseEntity.setClient(client);
        caseEntity.setFirm(client.getFirm()); // Hériter le firm_id du client
        caseEntity.setStatus(CaseStatus.OPEN);
        caseEntity.setCreatedAt(LocalDateTime.now());
        
        // Ensure caseType is set to a default if not provided to avoid DB non-null constraint
        if (caseEntity.getCaseType() == null) {
            caseEntity.setCaseType(Case.CaseType.AUTRE);
        }
        
        Case savedCase = caseRepository.save(caseEntity);
        
        log.info("Dossier créé: {} ({})", savedCase.getName(), savedCase.getId());
        
        // Audit
        try {
            auditService.log("CASE_CREATED", "Case", savedCase.getId(), 
                "Création du dossier: " + savedCase.getName(), lawyerId);
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de création du dossier: {}", e.getMessage());
            // Ne pas bloquer la création si l'audit échoue
        }
        
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
            if (newClient.getLawyer() == null || !newClient.getLawyer().getId().equals(lawyerId)) {
                throw new RuntimeException("Ce client ne vous appartient pas");
            }
            caseEntity.setClient(newClient);
            caseEntity.setFirm(newClient.getFirm()); // Hériter le firm_id du nouveau client
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
        documentRepository.clearParentReferencesByCaseId(caseId);
        documentRepository.deleteAllByCaseEntityId(caseId);
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