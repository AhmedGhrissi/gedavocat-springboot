package com.gedavocat.service;

import com.gedavocat.model.*;
import com.gedavocat.repository.DocumentShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service gérant le partage granulaire de documents avec les collaborateurs et huissiers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentShareService {

    private final DocumentShareRepository documentShareRepository;

    /**
     * Partage un document avec un rôle (collaborateur ou huissier).
     */
    @Transactional
    public DocumentShare shareDocument(Document document, Case caseEntity, User.UserRole targetRole, boolean canDownload) {
        // Vérifier si un partage existe déjà
        Optional<DocumentShare> existing = documentShareRepository.findByDocumentIdAndRole(document.getId(), targetRole);
        if (existing.isPresent()) {
            DocumentShare ds = existing.get();
            ds.setCanDownload(canDownload);
            return documentShareRepository.save(ds);
        }

        DocumentShare ds = new DocumentShare();
        ds.setDocument(document);
        ds.setCaseEntity(caseEntity);
        ds.setTargetRole(targetRole);
        ds.setCanDownload(canDownload);
        return documentShareRepository.save(ds);
    }

    /**
     * Retire le partage d'un document pour un rôle.
     */
    @Transactional
    public void unshareDocument(String documentId, User.UserRole targetRole) {
        documentShareRepository.deleteByDocumentIdAndRole(documentId, targetRole);
    }

    /**
     * Vérifie si un document est partagé avec un rôle donné.
     */
    @Transactional(readOnly = true)
    public boolean isShared(String documentId, User.UserRole role) {
        return documentShareRepository.isSharedWithRole(documentId, role);
    }

    /**
     * Retourne les IDs de documents partagés pour un dossier et un rôle.
     */
    @Transactional(readOnly = true)
    public Set<String> getSharedDocumentIds(String caseId, User.UserRole role) {
        return new HashSet<>(documentShareRepository.findDocumentIdsByCaseIdAndRole(caseId, role));
    }

    /**
     * Retourne une map documentId -> Set(targetRoles) pour l'affichage dans la vue du dossier.
     */
    @Transactional(readOnly = true)
    public Map<String, Set<String>> getShareMapForCase(String caseId) {
        List<DocumentShare> shares = documentShareRepository.findByCaseId(caseId);
        Map<String, Set<String>> map = new HashMap<>();
        for (DocumentShare ds : shares) {
            map.computeIfAbsent(ds.getDocument().getId(), k -> new HashSet<>())
               .add(ds.getTargetRole().name());
        }
        return map;
    }

    /**
     * Partage/départage en masse tous les documents d'un dossier pour un rôle.
     *
     * @param caseId      ID du dossier
     * @param documents   Liste des documents du dossier
     * @param targetRole  Rôle cible
     * @param share       true = partager, false = retirer
     */
    @Transactional
    public void bulkShare(String caseId, List<Document> documents, Case caseEntity, User.UserRole targetRole, boolean share) {
        if (share) {
            for (Document doc : documents) {
                shareDocument(doc, caseEntity, targetRole, false);
            }
            log.info("[DocShare] Tous les documents du dossier {} partagés avec {}", caseId, targetRole);
        } else {
            documentShareRepository.deleteByCaseId(caseId);
            log.info("[DocShare] Tous les partages du dossier {} retirés pour {}", caseId, targetRole);
        }
    }

    /**
     * Met à jour les partages pour un dossier en fonction des documents sélectionnés.
     *
     * @param caseId          ID du dossier
     * @param caseEntity      Entité Case
     * @param allDocuments    Tous les documents du dossier
     * @param selectedDocIds  IDs des documents sélectionnés pour partage
     * @param targetRole      Rôle cible
     */
    @Transactional
    public void updateShares(String caseId, Case caseEntity, List<Document> allDocuments,
                             Set<String> selectedDocIds, User.UserRole targetRole) {
        for (Document doc : allDocuments) {
            if (selectedDocIds.contains(doc.getId())) {
                shareDocument(doc, caseEntity, targetRole, false);
            } else {
                unshareDocument(doc.getId(), targetRole);
            }
        }
    }

    /**
     * Filtre les documents pour ne garder que ceux partagés avec le rôle.
     */
    @Transactional(readOnly = true)
    public List<Document> filterSharedDocuments(String caseId, List<Document> documents, User.UserRole role) {
        Set<String> sharedIds = getSharedDocumentIds(caseId, role);
        return documents.stream()
                .filter(doc -> sharedIds.contains(doc.getId()))
                .collect(Collectors.toList());
    }
}
