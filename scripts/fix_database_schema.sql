-- ========================================
-- SCRIPT DE CORRECTION COMPLÈTE DU SCHÉMA DE BASE DE DONNÉES
-- Date: 2026-03-03
-- Objectif: Corriger toutes les tables pour correspondre aux entités Java
-- ATTENTION: Ce script supprime les données existantes dans plusieurs tables
-- ========================================

USE gedavocat;

-- ========================================
-- 1. CORRECTION DE LA TABLE APPOINTMENTS
-- ========================================

DROP TABLE IF EXISTS appointments;

CREATE TABLE appointments (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) DEFAULT 'SCHEDULED',
    entity_version BIGINT,
    
    -- Relations
    case_id VARCHAR(36),
    lawyer_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36),
    firm_id VARCHAR(36) NOT NULL,
    
    -- Dates/Planning
    start_time DATETIME(6),
    end_time DATETIME(6),
    is_all_day TINYINT(1) DEFAULT 0,
    
    -- Anciennes colonnes pour compatibilité
    appointment_date DATETIME(6),
    end_date DATETIME(6),
    
    -- Informations tribunal
    court_name VARCHAR(200),
    court_room VARCHAR(50),
    judge_name VARCHAR(100),
    
    -- Rappels
    send_reminder TINYINT(1) DEFAULT 1,
    reminder_sent TINYINT(1) DEFAULT 0,
    reminder_minutes_before INT DEFAULT 60,
    
    -- Autres
    notes TEXT,
    video_conference_link VARCHAR(500),
    client_confirmed_at DATETIME(6),
    reschedule_requested_by VARCHAR(20),
    proposed_date DATETIME(6),
    reschedule_message VARCHAR(500),
    color VARCHAR(7) DEFAULT '#3788d8',
    
    -- Timestamps
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Contraintes
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
    INDEX idx_appointment_date (appointment_date),
    INDEX idx_appointment_type (type),
    INDEX idx_appointment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 2. CORRECTION DE LA TABLE INVOICES
-- ========================================

DROP TABLE IF EXISTS invoice_items;
DROP TABLE IF EXISTS invoices;

CREATE TABLE invoices (
    id VARCHAR(36) PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    entity_version BIGINT,
    
    -- Relations
    client_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36),
    firm_id VARCHAR(36) NOT NULL,
    
    -- Dates
    issue_date DATE,
    due_date DATE,
    paid_date DATE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    -- Statuts
    status VARCHAR(50) DEFAULT 'DRAFT',
    payment_status VARCHAR(50) DEFAULT 'UNPAID',
    
    -- Montants
    subtotal_amount DECIMAL(10,2) DEFAULT 0.00,
    tax_amount DECIMAL(10,2) DEFAULT 0.00,
    total_amount DECIMAL(10,2) DEFAULT 0.00,
    paid_amount DECIMAL(10,2) DEFAULT 0.00,
    
    -- Autres champs
    currency VARCHAR(3) DEFAULT 'EUR',
    notes TEXT,
    payment_method VARCHAR(50),
    document_url VARCHAR(500),
    
    -- Anciennes colonnes pour compatibilité (lecture seule depuis Java)
    invoice_date DATE,
    total_ht DECIMAL(10,2),
    total_tva DECIMAL(10,2),
    total_ttc DECIMAL(10,2),
    
    -- Contraintes
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
    
    -- Contraintes
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_invoice_item_invoice_id (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- 3. VÉRIFICATION DE LA TABLE CASES
-- ========================================

-- S'assurer que les colonnes peuvent être NULL ou ont des valeurs par défaut
ALTER TABLE cases MODIFY COLUMN name VARCHAR(255) NULL;
ALTER TABLE cases MODIFY COLUMN title VARCHAR(255) NULL;
ALTER TABLE cases MODIFY COLUMN reference VARCHAR(50) NULL;
ALTER TABLE cases MODIFY COLUMN type VARCHAR(50) NULL;

-- ========================================
-- 4. AFFICHAGE DES SCHÉMAS POUR VÉRIFICATION
-- ========================================

SELECT 'Migration terminée avec succès!' AS status;
SELECT 'Vérification des tables :' AS info;

DESCRIBE appointments;
DESCRIBE invoices;
DESCRIBE invoice_items;
DESCRIBE cases;
