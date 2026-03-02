-- ========================================
-- MIGRATION V3: SUPPORT MULTI-TENANT
-- Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.1 VULN-01
-- Date: 2026-02-27
-- Objectif: Isolation complète des données par cabinet (firmId)
-- ========================================

-- 1. CRÉATION DE LA TABLE FIRMS (CABINETS D'AVOCATS)
-- ========================================

CREATE TABLE IF NOT EXISTS firms (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(20),
    email VARCHAR(255),
    siren VARCHAR(14),
    tva_number VARCHAR(20),
    
    -- Abonnement du cabinet
    subscription_plan VARCHAR(20) DEFAULT 'SOLO',
    subscription_status VARCHAR(20) DEFAULT 'TRIAL',
    subscription_starts_at DATETIME,
    subscription_ends_at DATETIME,
    max_lawyers INT NOT NULL DEFAULT 1,
    max_clients INT NOT NULL DEFAULT 10,
    
    -- Stripe (paiements)
    stripe_customer_id VARCHAR(100),
    stripe_subscription_id VARCHAR(100),
    
    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Index
    INDEX idx_firm_created_at (created_at),
    INDEX idx_firm_subscription_status (subscription_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. AJOUT DE LA COLONNE firm_id À TOUTES LES TABLES
-- ========================================

-- Users
ALTER TABLE users 
    ADD COLUMN firm_id VARCHAR(36) AFTER role,
    ADD INDEX idx_user_firm_id (firm_id);

-- Cases (Dossiers)
ALTER TABLE cases 
    ADD COLUMN firm_id VARCHAR(36) AFTER id,
    ADD INDEX idx_case_firm_id (firm_id);

-- Documents
ALTER TABLE documents 
    ADD COLUMN firm_id VARCHAR(36) AFTER id,
    ADD INDEX idx_document_firm_id (firm_id);

-- Clients
ALTER TABLE clients 
    ADD COLUMN firm_id VARCHAR(36) AFTER id,
    ADD INDEX idx_client_firm_id (firm_id);

-- Invoices (si la table existe)
ALTER TABLE invoices 
    ADD COLUMN firm_id VARCHAR(36) AFTER id,
    ADD INDEX idx_invoice_firm_id (firm_id);

-- Appointments (si la table existe)
ALTER TABLE appointments 
    ADD COLUMN firm_id VARCHAR(36) AFTER id,
    ADD INDEX idx_appointment_firm_id (firm_id);

-- 3. CRÉATION D'UN CABINET PAR DÉFAUT (MIGRATION DES DONNÉES EXISTANTES)
-- ========================================

INSERT INTO firms (id, name, subscription_plan, subscription_status, max_lawyers, max_clients, created_at, updated_at)
VALUES (UUID(), 'Cabinet Principal (Migration)', 'ENTERPRISE', 'ACTIVE', 999, 999, NOW(), NOW());

-- Récupérer l'ID du cabinet créé pour la migration
SET @default_firm_id = (SELECT id FROM firms WHERE name = 'Cabinet Principal (Migration)' LIMIT 1);

-- 4. MIGRATION DES DONNÉES EXISTANTES VERS LE CABINET PAR DÉFAUT
-- ========================================

-- Mise à jour des utilisateurs existants
UPDATE users SET firm_id = @default_firm_id WHERE firm_id IS NULL;

-- Mise à jour des dossiers existants
UPDATE cases SET firm_id = @default_firm_id WHERE firm_id IS NULL;

-- Mise à jour des documents existants
UPDATE documents SET firm_id = @default_firm_id WHERE firm_id IS NULL;

-- Mise à jour des clients existants
UPDATE clients SET firm_id = @default_firm_id WHERE firm_id IS NULL;

-- Mise à jour des factures existantes
UPDATE invoices SET firm_id = @default_firm_id WHERE firm_id IS NULL;

-- Mise à jour des rendez-vous existants
UPDATE appointments SET firm_id = @default_firm_id WHERE firm_id IS NULL;

-- 5. AJOUT DES CONTRAINTES DE CLÉ ÉTRANGÈRE
-- ========================================

-- Users -> Firms (NULL autorisé pour les ADMIN)
ALTER TABLE users 
    ADD CONSTRAINT fk_user_firm 
    FOREIGN KEY (firm_id) REFERENCES firms(id) 
    ON DELETE RESTRICT;

-- Cases -> Firms (OBLIGATOIRE)
ALTER TABLE cases 
    MODIFY COLUMN firm_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT fk_case_firm 
    FOREIGN KEY (firm_id) REFERENCES firms(id) 
    ON DELETE RESTRICT;

-- Documents -> Firms (OBLIGATOIRE)
ALTER TABLE documents 
    MODIFY COLUMN firm_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT fk_document_firm 
    FOREIGN KEY (firm_id) REFERENCES firms(id) 
    ON DELETE RESTRICT;

-- Clients -> Firms (OBLIGATOIRE)
ALTER TABLE clients 
    MODIFY COLUMN firm_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT fk_client_firm 
    FOREIGN KEY (firm_id) REFERENCES firms(id) 
    ON DELETE RESTRICT;

-- Invoices -> Firms (OBLIGATOIRE)
ALTER TABLE invoices 
    MODIFY COLUMN firm_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT fk_invoice_firm 
    FOREIGN KEY (firm_id) REFERENCES firms(id) 
    ON DELETE RESTRICT;

-- Appointments -> Firms (OBLIGATOIRE)
ALTER TABLE appointments 
    MODIFY COLUMN firm_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT fk_appointment_firm 
    FOREIGN KEY (firm_id) REFERENCES firms(id) 
    ON DELETE RESTRICT;

-- ========================================
-- FIN DE LA MIGRATION V3
-- ========================================
