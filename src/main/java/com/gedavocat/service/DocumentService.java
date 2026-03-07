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
@SuppressWarnings("null")
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
     * Récupère un document par ID (avec Case + Client chargés pour éviter LazyInitializationException)
     */
    @Transactional(readOnly = true)
    public Document getDocumentById(String documentId) {
        return documentRepository.findByIdWithCaseAndClient(documentId)
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
            "message/rfc822", "application/vnd.ms-outlook"
            // SEC-14 FIX : application/octet-stream retiré — type générique trop permissif
    );

    /**
     * Upload un nouveau document
     */
    @Transactional
    public Document uploadDocument(String caseId, MultipartFile file, String userId, String userRole) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));
        
        // SEC-IDOR FIX : vérifier que l'utilisateur est propriétaire du dossier
        if (caseEntity.getLawyer() == null || !caseEntity.getLawyer().getId().equals(userId)) {
            throw new SecurityException("Accès non autorisé à ce dossier");
        }
        
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
        
        // SEC FIX H-11 : validation des magic bytes (premiers octets) pour vérifier le contenu réel du fichier
        try {
            byte[] header = new byte[Math.min(8, (int) file.getSize())];
            file.getInputStream().read(header);
            if (!validateMagicBytes(header, fileExtension)) {
                throw new RuntimeException("Le contenu du fichier ne correspond pas à son extension");
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la validation du fichier", e);
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
            Path filePath = uploadPath.resolve(uniqueFilename).normalize();
            // SECURITE: Vérification path traversal
            if (!filePath.startsWith(uploadPath.normalize())) {
                throw new SecurityException("Path traversal détecté");
            }
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
            throw new RuntimeException("Erreur de permissions lors de l'upload du fichier");
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload du fichier: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }
    
    /**
     * Upload une nouvelle version d'un document
     * SEC-IDOR FIX SVC-02 : vérification ownership avant upload nouvelle version
     */
    @Transactional
    public Document uploadNewVersion(String parentDocumentId, MultipartFile file, String userId) {
        Document parentDoc = getDocumentById(parentDocumentId);
        
        // SÉCURITÉ SVC-02 : vérifier que l'utilisateur est propriétaire du document
        verifyDocumentOwnership(parentDoc, userId);
        
        // Marquer l'ancienne version comme non-latest
        parentDoc.setIsLatest(false);
        documentRepository.save(parentDoc);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        try {
            Path uploadPath = Paths.get(uploadDir);
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new RuntimeException("Nom de fichier invalide");
            }
            // SECURITE: Sanitize filename — remove path separators and null bytes
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
            
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename).normalize();
            // SECURITE: Vérification path traversal
            if (!filePath.startsWith(uploadPath.normalize())) {
                throw new SecurityException("Path traversal détecté");
            }
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
        // SEC-IDOR FIX : vérifier ownership
        verifyDocumentOwnership(document, userId);
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
        // SEC-IDOR FIX : vérifier ownership
        verifyDocumentOwnership(document, userId);
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
        // SEC-IDOR FIX : vérifier ownership
        verifyDocumentOwnership(document, userId);
        
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
        // SEC-IDOR FIX : vérifier ownership
        verifyDocumentOwnership(document, userId);
        
        // Audit
        auditService.log("DOCUMENT_DOWNLOADED", "Document", documentId, 
            "Téléchargement du document: " + document.getOriginalName(), userId);
        
        return Paths.get(document.getPath());
    }
    
    /**
     * SEC-IDOR FIX : Vérifie que l'utilisateur est propriétaire du document via le dossier
     */
    private void verifyDocumentOwnership(Document document, String userId) {
        if (document.getCaseEntity() == null || document.getCaseEntity().getLawyer() == null
                || !document.getCaseEntity().getLawyer().getId().equals(userId)) {
            throw new SecurityException("Accès non autorisé à ce document");
        }
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
    
    /**
     * SEC FIX H-11 : Validation des magic bytes pour les formats courants.
     * Retourne true si les octets d'en-tête correspondent à l'extension déclarée,
     * ou si l'extension n'a pas de signature connue (on laisse passer les formats texte, etc.)
     */
    private boolean validateMagicBytes(byte[] header, String extension) {
        if (header == null || header.length < 4) return false;
        return switch (extension.toLowerCase()) {
            case ".pdf" -> header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46; // %PDF
            case ".png" -> header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47; // .PNG
            case ".jpg", ".jpeg" -> header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
            case ".gif" -> header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46; // GIF
            case ".zip", ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp" ->
                header[0] == 0x50 && header[1] == 0x4B; // PK (ZIP-based)
            case ".rar" -> header[0] == 0x52 && header[1] == 0x61 && header[2] == 0x72; // Rar
            case ".7z" -> header[0] == 0x37 && header[1] == 0x7A && header[2] == (byte) 0xBC && header[3] == (byte) 0xAF;
            case ".bmp" -> header[0] == 0x42 && header[1] == 0x4D; // BM
            case ".tiff" -> (header[0] == 0x49 && header[1] == 0x49) || (header[0] == 0x4D && header[1] == 0x4D); // II or MM
            case ".doc", ".xls", ".ppt", ".msg" ->
                header[0] == (byte) 0xD0 && header[1] == (byte) 0xCF && header[2] == 0x11 && header[3] == (byte) 0xE0; // OLE2
            // Formats texte : .txt, .csv, .rtf, .eml, .webp — pas de magic bytes fiables ou acceptés tels quels
            default -> true;
        };
    }
}