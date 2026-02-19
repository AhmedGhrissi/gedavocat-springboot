-- ===================================================================
-- GED Avocat - Schema MySQL CORRIGE
-- ===================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS cases;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ===================================================================
-- Table: users
-- ===================================================================
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'LAWYER', 'CLIENT', 'LAWYER_SECONDARY') NOT NULL,
    subscription_plan ENUM('SOLO', 'CABINET', 'ENTERPRISE'),
    subscription_status ENUM('ACTIVE', 'INACTIVE', 'CANCELLED', 'TRIAL'),
    max_clients INT DEFAULT 10,
    subscription_ends_at DATETIME,
    gdpr_consent_at DATETIME,
    terms_accepted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================================================
-- Table: clients
-- ===================================================================
CREATE TABLE clients (
    id VARCHAR(36) PRIMARY KEY,
    lawyer_id VARCHAR(36) NOT NULL,
    client_user_id VARCHAR(36),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    address TEXT,
    access_ends_at DATETIME,
    invitation_id VARCHAR(36),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (client_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_lawyer_id (lawyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================================================
-- Table: cases
-- ===================================================================
CREATE TABLE cases (
    id VARCHAR(36) PRIMARY KEY,
    lawyer_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status ENUM('OPEN', 'CLOSED', 'ARCHIVED') NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL,
    INDEX idx_lawyer_id (lawyer_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================================================
-- Table: documents
-- ===================================================================
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    uploader_role VARCHAR(20) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mimetype VARCHAR(100),
    file_size BIGINT,
    path VARCHAR(500) NOT NULL,
    version INT DEFAULT 1,
    parent_document_id VARCHAR(36),
    is_latest BOOLEAN DEFAULT TRUE,
    deleted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id),
    INDEX idx_case_id (case_id),
    INDEX idx_is_latest (is_latest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================================================
-- Table: permissions
-- ===================================================================
CREATE TABLE permissions (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    granted_by VARCHAR(36) NOT NULL,
    lawyer_id VARCHAR(36) NOT NULL,
    can_read BOOLEAN DEFAULT TRUE,
    can_write BOOLEAN DEFAULT FALSE,
    can_upload BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at DATETIME,
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY (granted_by) REFERENCES users(id),
    FOREIGN KEY (lawyer_id) REFERENCES users(id),
    INDEX idx_case_id (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================================================================
-- Table: audit_logs
-- ===================================================================
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(36),
    details TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- ===================================================================
-- GED Avocat - Donnees de demonstration (CORRIGE - sans apostrophes)
-- ===================================================================
-- Mot de passe pour TOUS les comptes : password123
-- Hash BCrypt correspondant :
-- $2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm
-- ===================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Nettoyer les donnees existantes (ordre inverse des FK)
DELETE FROM audit_logs;
DELETE FROM permissions;
DELETE FROM documents;
DELETE FROM cases;
DELETE FROM clients;
DELETE FROM users;

-- ===================================================================
-- Utilisateurs
-- ===================================================================

INSERT INTO users (id, name, email, password, role, subscription_plan, subscription_status, max_clients, subscription_ends_at, gdpr_consent_at, terms_accepted_at, created_at, updated_at) VALUES
-- Admin
('admin-001',
 'Administrateur Systeme',
 'admin@gedavocat.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'ADMIN', NULL, NULL, 999,
 DATE_ADD(NOW(), INTERVAL 99 YEAR),
 NOW(), NOW(), NOW(), NOW()),

-- Avocat 1 - Plan Cabinet
('lawyer-001',
 'Maitre Jean Dupont',
 'jean.dupont@gedavocat.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'LAWYER', 'CABINET', 'ACTIVE', 100,
 DATE_ADD(NOW(), INTERVAL 1 YEAR),
 NOW(), NOW(), NOW(), NOW()),

-- Avocat 2 - Plan Solo
('lawyer-002',
 'Maitre Marie Martin',
 'marie.martin@gedavocat.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'LAWYER', 'SOLO', 'ACTIVE', 10,
 DATE_ADD(NOW(), INTERVAL 6 MONTH),
 NOW(), NOW(), NOW(), NOW()),

-- Avocat 3 - Plan Enterprise
('lawyer-003',
 'Maitre Pierre Bernard',
 'pierre.bernard@gedavocat.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'LAWYER', 'ENTERPRISE', 'ACTIVE', 999999,
 DATE_ADD(NOW(), INTERVAL 2 YEAR),
 NOW(), NOW(), NOW(), NOW()),

-- Collaborateur
('collab-001',
 'Sophie Lefebvre',
 'sophie.lefebvre@gedavocat.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'LAWYER_SECONDARY', NULL, NULL, 5,
 NULL, NOW(), NOW(), NOW(), NOW()),

-- Client 1
('client-user-001',
 'Paul Durand',
 'paul.durand@email.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'CLIENT', NULL, NULL, 0,
 NULL, NOW(), NOW(), NOW(), NOW()),

-- Client 2
('client-user-002',
 'Claire Petit',
 'claire.petit@email.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qP/5P5PjI8fMlODJlFhJYt3C1WQm',
 'CLIENT', NULL, NULL, 0,
 NULL, NOW(), NOW(), NOW(), NOW());

-- ===================================================================
-- Clients du cabinet
-- ===================================================================

INSERT INTO clients (id, lawyer_id, client_user_id, name, email, phone, address, created_at, updated_at) VALUES
('client-001', 'lawyer-001', 'client-user-001', 'Paul Durand',        'paul.durand@email.com',    '0612345678', '12 Rue de la Republique, 75001 Paris', NOW(), NOW()),
('client-002', 'lawyer-001', 'client-user-002', 'Claire Petit',       'claire.petit@email.com',   '0623456789', '45 Avenue des Champs, 75008 Paris',    NOW(), NOW()),
('client-003', 'lawyer-001', NULL,              'Entreprise ABC SARL','contact@abc.fr',            '0134567890', '78 Boulevard Haussmann, 75009 Paris',  NOW(), NOW()),
('client-004', 'lawyer-002', NULL,              'Jacques Moreau',     'jacques.moreau@email.com',  '0645678901', '23 Rue du Commerce, 69002 Lyon',       NOW(), NOW()),
('client-005', 'lawyer-002', NULL,              'Sophie Lambert',     'sophie.lambert@email.com',  '0656789012', '56 Cours Mirabeau, 13100 Aix',         NOW(), NOW()),
('client-006', 'lawyer-003', NULL,              'Societe XYZ SA',     'direction@xyz-sa.com',      '0167890123', '34 Rue Lafayette, 31000 Toulouse',     NOW(), NOW());

-- ===================================================================
-- Dossiers (sans apostrophes dans les noms)
-- ===================================================================

INSERT INTO cases (id, lawyer_id, client_id, name, description, status, created_at, updated_at) VALUES
('case-001', 'lawyer-001', 'client-001', 'Divorce Durand',          'Procedure de divorce contentieux avec partage des biens', 'OPEN',     DATE_SUB(NOW(), INTERVAL 30 DAY), NOW()),
('case-002', 'lawyer-001', 'client-001', 'Garde des enfants Durand','Modification du droit de garde suite au divorce',          'OPEN',     DATE_SUB(NOW(), INTERVAL 20 DAY), NOW()),
('case-003', 'lawyer-001', 'client-002', 'Licenciement Petit',      'Contestation licenciement pour faute grave',              'OPEN',     DATE_SUB(NOW(), INTERVAL 45 DAY), NOW()),
('case-004', 'lawyer-001', 'client-003', 'Litige ABC Fournisseur',  'Non-respect de contrat de fourniture',                    'CLOSED',   DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
('case-005', 'lawyer-001', 'client-003', 'Recouvrement creances ABC','Recouvrement de creances clients impayes',               'ARCHIVED', DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY)),
('case-006', 'lawyer-002', 'client-004', 'Succession Moreau',       'Reglement de succession avec testament conteste',         'OPEN',     DATE_SUB(NOW(), INTERVAL 60 DAY), NOW()),
('case-007', 'lawyer-002', 'client-005', 'Vente immobiliere Lambert','Accompagnement vente appartement defaut cache',          'OPEN',     DATE_SUB(NOW(), INTERVAL 15 DAY), NOW()),
('case-008', 'lawyer-003', 'client-006', 'Fusion XYZ',              'Due diligence fusion avec societe concurrente',           'OPEN',     DATE_SUB(NOW(), INTERVAL 120 DAY), NOW()),
('case-009', 'lawyer-003', 'client-006', 'Propriete intellectuelle XYZ','Depot et protection marques et brevets',              'OPEN',     DATE_SUB(NOW(), INTERVAL 90 DAY), NOW());

-- ===================================================================
-- Documents
-- ===================================================================

INSERT INTO documents (id, case_id, uploaded_by, uploader_role, filename, original_name, mimetype, file_size, path, version, is_latest, created_at, updated_at) VALUES
('doc-001', 'case-001', 'lawyer-001', 'LAWYER', 'doc-001.pdf', 'Requete en divorce.pdf',       'application/pdf', 524288,  '/uploads/doc-001.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 30 DAY), NOW()),
('doc-002', 'case-001', 'lawyer-001', 'LAWYER', 'doc-002.pdf', 'Convention parentale.pdf',     'application/pdf', 327680,  '/uploads/doc-002.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 28 DAY), NOW()),
('doc-003', 'case-001', 'client-user-001', 'CLIENT', 'doc-003.pdf', 'Justificatifs revenus.pdf', 'application/pdf', 1048576, '/uploads/doc-003.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 25 DAY), NOW()),
('doc-004', 'case-002', 'lawyer-001', 'LAWYER', 'doc-004.pdf', 'Requete modification garde.pdf','application/pdf', 458752,  '/uploads/doc-004.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 20 DAY), NOW()),
('doc-005', 'case-003', 'lawyer-001', 'LAWYER', 'doc-005.pdf', 'Assignation prudhommes.pdf',   'application/pdf', 655360,  '/uploads/doc-005.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 45 DAY), NOW()),
('doc-006', 'case-003', 'client-user-002', 'CLIENT', 'doc-006.pdf', 'Lettre licenciement.pdf', 'application/pdf', 262144,  '/uploads/doc-006.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 44 DAY), NOW()),
('doc-007', 'case-004', 'lawyer-001', 'LAWYER', 'doc-007.pdf', 'Contrat de fourniture.pdf',    'application/pdf', 983040,  '/uploads/doc-007.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 90 DAY), NOW()),
('doc-008', 'case-004', 'lawyer-001', 'LAWYER', 'doc-008.pdf', 'Jugement tribunal commerce.pdf','application/pdf',720896,  '/uploads/doc-008.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW()),
('doc-009', 'case-006', 'lawyer-002', 'LAWYER', 'doc-009.pdf', 'Acte de notoriete.pdf',        'application/pdf', 491520,  '/uploads/doc-009.pdf', 1, TRUE, DATE_SUB(NOW(), INTERVAL 60 DAY), NOW());

-- ===================================================================
-- Permissions
-- ===================================================================

INSERT INTO permissions (id, case_id, granted_by, lawyer_id, can_read, can_write, can_upload, is_active, granted_at) VALUES
('perm-001', 'case-001', 'lawyer-001', 'collab-001', TRUE, TRUE,  TRUE,  TRUE, DATE_SUB(NOW(), INTERVAL 25 DAY)),
('perm-002', 'case-002', 'lawyer-001', 'collab-001', TRUE, FALSE, FALSE, TRUE, DATE_SUB(NOW(), INTERVAL 18 DAY));

INSERT INTO permissions (id, case_id, granted_by, lawyer_id, can_read, can_write, can_upload, is_active, expires_at, granted_at) VALUES
('perm-003', 'case-003', 'lawyer-001', 'lawyer-002', TRUE, FALSE, FALSE, TRUE, DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

-- ===================================================================
-- Logs audit
-- ===================================================================

INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id, details, ip_address, created_at) VALUES
('audit-001', 'lawyer-001', 'USER_LOGIN',        NULL,     NULL,       'Connexion reussie',                              '127.0.0.1', DATE_SUB(NOW(), INTERVAL 1 HOUR)),
('audit-002', 'lawyer-001', 'CASE_CREATED',      'Case',   'case-001', 'Creation dossier Divorce Durand',                '127.0.0.1', DATE_SUB(NOW(), INTERVAL 30 DAY)),
('audit-003', 'lawyer-001', 'DOCUMENT_UPLOADED', 'Document','doc-001', 'Upload Requete en divorce.pdf',                  '127.0.0.1', DATE_SUB(NOW(), INTERVAL 30 DAY)),
('audit-004', 'admin-001',  'USER_LOGIN',        NULL,     NULL,       'Connexion administrateur',                       '127.0.0.1', DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
('audit-005', 'lawyer-002', 'CLIENT_CREATED',    'Client', 'client-004','Creation client Jacques Moreau',                '127.0.0.1', DATE_SUB(NOW(), INTERVAL 60 DAY));

SET FOREIGN_KEY_CHECKS = 1;

-- ===================================================================
-- Verification
-- ===================================================================
SELECT CONCAT('✓ Utilisateurs inseres : ', COUNT(*)) AS Resultat FROM users;
SELECT CONCAT('✓ Clients inseres : ',      COUNT(*)) AS Resultat FROM clients;
SELECT CONCAT('✓ Dossiers inseres : ',     COUNT(*)) AS Resultat FROM cases;
SELECT CONCAT('✓ Documents inseres : ',    COUNT(*)) AS Resultat FROM documents;

SELECT '' AS '';
SELECT 'COMPTES DE CONNEXION' AS Info;
SELECT email AS Email, 'password123' AS MotDePasse, role AS Role FROM users ORDER BY role;
