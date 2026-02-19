-- ============================================
-- GED AVOCAT - BASE DE DONNÉES COMPLÈTE
-- Pour O2switch / MySQL 8.0+
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Création de la base (si elle n'existe pas)
CREATE DATABASE IF NOT EXISTS gedavocat
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE gedavocat;

-- ============================================
-- TABLE: users
-- ============================================
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NULL,
    last_name VARCHAR(100) NULL,
    phone VARCHAR(20) NULL,
    bar_number VARCHAR(50) NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','LAWYER','CLIENT','LAWYER_SECONDARY') NOT NULL,

    -- Abonnement
    subscription_plan ENUM('SOLO','CABINET','ENTERPRISE') NULL,
    subscription_status ENUM('ACTIVE','INACTIVE','CANCELLED','TRIAL') NULL,
    subscription_start_date DATETIME NULL,
    subscription_ends_at DATETIME NULL,
    max_clients INT DEFAULT 10,

    -- RGPD
    gdpr_consent_at DATETIME NULL,
    terms_accepted_at DATETIME NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Autres
    access_ends_at DATETIME NULL,
    invitation_id VARCHAR(36) NULL,

    INDEX idx_email (email),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: clients
-- ============================================
DROP TABLE IF EXISTS clients;
CREATE TABLE clients (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NULL,
    address TEXT NULL,

    -- Relations
    lawyer_id VARCHAR(36) NOT NULL,
    client_user_id VARCHAR(36) NULL,

    -- Accès et invitation
    access_ends_at DATETIME NULL,
    invitation_id VARCHAR(36) NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (client_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_lawyer (lawyer_id),
    INDEX idx_client_user (client_user_id),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: cases (dossiers)
-- ============================================
DROP TABLE IF EXISTS cases;
CREATE TABLE cases (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status ENUM('OPEN','IN_PROGRESS','CLOSED','ARCHIVED') NOT NULL DEFAULT 'OPEN',

    -- Relations
    lawyer_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36) NOT NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    INDEX idx_lawyer (lawyer_id),
    INDEX idx_client (client_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: documents
-- ============================================
DROP TABLE IF EXISTS documents;
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mimetype VARCHAR(100) NULL,
    file_size BIGINT NULL,
    path TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    uploader_role VARCHAR(20) NOT NULL,
    deleted_at DATETIME NULL,

    -- Relations
    case_id VARCHAR(36) NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    parent_document_id VARCHAR(36) NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_document_id) REFERENCES documents(id) ON DELETE SET NULL,
    INDEX idx_case (case_id),
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_deleted_at (deleted_at),
    INDEX idx_parent (parent_document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: signatures (Yousign)
-- ============================================
DROP TABLE IF EXISTS signatures;
CREATE TABLE signatures (
    id VARCHAR(36) PRIMARY KEY,
    yousign_signature_request_id VARCHAR(255) UNIQUE NULL,
    document_name VARCHAR(255) NOT NULL,
    status ENUM('DRAFT','PENDING','SIGNED','REJECTED','EXPIRED') NOT NULL DEFAULT 'DRAFT',

    -- Relations
    document_id VARCHAR(36) NOT NULL,
    requested_by VARCHAR(36) NOT NULL,

    -- Signataires
    signer_name VARCHAR(255) NOT NULL,
    signer_email VARCHAR(255) NOT NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    signed_at DATETIME NULL,

    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_document (document_id),
    INDEX idx_status (status),
    INDEX idx_yousign (yousign_signature_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: rpva_communications (e-Barreau)
-- ============================================
DROP TABLE IF EXISTS rpva_communications;
CREATE TABLE rpva_communications (
    id VARCHAR(36) PRIMARY KEY,
    type ENUM('ASSIGNATION','CONCLUSIONS','MEMOIRE','PIECE','NOTIFICATION') NOT NULL,
    jurisdiction VARCHAR(255) NOT NULL,
    reference_number VARCHAR(100) NULL,
    status ENUM('DRAFT','SENT','DELIVERED','READ','FAILED') NOT NULL DEFAULT 'DRAFT',

    -- Relations
    case_id VARCHAR(36) NOT NULL,
    sent_by VARCHAR(36) NOT NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    delivered_at DATETIME NULL,

    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY (sent_by) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_case (case_id),
    INDEX idx_status (status),
    INDEX idx_reference (reference_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: payments (historique paiements)
-- ============================================
DROP TABLE IF EXISTS payments;
CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    payplug_payment_id VARCHAR(255) UNIQUE NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    status ENUM('PENDING','PAID','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',

    -- Relations
    user_id VARCHAR(36) NOT NULL,

    -- Détails
    subscription_plan ENUM('SOLO','CABINET','ENTERPRISE') NOT NULL,
    billing_period ENUM('MONTHLY','YEARLY') NOT NULL,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_payplug (payplug_payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: audit_logs
-- ============================================
DROP TABLE IF EXISTS audit_logs;
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    details TEXT NULL,
    ip_address VARCHAR(45) NULL,

    -- Relations
    user_id VARCHAR(36) NOT NULL,

    -- Timestamp
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_entity (entity_type, entity_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: invoices (factures)
-- ============================================
DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
    id VARCHAR(36) PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    client_id VARCHAR(36) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE NULL,
    paid_date DATE NULL,
    status ENUM('DRAFT','SENT','PAID','OVERDUE','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    total_ht DECIMAL(10, 2) NOT NULL,
    total_tva DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_ttc DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    notes TEXT NULL,
    payment_method VARCHAR(50) NULL,
    document_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    INDEX idx_invoice_client_id (client_id),
    INDEX idx_invoice_number (invoice_number),
    INDEX idx_invoice_status (status),
    INDEX idx_invoice_date (invoice_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- TABLE: invoice_items (lignes de facture)
-- ============================================
DROP TABLE IF EXISTS invoice_items;
CREATE TABLE invoice_items (
    id VARCHAR(36) PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    unit_price_ht DECIMAL(10, 2) NOT NULL,
    tva_rate DECIMAL(5, 2) NOT NULL DEFAULT 20.00,
    total_ht DECIMAL(10, 2) NULL,
    total_tva DECIMAL(10, 2) NULL,
    total_ttc DECIMAL(10, 2) NULL,
    display_order INT NULL,

    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    INDEX idx_invoice_item_invoice_id (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- DONNÉES DE TEST
-- ============================================

-- ============================================
-- UTILISATEURS DE TEST (mot de passe pour tous: password123)
-- Hash bcrypt: $2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6
-- ============================================

-- 1. ADMIN
INSERT INTO users (id, name, first_name, last_name, email, password, role, subscription_plan, subscription_status, subscription_ends_at, max_clients, created_at) VALUES
('admin-001', 'Admin Principal', 'Admin', 'Principal', 'admin@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'ADMIN', 'ENTERPRISE', 'ACTIVE', DATE_ADD(NOW(), INTERVAL 1 YEAR), 999, NOW());

-- 2. AVOCATS
INSERT INTO users (id, name, first_name, last_name, phone, bar_number, email, password, role, subscription_plan, subscription_status, subscription_start_date, subscription_ends_at, max_clients, gdpr_consent_at, terms_accepted_at, created_at) VALUES
('lawyer-001', 'Jean Dupont', 'Jean', 'Dupont', '+33612345678', 'P123456', 'jean.dupont@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'LAWYER', 'CABINET', 'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 100, NOW(), NOW(), NOW()),
('lawyer-002', 'Sophie Bernard', 'Sophie', 'Bernard', '+33623456789', 'P234567', 'sophie.bernard@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'LAWYER', 'SOLO', 'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 6 MONTH), 10, NOW(), NOW(), NOW()),
('lawyer-003', 'Marc Dubois', 'Marc', 'Dubois', '+33634567890', 'P345678', 'marc.dubois@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'LAWYER', 'ENTERPRISE', 'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 500, NOW(), NOW(), NOW()),
('lawyer-004', 'Julie Moreau', 'Julie', 'Moreau', '+33645678901', 'P456789', 'julie.moreau@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'LAWYER', 'SOLO', 'TRIAL', NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY), 10, NOW(), NOW(), NOW());

-- 3. AVOCATS SECONDAIRES
INSERT INTO users (id, name, first_name, last_name, phone, email, password, role, created_at) VALUES
('lawyer-sec-001', 'Pierre Lefebvre', 'Pierre', 'Lefebvre', '+33656789012', 'pierre.lefebvre@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'LAWYER_SECONDARY', NOW()),
('lawyer-sec-002', 'Émilie Petit', 'Émilie', 'Petit', '+33667890123', 'emilie.petit@gedavocat.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'LAWYER_SECONDARY', NOW());

-- 4. CLIENTS (avec compte utilisateur)
INSERT INTO users (id, name, first_name, last_name, phone, email, password, role, gdpr_consent_at, terms_accepted_at, created_at) VALUES
('client-user-001', 'Marie Martin', 'Marie', 'Martin', '+33678901234', 'marie.martin@example.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'CLIENT', NOW(), NOW(), NOW()),
('client-user-002', 'Paul Durand', 'Paul', 'Durand', '+33689012345', 'paul.durand@example.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'CLIENT', NOW(), NOW(), NOW()),
('client-user-003', 'Claire Rousseau', 'Claire', 'Rousseau', '+33690123456', 'claire.rousseau@example.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'CLIENT', NOW(), NOW(), NOW()),
('client-user-004', 'Thomas Vincent', 'Thomas', 'Vincent', '+33601234567', 'thomas.vincent@example.com', '$2a$12$zChbFEgJQsSVmhlWhH4kiuLhV1uasxBInAncAQCPdTDygFl66FbZ6', 'CLIENT', NOW(), NOW(), NOW());

-- ============================================
-- CLIENTS (entités clients rattachées aux avocats)
-- ============================================
INSERT INTO clients (id, name, email, phone, address, lawyer_id, client_user_id, created_at) VALUES
-- Clients de l'avocat Jean Dupont (lawyer-001)
('client-001', 'Marie Martin', 'marie.martin@example.com', '+33678901234', '15 rue de la Paix, 75001 Paris', 'lawyer-001', 'client-user-001', NOW()),
('client-002', 'Paul Durand', 'paul.durand@example.com', '+33689012345', '28 avenue des Champs, 75008 Paris', 'lawyer-001', 'client-user-002', NOW()),
('client-003', 'Isabelle Leroy', 'isabelle.leroy@example.com', '+33612345678', '42 boulevard Haussmann, 75009 Paris', 'lawyer-001', NULL, NOW()),
('client-004', 'François Garnier', 'francois.garnier@example.com', '+33623456789', '7 place Vendôme, 75001 Paris', 'lawyer-001', NULL, NOW()),

-- Clients de l'avocat Sophie Bernard (lawyer-002)
('client-005', 'Claire Rousseau', 'claire.rousseau@example.com', '+33690123456', '33 rue du Faubourg Saint-Honoré, 75008 Paris', 'lawyer-002', 'client-user-003', NOW()),
('client-006', 'Antoine Mercier', 'antoine.mercier@example.com', '+33634567890', '18 avenue Montaigne, 75008 Paris', 'lawyer-002', NULL, NOW()),
('client-007', 'Nathalie Blanc', 'nathalie.blanc@example.com', '+33645678901', '50 rue de Rivoli, 75004 Paris', 'lawyer-002', NULL, NOW()),

-- Clients de l'avocat Marc Dubois (lawyer-003)
('client-008', 'Thomas Vincent', 'thomas.vincent@example.com', '+33601234567', '12 place de la République, 75011 Paris', 'lawyer-003', 'client-user-004', NOW()),
('client-009', 'Sylvie Lambert', 'sylvie.lambert@example.com', '+33656789012', '25 rue Lafayette, 75009 Paris', 'lawyer-003', NULL, NOW()),
('client-010', 'Pierre Fontaine', 'pierre.fontaine@example.com', '+33667890123', '8 avenue Victor Hugo, 75116 Paris', 'lawyer-003', NULL, NOW()),

-- Clients de l'avocat Julie Moreau (lawyer-004)
('client-011', 'Céline Renard', 'celine.renard@example.com', '+33678901234', '14 rue Saint-Antoine, 75004 Paris', 'lawyer-004', NULL, NOW()),
('client-012', 'Olivier Dupuis', 'olivier.dupuis@example.com', '+33689012345', '22 boulevard Saint-Germain, 75005 Paris', 'lawyer-004', NULL, NOW());

-- ============================================
-- DOSSIERS (CASES)
-- ============================================
INSERT INTO cases (id, name, description, status, lawyer_id, client_id, created_at) VALUES
-- Dossiers de Jean Dupont (lawyer-001)
('case-001', 'Divorce contentieux Martin', 'Procédure de divorce avec désaccord sur la garde des enfants et le partage des biens', 'IN_PROGRESS', 'lawyer-001', 'client-001', DATE_SUB(NOW(), INTERVAL 45 DAY)),
('case-002', 'Succession Durand', 'Règlement de succession complexe avec plusieurs héritiers', 'OPEN', 'lawyer-001', 'client-002', DATE_SUB(NOW(), INTERVAL 30 DAY)),
('case-003', 'Litige commercial Leroy', 'Conflit avec un fournisseur - rupture abusive de contrat', 'IN_PROGRESS', 'lawyer-001', 'client-003', DATE_SUB(NOW(), INTERVAL 60 DAY)),
('case-004', 'Contentieux prud''homal Garnier', 'Licenciement abusif - demande de réintégration', 'OPEN', 'lawyer-001', 'client-004', DATE_SUB(NOW(), INTERVAL 15 DAY)),
('case-005', 'Divorce amiable Martin', 'Divorce par consentement mutuel - dossier clos', 'CLOSED', 'lawyer-001', 'client-001', DATE_SUB(NOW(), INTERVAL 120 DAY)),

-- Dossiers de Sophie Bernard (lawyer-002)
('case-006', 'Droit immobilier Rousseau', 'Vice caché lors de l''achat d''un appartement', 'OPEN', 'lawyer-002', 'client-005', DATE_SUB(NOW(), INTERVAL 20 DAY)),
('case-007', 'Contentieux locatif Mercier', 'Litige avec un locataire - impayés de loyers', 'IN_PROGRESS', 'lawyer-002', 'client-006', DATE_SUB(NOW(), INTERVAL 40 DAY)),
('case-008', 'Préjudice corporel Blanc', 'Accident de la circulation - indemnisation', 'OPEN', 'lawyer-002', 'client-007', DATE_SUB(NOW(), INTERVAL 10 DAY)),

-- Dossiers de Marc Dubois (lawyer-003)
('case-009', 'Droit des affaires Vincent', 'Création de société - structuration juridique', 'IN_PROGRESS', 'lawyer-003', 'client-008', DATE_SUB(NOW(), INTERVAL 25 DAY)),
('case-010', 'Contentieux fiscal Lambert', 'Redressement fiscal - contestation', 'OPEN', 'lawyer-003', 'client-009', DATE_SUB(NOW(), INTERVAL 35 DAY)),
('case-011', 'Acquisition Fontaine', 'Acquisition d''une société - due diligence', 'IN_PROGRESS', 'lawyer-003', 'client-010', DATE_SUB(NOW(), INTERVAL 50 DAY)),
('case-012', 'Propriété intellectuelle Lambert', 'Contrefaçon de brevet - action en justice', 'OPEN', 'lawyer-003', 'client-009', DATE_SUB(NOW(), INTERVAL 5 DAY)),

-- Dossiers de Julie Moreau (lawyer-004)
('case-013', 'Droit pénal Renard', 'Défense dans une affaire de diffamation', 'OPEN', 'lawyer-004', 'client-011', DATE_SUB(NOW(), INTERVAL 18 DAY)),
('case-014', 'Contentieux familial Dupuis', 'Pension alimentaire - révision', 'IN_PROGRESS', 'lawyer-004', 'client-012', DATE_SUB(NOW(), INTERVAL 28 DAY)),
('case-015', 'Dossier archivé Renard', 'Ancien dossier de référence', 'ARCHIVED', 'lawyer-004', 'client-011', DATE_SUB(NOW(), INTERVAL 200 DAY));

-- ============================================
-- FACTURES (INVOICES)
-- ============================================
INSERT INTO invoices (id, invoice_number, client_id, invoice_date, due_date, paid_date, status, total_ht, total_tva, total_ttc, currency, notes, payment_method, created_at) VALUES
-- Factures de Jean Dupont (lawyer-001)
('invoice-001', 'FACT-2026-00001', 'client-001', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 30 DAY), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 'PAID', 2500.00, 500.00, 3000.00, 'EUR', 'Honoraires pour consultation et première procédure', 'Virement bancaire', DATE_SUB(NOW(), INTERVAL 30 DAY)),
('invoice-002', 'FACT-2026-00002', 'client-002', DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL 30 DAY), NULL, 'SENT', 3500.00, 700.00, 4200.00, 'EUR', 'Frais de dossier succession', NULL, DATE_SUB(NOW(), INTERVAL 25 DAY)),
('invoice-003', 'FACT-2026-00003', 'client-003', DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, 'OVERDUE', 4200.00, 840.00, 5040.00, 'EUR', 'Honoraires contentieux commercial - phase 1', NULL, DATE_SUB(NOW(), INTERVAL 45 DAY)),
('invoice-004', 'FACT-2026-00004', 'client-004', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 30 DAY), NULL, 'SENT', 1800.00, 360.00, 2160.00, 'EUR', 'Consultation initiale et analyse du dossier prud''homal', NULL, DATE_SUB(NOW(), INTERVAL 10 DAY)),

-- Factures de Sophie Bernard (lawyer-002)
('invoice-005', 'FACT-2026-00005', 'client-005', DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 15 DAY), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 'PAID', 1500.00, 300.00, 1800.00, 'EUR', 'Expertise vice caché immobilier', 'Chèque', DATE_SUB(NOW(), INTERVAL 15 DAY)),
('invoice-006', 'FACT-2026-00006', 'client-006', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL 30 DAY), NULL, 'SENT', 2200.00, 440.00, 2640.00, 'EUR', 'Procédure contentieux locatif', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY)),
('invoice-007', 'FACT-2026-00007', 'client-007', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 DAY), INTERVAL 30 DAY), NULL, 'SENT', 3000.00, 600.00, 3600.00, 'EUR', 'Dossier indemnisation accident', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),

-- Factures de Marc Dubois (lawyer-003)
('invoice-008', 'FACT-2026-00008', 'client-008', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'PAID', 5000.00, 1000.00, 6000.00, 'EUR', 'Création de société - conseil juridique', 'Virement bancaire', DATE_SUB(NOW(), INTERVAL 20 DAY)),
('invoice-009', 'FACT-2026-00009', 'client-009', DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 'OVERDUE', 8500.00, 1700.00, 10200.00, 'EUR', 'Contentieux fiscal - phase préparatoire', NULL, DATE_SUB(NOW(), INTERVAL 35 DAY)),
('invoice-010', 'FACT-2026-00010', 'client-010', DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 40 DAY), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), 'PAID', 12000.00, 2400.00, 14400.00, 'EUR', 'Due diligence acquisition société', 'Virement bancaire', DATE_SUB(NOW(), INTERVAL 40 DAY)),

-- Factures de Julie Moreau (lawyer-004)
('invoice-011', 'FACT-2026-00011', 'client-011', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 DAY), INTERVAL 30 DAY), NULL, 'SENT', 1800.00, 360.00, 2160.00, 'EUR', 'Défense pénale - consultation et analyse', NULL, DATE_SUB(NOW(), INTERVAL 12 DAY)),
('invoice-012', 'FACT-2026-00012', 'client-012', DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL 30 DAY), NULL, 'SENT', 1200.00, 240.00, 1440.00, 'EUR', 'Révision pension alimentaire', NULL, DATE_SUB(NOW(), INTERVAL 25 DAY)),

-- Facture brouillon
('invoice-013', 'FACT-2026-00013', 'client-001', NOW(), NULL, NULL, 'DRAFT', 0.00, 0.00, 0.00, 'EUR', 'Facture en cours de préparation', NULL, NOW());

-- ============================================
-- LIGNES DE FACTURE (INVOICE_ITEMS)
-- ============================================
INSERT INTO invoice_items (id, invoice_id, description, quantity, unit_price_ht, tva_rate, total_ht, total_tva, total_ttc, display_order) VALUES
-- Lignes pour FACT-2026-00001
('item-001', 'invoice-001', 'Consultation initiale (2 heures)', 2.00, 250.00, 20.00, 500.00, 100.00, 600.00, 1),
('item-002', 'invoice-001', 'Rédaction requête divorce', 1.00, 1200.00, 20.00, 1200.00, 240.00, 1440.00, 2),
('item-003', 'invoice-001', 'Correspondances et démarches', 1.00, 800.00, 20.00, 800.00, 160.00, 960.00, 3),

-- Lignes pour FACT-2026-00002
('item-004', 'invoice-002', 'Analyse dossier succession', 1.00, 1500.00, 20.00, 1500.00, 300.00, 1800.00, 1),
('item-005', 'invoice-002', 'Rédaction acte de partage', 1.00, 2000.00, 20.00, 2000.00, 400.00, 2400.00, 2),

-- Lignes pour FACT-2026-00003
('item-006', 'invoice-003', 'Étude du contrat commercial', 1.00, 1200.00, 20.00, 1200.00, 240.00, 1440.00, 1),
('item-007', 'invoice-003', 'Rédaction conclusions', 1.00, 2000.00, 20.00, 2000.00, 400.00, 2400.00, 2),
('item-008', 'invoice-003', 'Préparation audience', 1.00, 1000.00, 20.00, 1000.00, 200.00, 1200.00, 3),

-- Lignes pour FACT-2026-00004
('item-009', 'invoice-004', 'Consultation droit du travail', 1.00, 1800.00, 20.00, 1800.00, 360.00, 2160.00, 1),

-- Lignes pour FACT-2026-00005
('item-010', 'invoice-005', 'Expertise technique immobilière', 1.00, 1500.00, 20.00, 1500.00, 300.00, 1800.00, 1),

-- Lignes pour FACT-2026-00006
('item-011', 'invoice-006', 'Procédure contentieux locatif', 1.00, 2200.00, 20.00, 2200.00, 440.00, 2640.00, 1),

-- Lignes pour FACT-2026-00007
('item-012', 'invoice-007', 'Constitution dossier indemnisation', 1.00, 1500.00, 20.00, 1500.00, 300.00, 1800.00, 1),
('item-013', 'invoice-007', 'Négociation avec assurance', 1.00, 1500.00, 20.00, 1500.00, 300.00, 1800.00, 2),

-- Lignes pour FACT-2026-00008
('item-014', 'invoice-008', 'Conseil création société', 1.00, 3000.00, 20.00, 3000.00, 600.00, 3600.00, 1),
('item-015', 'invoice-008', 'Rédaction statuts', 1.00, 2000.00, 20.00, 2000.00, 400.00, 2400.00, 2),

-- Lignes pour FACT-2026-00009
('item-016', 'invoice-009', 'Analyse redressement fiscal', 1.00, 4000.00, 20.00, 4000.00, 800.00, 4800.00, 1),
('item-017', 'invoice-009', 'Préparation recours', 1.00, 3000.00, 20.00, 3000.00, 600.00, 3600.00, 2),
('item-018', 'invoice-009', 'Correspondances administration', 1.00, 1500.00, 20.00, 1500.00, 300.00, 1800.00, 3),

-- Lignes pour FACT-2026-00010
('item-019', 'invoice-010', 'Due diligence juridique', 1.00, 8000.00, 20.00, 8000.00, 1600.00, 9600.00, 1),
('item-020', 'invoice-010', 'Rédaction protocole acquisition', 1.00, 4000.00, 20.00, 4000.00, 800.00, 4800.00, 2),

-- Lignes pour FACT-2026-00011
('item-021', 'invoice-011', 'Consultation pénale et analyse', 1.00, 1800.00, 20.00, 1800.00, 360.00, 2160.00, 1),

-- Lignes pour FACT-2026-00012
('item-022', 'invoice-012', 'Dossier révision pension', 1.00, 1200.00, 20.00, 1200.00, 240.00, 1440.00, 1);

-- ============================================
-- AFFICHAGE FINAL
-- ============================================
SELECT '✅ Base de données GED Avocat créée avec succès !' AS Status;
SELECT COUNT(*) AS NombreUtilisateurs FROM users;
SELECT COUNT(*) AS NombreClients FROM clients;
SELECT COUNT(*) AS NombreDossiers FROM cases;
SELECT COUNT(*) AS NombreFactures FROM invoices;
SELECT COUNT(*) AS NombreLignesFacture FROM invoice_items;

SET FOREIGN_KEY_CHECKS = 1;