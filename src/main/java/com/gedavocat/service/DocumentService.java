package com.gedavocat.service;

import com.gedavocat.model.Case;
import com.gedavocat.model.Document;
import com.gedavocat.model.User;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.DocumentRepository;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.crypto.SecureCryptographyService;
import com.gedavocat.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service de gestion des documents.
 * Stockage : MinIO S3 (bucket docavocat-documents).
 * Rétrocompatibilité : si document.path commence par "/" → lecture disque local (anciens docs).
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String FILE_ENCRYPTION_KEY_ID = "data_encryption_key";
    static final String BUCKET_DOCUMENTS = "docavocat-documents";

    private final DocumentRepository documentRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecureCryptographyService cryptographyService;
    private final StorageService storageService;

    @Value("${security.file-encryption.enabled:true}")
    private boolean fileEncryptionEnabled;

    // Conservé uniquement pour la rétrocompatibilité lecture disque (anciens documents)
    @Value("${app.upload.dir:./uploads/documents}")
    private String legacyUploadDir;

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
    );

    /**
     * Upload un nouveau document → stocké dans MinIO (chiffré AES-256-GCM si activé).
     */
    @Transactional
    public Document uploadDocument(String caseId, MultipartFile file, String userId, String userRole) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Dossier non trouvé"));

        // SEC-IDOR FIX : vérifier que l'utilisateur a accès au dossier
        // Clients: access already verified in ClientPortalController; skip lawyer check
        // Collaborators (LAWYER_SECONDARY) and Huissiers: access already verified in their portal controllers
        if (!"CLIENT".equals(userRole) && !"COLLABORATOR".equals(userRole) && !"HUISSIER".equals(userRole)) {
            if (caseEntity.getLawyer() == null || !caseEntity.getLawyer().getId().equals(userId)) {
                throw new SecurityException("Accès non autorisé à ce dossier");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String originalFilename = validateAndSanitizeFile(file);
        String fileExtension = getExtension(originalFilename);
        String mimeType = file.getContentType();

        // SEC FIX H-11 / F-18 : validation magic bytes
        try {
            byte[] header;
            try (java.io.InputStream magicStream = file.getInputStream()) {
                header = magicStream.readNBytes(8);
            }
            if (!validateMagicBytes(header, fileExtension)) {
                throw new RuntimeException("Le contenu du fichier ne correspond pas à son extension");
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la validation du fichier", e);
        }

        try {
            byte[] fileBytes = file.getBytes();
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // SEC-HARDENED : chiffrement at-rest AES-256-GCM avant stockage MinIO
            boolean encrypted = false;
            if (fileEncryptionEnabled) {
                try {
                    fileBytes = cryptographyService.encryptBytes(fileBytes, FILE_ENCRYPTION_KEY_ID);
                    encrypted = true;
                    log.info("Document chiffré at-rest: {}", uniqueFilename);
                } catch (Exception e) {
                    log.error("Échec chiffrement document {} — stocké en clair", uniqueFilename, e);
                }
            }

            // Stocker dans MinIO — la clé MinIO est uniqueFilename
            storageService.storeBytes(BUCKET_DOCUMENTS, uniqueFilename, fileBytes,
                    mimeType != null ? mimeType : "application/octet-stream");

            Document document = new Document();
            document.setId(UUID.randomUUID().toString());
            document.setCaseEntity(caseEntity);
            document.setUploadedBy(user);
            document.setUploaderRole(userRole);
            document.setFilename(uniqueFilename);
            document.setOriginalName(originalFilename);
            document.setMimetype(mimeType);
            document.setFileSize(file.getSize());
            // path = clé MinIO (pas de "/" au début → nouveau format)
            document.setPath(uniqueFilename);
            document.setVersion(1);
            document.setIsLatest(true);
            document.setEncrypted(encrypted);
            document.setCreatedAt(LocalDateTime.now());

            Document saved = documentRepository.save(document);
            auditService.log("DOCUMENT_UPLOADED", "Document", saved.getId(),
                "Upload du document: " + originalFilename + (encrypted ? " [chiffré]" : " [non chiffré]"), userId);

            return saved;
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload du fichier: " + e.getMessage(), e);
        }
    }

    /**
     * Upload une nouvelle version d'un document.
     */
    @Transactional
    public Document uploadNewVersion(String parentDocumentId, MultipartFile file, String userId) {
        Document parentDoc = getDocumentById(parentDocumentId);
        verifyDocumentOwnership(parentDoc, userId);

        parentDoc.setIsLatest(false);
        documentRepository.save(parentDoc);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        String originalFilename = validateAndSanitizeFile(file);
        String fileExtension = getExtension(originalFilename);
        String mimeType = file.getContentType();

        // SEC FIX F-18 : validation magic bytes
        try {
            byte[] header;
            try (java.io.InputStream magicStream = file.getInputStream()) {
                header = magicStream.readNBytes(8);
            }
            if (!validateMagicBytes(header, fileExtension)) {
                throw new RuntimeException("Le contenu du fichier ne correspond pas à son extension");
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la validation du fichier", e);
        }

        try {
            byte[] fileBytes = file.getBytes();
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            boolean encrypted = false;
            if (fileEncryptionEnabled) {
                try {
                    fileBytes = cryptographyService.encryptBytes(fileBytes, FILE_ENCRYPTION_KEY_ID);
                    encrypted = true;
                } catch (Exception e) {
                    log.error("Échec chiffrement [uploadNewVersion] {} — stocké en clair", uniqueFilename, e);
                }
            }

            storageService.storeBytes(BUCKET_DOCUMENTS, uniqueFilename, fileBytes,
                    mimeType != null ? mimeType : "application/octet-stream");

            Document newVersion = new Document();
            newVersion.setId(UUID.randomUUID().toString());
            newVersion.setCaseEntity(parentDoc.getCaseEntity());
            newVersion.setUploadedBy(user);
            newVersion.setUploaderRole(user.getRole().name());
            newVersion.setFilename(uniqueFilename);
            newVersion.setOriginalName(originalFilename);
            newVersion.setMimetype(mimeType);
            newVersion.setFileSize(file.getSize());
            newVersion.setPath(uniqueFilename);
            newVersion.setVersion(parentDoc.getVersion() + 1);
            newVersion.setParentDocument(parentDoc);
            newVersion.setIsLatest(true);
            newVersion.setEncrypted(encrypted);
            newVersion.setCreatedAt(LocalDateTime.now());

            Document saved = documentRepository.save(newVersion);
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
        verifyDocumentOwnership(document, userId);
        document.softDelete();
        documentRepository.save(document);
        auditService.log("DOCUMENT_DELETED", "Document", documentId,
            "Suppression du document: " + document.getOriginalName(), userId);
    }

    /**
     * Restauration depuis la corbeille
     */
    @Transactional
    public void restoreDocument(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        verifyDocumentOwnership(document, userId);
        document.restore();
        documentRepository.save(document);
        auditService.log("DOCUMENT_RESTORED", "Document", documentId,
            "Restauration du document: " + document.getOriginalName(), userId);
    }

    /**
     * Suppression définitive — supprime aussi l'objet MinIO.
     */
    @Transactional
    public void permanentDeleteDocument(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        verifyDocumentOwnership(document, userId);

        // Supprimer le support physique (MinIO ou disque legacy)
        try {
            String path = document.getPath();
            if (path != null) {
                if (isLegacyPath(path)) {
                    Files.deleteIfExists(Paths.get(path));
                } else {
                    storageService.delete(BUCKET_DOCUMENTS, path);
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de supprimer le fichier physique du document {} : {}", documentId, e.getMessage());
        }

        String filename = document.getOriginalName();
        documentRepository.delete(document);
        auditService.log("DOCUMENT_DELETED", "Document", documentId,
            "Suppression définitive du document: " + filename, userId);
    }

    /**
     * Télécharger un document — retourne les bytes déchiffrés.
     * Gère la rétrocompatibilité disque local (anciens documents).
     */
    public byte[] downloadDocument(String documentId, String userId) {
        return getDecryptedFileContent(documentId, userId);
    }

    /**
     * Récupère le contenu déchiffré d'un document.
     * - Nouveau format (MinIO) : path = clé MinIO (ex: "uuid.pdf")
     * - Ancien format (disque) : path = chemin absolu (ex: "/opt/docavocat/uploads/...")
     */
    @Transactional(readOnly = true)
    public byte[] getDecryptedFileContent(String documentId, String userId) {
        Document document = getDocumentById(documentId);
        verifyDocumentOwnership(document, userId);

        try {
            byte[] fileBytes;
            String path = document.getPath();

            if (isLegacyPath(path)) {
                // Rétrocompatibilité : lecture depuis le disque local
                fileBytes = Files.readAllBytes(Paths.get(path));
            } else {
                // Nouveau format : lecture depuis MinIO
                fileBytes = storageService.getBytes(BUCKET_DOCUMENTS, path);
            }

            if (Boolean.TRUE.equals(document.getEncrypted())) {
                return cryptographyService.decryptBytes(fileBytes, FILE_ENCRYPTION_KEY_ID);
            }
            return fileBytes;
        } catch (Exception e) {
            log.error("Erreur lecture document {}: {}", documentId, e.getMessage());
            throw new RuntimeException("Impossible de lire le document", e);
        }
    }

    /**
     * Calcule la taille totale des documents d'un avocat (depuis la BDD).
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

    // ── Helpers privés ───────────────────────────────────────────────────────

    /** Un chemin est "legacy" s'il commence par "/" (chemin filesystem absolu). */
    private boolean isLegacyPath(String path) {
        return path != null && path.startsWith("/");
    }

    private String validateAndSanitizeFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("Nom de fichier invalide");
        }
        originalFilename = originalFilename.replaceAll("[/\\\\\\x00]", "_");
        String ext = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("Type de fichier non autorisé: " + ext);
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIMETYPES.contains(mimeType.toLowerCase())) {
            throw new RuntimeException("Type MIME non autorisé: " + mimeType);
        }
        return originalFilename;
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex > 0 ? filename.substring(dotIndex).toLowerCase() : "";
    }

    private void verifyDocumentOwnership(Document document, String userId) {
        if (document.getCaseEntity() == null) {
            throw new SecurityException("Accès non autorisé à ce document");
        }
        // Autoriser l'avocat du dossier
        if (document.getCaseEntity().getLawyer() != null
                && document.getCaseEntity().getLawyer().getId().equals(userId)) {
            return;
        }
        // Autoriser le client du dossier (accès lecture via portail client)
        if (document.getCaseEntity().getClient() != null
                && document.getCaseEntity().getClient().getClientUser() != null
                && document.getCaseEntity().getClient().getClientUser().getId().equals(userId)) {
            return;
        }
        throw new SecurityException("Accès non autorisé à ce document");
    }

    /**
     * SEC FIX H-11 : Validation des magic bytes pour les formats courants.
     */
    private boolean validateMagicBytes(byte[] header, String extension) {
        if (header == null || header.length < 4) return false;
        return switch (extension.toLowerCase()) {
            case ".pdf" -> header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;
            case ".png" -> header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
            case ".jpg", ".jpeg" -> header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
            case ".gif" -> header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46;
            case ".zip", ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp" ->
                header[0] == 0x50 && header[1] == 0x4B;
            case ".rar" -> header[0] == 0x52 && header[1] == 0x61 && header[2] == 0x72;
            case ".7z" -> header[0] == 0x37 && header[1] == 0x7A && header[2] == (byte) 0xBC && header[3] == (byte) 0xAF;
            case ".bmp" -> header[0] == 0x42 && header[1] == 0x4D;
            case ".tiff" -> (header[0] == 0x49 && header[1] == 0x49) || (header[0] == 0x4D && header[1] == 0x4D);
            case ".doc", ".xls", ".ppt", ".msg" ->
                header[0] == (byte) 0xD0 && header[1] == (byte) 0xCF && header[2] == 0x11 && header[3] == (byte) 0xE0;
            default -> true;
        };
    }
}
