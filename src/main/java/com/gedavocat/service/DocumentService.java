package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des documents
 */
@Service
@RequiredArgsConstructor
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    
    @Value("${app.upload.dir}")
    private String uploadDir;
    
    /**
     * Récupère tous les documents d'un dossier
     */
    public List<Document> getDocumentsByCase(String caseId) {
        return documentRepository.findByCaseIdAndNotDeleted(caseId);
    }
    
    /**
     * Récupère les dernières versions des documents
     */
    public List<Document> getLatestVersions(String caseId) {
        return documentRepository.findLatestVersionsByCaseId(caseId);
    }
    
    /**
     * Récupère un document par ID
     */
    public Document getDocumentById(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document non trouvé"));
    }
    
    /**
     * Récupère les documents supprimés (corbeille)
     */
    public List<Document> getDeletedDocuments(String caseId) {
        return documentRepository.findDeletedByCaseId(caseId);
    }
    
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".odt", ".ods", ".odp", ".txt", ".csv", ".rtf",
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp",
            ".zip", ".rar", ".7z", ".eml", ".msg"
    );

    private static final java.util.Set<String> ALLOWED_MIMETYPES = java.util.Set.of(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "text/plain", "text/csv", "application/rtf",
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/tiff", "image/webp",
            "application/zip", "application/x-rar-compressed", "application/x-7z-compressed",
            "message/rfc822", "application/vnd.ms-outlook",
            "application/octet-stream"
    );

    /**
     * Upload un nouveau document
     */
    @Transactional
    public Document uploadDocument(String caseId, MultipartFile file, String userId, String userRole) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // SÉCURITÉ : validation du fichier
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("Nom de fichier invalide");
        }
        // Sanitize filename — remove path separators and null bytes
        originalFilename = originalFilename.replaceAll("[/\\\\\\x00]", "_");
        
        // Validate extension
        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex > 0) {
            fileExtension = originalFilename.substring(dotIndex).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new RuntimeException("Type de fichier non autorisé: " + fileExtension);
        }
        
        // Validate MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIMETYPES.contains(mimeType.toLowerCase())) {
            throw new RuntimeException("Type MIME non autorisé: " + mimeType);
        }
        
        // Créer le répertoire si nécessaire
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Générer un nom de fichier unique
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            
            // Sauvegarder le fichier
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Créer l'entité document
            Document document = new Document();
            document.setId(UUID.randomUUID().toString());
            document.setCaseEntity(caseEntity);
            document.setUploadedBy(user);
            document.setUploaderRole(userRole);
            document.setFilename(uniqueFilename);
            document.setOriginalName(originalFilename);
            document.setMimetype(file.getContentType());
            document.setFileSize(file.getSize());
            document.setPath(filePath.toString());
            document.setVersion(1);
            document.setIsLatest(true);
            document.setCreatedAt(LocalDateTime.now());
            
            Document saved = documentRepository.save(document);
            
            // Audit
            auditService.log("DOCUMENT_UPLOADED", "Document", saved.getId(), 
                "Upload du document: " + originalFilename, userId);
            
            return saved;
            
        } catch (java.nio.file.AccessDeniedException e) {
            throw new RuntimeException("Permissions insuffisantes pour écrire dans " + uploadDir 
                + ". Vérifiez les droits du répertoire (chown docavocat:docavocat " + uploadDir + ")");
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload du fichier: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }
    
    /**
     * Upload une nouvelle version d'un document
     */
    @Transactional
    public Document uploadNewVersion(String parentDocumentId, MultipartFile file, String userId) {
        Document parentDoc = getDocumentById(parentDocumentId);
        
        // Marquer l'ancienne version comme non-latest
        parentDoc.setIsLatest(false);
        documentRepository.save(parentDoc);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        try {
            Path uploadPath = Paths.get(uploadDir);
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            Document newVersion = new Document();
            newVersion.setId(UUID.randomUUID().toString());
            newVersion.setCaseEntity(parentDoc.getCaseEntity());
            newVersion.setUploadedBy(user);
            newVersion.setUploaderRole(user.getRole().name());
            newVersion.setFilename(uniqueFilename);
            newVersion.setOriginalName(originalFilename);
            newVersion.setMimetype(file.getContentType());
            newVersion.setFileSize(file.getSize());
            newVersion.setPath(filePath.toString());
            newVersion.setVersion(parentDoc.getVersion() + 1);
            newVersion.setParentDocument(parentDoc);
            newVersion.setIsLatest(true);
            newVersion.setCreatedAt(LocalDateTime.now());
            
            Document saved = documentRepository.save(newVersion);
            
            // Audit
            auditService.log("DOCUMENT_UPLOADED", "Document", saved.getId(), 
                "Nouvelle version (v" + saved.getVersion() + "): " + originalFilename, userId);
            
            return saved;
            
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload: " + e.getMessage());
        }
    }
    
    /**
     * Suppression logique (corbeille)
     */
    @Transactional
    public void softDeleteDocument(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        document.softDelete();
        documentRepository.save(document);
        
        // Audit
        auditService.log("DOCUMENT_DELETED", "Document", documentId, 
            "Suppression du document: " + document.getOriginalName(), userId);
    }
    
    /**
     * Restauration depuis la corbeille
     */
    @Transactional
    public void restoreDocument(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        document.restore();
        documentRepository.save(document);
        
        // Audit
        auditService.log("DOCUMENT_RESTORED", "Document", documentId, 
            "Restauration du document: " + document.getOriginalName(), userId);
    }
    
    /**
     * Suppression définitive
     */
    @Transactional
    public void permanentDeleteDocument(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        
        // Supprimer le fichier physique
        try {
            Path filePath = Paths.get(document.getPath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log l'erreur mais continue la suppression de la BDD
        }
        
        String filename = document.getOriginalName();
        documentRepository.delete(document);
        
        // Audit
        auditService.log("DOCUMENT_DELETED", "Document", documentId, 
            "Suppression définitive du document: " + filename, userId);
    }
    
    /**
     * Télécharger un document
     */
    public Path downloadDocument(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        
        // Audit
        auditService.log("DOCUMENT_DOWNLOADED", "Document", documentId, 
            "Téléchargement du document: " + document.getOriginalName(), userId);
        
        return Paths.get(document.getPath());
    }
    
    /**
     * Calcule la taille totale des documents d'un avocat
     */
    public long getTotalStorageSize(String lawyerId) {
        return documentRepository.calculateTotalSizeByLawyer(lawyerId);
    }
    
    /**
     * Récupère tous les documents d'un avocat
     */
    public List<Document> getAllDocumentsByUser(String lawyerId) {
        return documentRepository.findByLawyerIdWithCase(lawyerId);
    }
}