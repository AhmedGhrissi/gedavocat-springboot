-- ============================================================
-- DOCAVOCAT - Schema Base de Donnees MySQL 8.0
-- ============================================================
-- Date: Mars 2026
-- Version: 1.0.0
-- Encoding: UTF-8
-- Collation: utf8mb4_unicode_ci
-- ============================================================

-- Drop existing tables (development only)
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS signature_events;
DROP TABLE IF EXISTS signatures;
DROP TABLE IF EXISTS document_shares;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS rpva_communications;
DROP TABLE IF EXISTS case_share_links;
DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS invoice_items;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS cases;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS firms;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- TABLE: firms (Cabinets d'avocats)
-- Multi-tenant isolation par firm_id
-- ============================================================
CREATE TABLE firms (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    siret VARCHAR(14),
    address VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(10),
    country VARCHAR(100) DEFAULT 'France',
    phone VARCHAR(20),
    email VARCHAR(255),
    website VARCHAR(255),
    logo_url VARCHAR(500),
    
    -- Configuration
    max_lawyers INT DEFAULT 5,
    max_clients INT DEFAULT 100,
    max_storage_gb INT DEFAULT 50,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Indexes
    INDEX idx_firm_name (name),
    INDEX idx_firm_siret (siret),
    INDEX idx_firm_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: users (Utilisateurs - Avocats, Clients, Admin)
-- ============================================================
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    
    -- Multi-tenant
    firm_id VARCHAR(36),
    
    -- Sécurité
    enabled TINYINT(1) DEFAULT 0,
    account_non_locked TINYINT(1) DEFAULT 1,
    failed_login_attempts INT DEFAULT 0,
    last_login DATETIME(6),
    password_changed_at DATETIME(6),
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_user_email (email),
    INDEX idx_user_role (role),
    INDEX idx_user_firm_id (firm_id),
    INDEX idx_user_enabled (enabled),
    INDEX idx_user_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: refresh_tokens (JWT Refresh Tokens)
-- ============================================================
CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked TINYINT(1) DEFAULT 0,
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_refresh_token (token),
    INDEX idx_refresh_token_user_id (user_id),
    INDEX idx_refresh_token_expires_at (expires_at),
    INDEX idx_refresh_token_revoked (revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: clients (Clients des avocats)
-- ============================================================
CREATE TABLE clients (
    id VARCHAR(36) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(10),
    
    -- Multi-tenant
    firm_id VARCHAR(36) NOT NULL,
    
    -- Portail client
    portal_access TINYINT(1) DEFAULT 0,
    portal_invitation_token VARCHAR(100),
    portal_invitation_sent_at DATETIME(6),
    portal_invitation_expires_at DATETIME(6),
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_client_firm_id (firm_id),
    INDEX idx_client_email (email),
    INDEX idx_client_name (last_name, first_name),
    INDEX idx_client_portal_access (portal_access),
    INDEX idx_client_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: cases (Dossiers juridiques)
-- ============================================================
CREATE TABLE cases (
    id VARCHAR(36) PRIMARY KEY,
    reference VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'OUVERT',
    
    -- Relations
    client_id VARCHAR(36) NOT NULL,
    lawyer_id VARCHAR(36) NOT NULL,
    firm_id VARCHAR(36) NOT NULL,
    
    -- Dates importantes
    opened_at DATE,
    closed_at DATE,
    deadline DATE,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_case_firm_id (firm_id),
    INDEX idx_case_client_id (client_id),
    INDEX idx_case_lawyer_id (lawyer_id),
    INDEX idx_case_reference (reference),
    INDEX idx_case_type (type),
    INDEX idx_case_status (status),
    INDEX idx_case_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: documents (Documents du dossier)
-- ============================================================
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    category VARCHAR(50),
    description TEXT,
    
    -- Relations
    case_id VARCHAR(36) NOT NULL,
    uploaded_by VARCHAR(36),
    firm_id VARCHAR(36) NOT NULL,
    
    -- Sécurité
    is_confidential TINYINT(1) DEFAULT 0,
    version INT DEFAULT 1,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_document_firm_id (firm_id),
    INDEX idx_document_case_id (case_id),
    INDEX idx_document_uploaded_by (uploaded_by),
    INDEX idx_document_category (category),
    INDEX idx_document_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: document_shares (Partage de documents)
-- ============================================================
CREATE TABLE document_shares (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    target_role VARCHAR(20) NOT NULL,
    can_download TINYINT(1) DEFAULT 0,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    UNIQUE KEY uk_docshare_doc_role (document_id, target_role),
    
    -- Indexes
    INDEX idx_docshare_document (document_id),
    INDEX idx_docshare_case (case_id),
    INDEX idx_docshare_role (target_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: case_share_links (Liens de partage temporaires)
-- ============================================================
CREATE TABLE case_share_links (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    token VARCHAR(72) NOT NULL UNIQUE,
    recipient_email VARCHAR(255),
    recipient_role VARCHAR(20),
    expires_at DATETIME(6) NOT NULL,
    access_count INT DEFAULT 0,
    max_access INT DEFAULT 10,
    is_active TINYINT(1) DEFAULT 1,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    last_accessed_at DATETIME(6),
    
    -- Constraints
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_case_share_link_token (token),
    INDEX idx_case_share_link_case_id (case_id),
    INDEX idx_case_share_link_expires_at (expires_at),
    INDEX idx_case_share_link_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: signatures (Signatures électroniques Yousign)
-- ============================================================
CREATE TABLE signatures (
    id VARCHAR(36) PRIMARY KEY,
    yousign_signature_request_id VARCHAR(255) UNIQUE,
    document_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36),
    status VARCHAR(50) DEFAULT 'PENDING',
    
    -- Signataires
    signer_email VARCHAR(255) NOT NULL,
    signer_first_name VARCHAR(100),
    signer_last_name VARCHAR(100),
    signer_role VARCHAR(50),
    
    -- Fichier signé
    signed_file_url VARCHAR(500),
   signed_file_path VARCHAR(500),
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    signed_at DATETIME(6),
    expires_at DATETIME(6),
    
    -- Constraints
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
    
    -- Indexes
    INDEX idx_signature_document_id (document_id),
    INDEX idx_signature_case_id (case_id),
    INDEX idx_signature_status (status),
    INDEX idx_signature_yousign_id (yousign_signature_request_id),
    INDEX idx_signature_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: signature_events (Événements de signature)
-- ============================================================
CREATE TABLE signature_events (
    id VARCHAR(36) PRIMARY KEY,
    signature_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data TEXT,
    webhook_payload TEXT,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (signature_id) REFERENCES signatures(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_signature_event_signature_id (signature_id),
    INDEX idx_signature_event_type (event_type),
    INDEX idx_signature_event_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: appointments (Rendez-vous et audiences)
-- ============================================================
CREATE TABLE appointments (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    type VARCHAR(50) NOT NULL,
    
    -- Relations
    case_id VARCHAR(36),
    lawyer_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36),
    firm_id VARCHAR(36) NOT NULL,
    
    -- Planning
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    is_all_day TINYINT(1) DEFAULT 0,
    
    -- Statut
    status VARCHAR(50) DEFAULT 'SCHEDULED',
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
    FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL,
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_appointment_firm_id (firm_id),
    INDEX idx_appointment_case_id (case_id),
    INDEX idx_appointment_lawyer_id (lawyer_id),
    INDEX idx_appointment_client_id (client_id),
    INDEX idx_appointment_start_time (start_time),
    INDEX idx_appointment_type (type),
    INDEX idx_appointment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: invoices (Factures)
-- ============================================================
CREATE TABLE invoices (
    id VARCHAR(36) PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    
    -- Relations
    client_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36),
    firm_id VARCHAR(36) NOT NULL,
    
    -- Montants
    subtotal_amount DECIMAL(10,2) DEFAULT 0.00,
    tax_amount DECIMAL(10,2) DEFAULT 0.00,
    total_amount DECIMAL(10,2) NOT NULL,
    paid_amount DECIMAL(10,2) DEFAULT 0.00,
    
    -- Statut
    status VARCHAR(50) DEFAULT 'DRAFT',
    payment_status VARCHAR(50) DEFAULT 'UNPAID',
    
    -- Dates
    issue_date DATE NOT NULL,
    due_date DATE,
    paid_date DATE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_invoice_firm_id (firm_id),
    INDEX idx_invoice_client_id (client_id),
    INDEX idx_invoice_case_id (case_id),
    INDEX idx_invoice_number (invoice_number),
    INDEX idx_invoice_status (status),
    INDEX idx_invoice_payment_status (payment_status),
    INDEX idx_invoice_issue_date (issue_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: invoice_items (Lignes de facture)
-- ============================================================
CREATE TABLE invoice_items (
    id VARCHAR(36) PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL DEFAULT 1.00,
    unit_price_ht DECIMAL(10,2) NOT NULL COMMENT 'Prix unitaire Hors Taxe',
    tva_rate DECIMAL(5,2) NOT NULL DEFAULT 20.00 COMMENT 'Taux de TVA en pourcentage',
    total_ht DECIMAL(10,2) NULL COMMENT 'Total Hors Taxe (calculé)',
    total_tva DECIMAL(10,2) NULL COMMENT 'Total TVA (calculé)',
    total_ttc DECIMAL(10,2) NULL COMMENT 'Total Toutes Taxes Comprises (calculé)',
    display_order INT NULL COMMENT 'Ordre d''affichage',
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_invoice_item_invoice_id (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: payments (Paiements Stripe)
-- ============================================================
CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    invoice_id VARCHAR(36),
    
    -- Montants
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    
    -- Statut
    status VARCHAR(50) DEFAULT 'PENDING',
    
    -- Métadonnées
    payment_method VARCHAR(50),
    card_last4 VARCHAR(4),
    card_brand VARCHAR(20),
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    paid_at DATETIME(6),
    
    -- Constraints
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL,
    
    -- Indexes
    INDEX idx_payment_invoice_id (invoice_id),
    INDEX idx_payment_stripe_id (stripe_payment_intent_id),
    INDEX idx_payment_status (status),
    INDEX idx_payment_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: rpva_communications (Communications RPVA)
-- ============================================================
CREATE TABLE rpva_communications (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36),
    
    -- Expéditeur
    sender_id VARCHAR(36) NOT NULL,
    sender_email VARCHAR(255) NOT NULL,
    
    -- Destinataire
    recipient_jurisdiction VARCHAR(255) NOT NULL,
    recipient_email VARCHAR(255),
    
    -- Contenu
    subject VARCHAR(500) NOT NULL,
    message TEXT,
    
    -- Documents joints
    attachment_path VARCHAR(500),
    attachment_filename VARCHAR(255),
    
    -- Statut
    status VARCHAR(50) DEFAULT 'SENT',
    direction VARCHAR(20) NOT NULL,
    
    -- Dates
    sent_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    received_at DATETIME(6),
    read_at DATETIME(6),
    
    -- Constraints
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_rpva_case_id (case_id),
    INDEX idx_rpva_sender_id (sender_id),
    INDEX idx_rpva_status (status),
    INDEX idx_rpva_direction (direction),
    INDEX idx_rpva_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: permissions (Permissions granulaires)
-- ============================================================
CREATE TABLE permissions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(36),
    permission VARCHAR(50) NOT NULL,
    
    -- Dates
    granted_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6),
    
    -- Constraints
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_permission_user_resource (user_id, resource_type, resource_id, permission),
    
    -- Indexes
    INDEX idx_permission_user_id (user_id),
    INDEX idx_permission_resource (resource_type, resource_id),
    INDEX idx_permission_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE: audit_logs (Logs d'audit sécurité)
-- ============================================================
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36),
    firm_id VARCHAR(36),
    
    -- Action
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(36),
    
    -- Détails
    description TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    
    -- Résultat
    status VARCHAR(20) DEFAULT 'SUCCESS',
    error_message TEXT,
    
    -- Dates
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    
    -- Constraints
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (firm_id) REFERENCES firms(id) ON DELETE SET NULL,
    
    -- Indexes
    INDEX idx_audit_user_id (user_id),
    INDEX idx_audit_firm_id (firm_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_resource (resource_type, resource_id),
    INDEX idx_audit_status (status),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- DONNEES INITIALES (Development/Demo)
-- ============================================================

-- Cabinet de démonstration
INSERT INTO firms (id, name, siret, address, city, postal_code, phone, email, max_lawyers, max_clients, max_storage_gb) VALUES
('firm-demo-123', 'Cabinet Dupont & Associés', '12345678901234', '10 Rue de la Paix', 'Paris', '75001', '01 23 45 67 89', 'contact@dupont-avocats.fr', 10, 500, 100);

-- Utilisateur admin
INSERT INTO users (id, email, password, first_name, last_name, role, firm_id, enabled, account_non_locked) VALUES
('admin-123', 'admin@docavocat.fr', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/HjxMZT8LqYw0LpyWm', 'Admin', 'DocAvocat', 'ADMIN', 'firm-demo-123', 1, 1);
-- Mot de passe: Admin123!

-- Avocat de démonstration
INSERT INTO users (id, email, password, first_name, last_name, phone, role, firm_id, enabled, account_non_locked) VALUES
('lawyer-demo-123', 'avocat@dupont-avocats.fr', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/HjxMZT8LqYw0LpyWm', 'Jean', 'Dupont', '01 23 45 67 89', 'LAWYER', 'firm-demo-123', 1, 1);
-- Mot de passe: Avocat123!

-- ============================================================
-- FIN DU SCHEMA
-- ============================================================

-- Verification
SELECT 'Schema installation complete' AS status;
SELECT TABLE_NAME, ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS 'Size (MB)'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY (DATA_LENGTH + INDEX_LENGTH) DESC;
