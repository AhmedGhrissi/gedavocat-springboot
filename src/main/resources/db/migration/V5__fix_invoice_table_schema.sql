-- ========================================
-- MIGRATION V5: CORRECTION SCHEMA INVOICES
-- Date: 2026-03-03
-- Objectif: Aligner le schéma de la table invoices avec l'entité Java
-- ========================================

-- Étape 1: Supprimer les contraintes NOT NULL des anciennes colonnes si elles existent
ALTER TABLE invoices MODIFY COLUMN invoice_date DATE NULL;
ALTER TABLE invoices MODIFY COLUMN total_ht DECIMAL(10,2) NULL;
ALTER TABLE invoices MODIFY COLUMN total_tva DECIMAL(10,2) NULL;
ALTER TABLE invoices MODIFY COLUMN total_ttc DECIMAL(10,2) NULL;

-- Étape 2: Ajouter les nouvelles colonnes si elles n'existent pas
ALTER TABLE invoices 
    ADD COLUMN IF NOT EXISTS issue_date DATE,
    ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(10,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(10,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_amount DECIMAL(10,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS paid_amount DECIMAL(10,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) DEFAULT 'UNPAID',
    ADD COLUMN IF NOT EXISTS case_id VARCHAR(36);

-- Étape 3: Migrer les données des anciennes colonnes vers les nouvelles
UPDATE invoices 
SET 
    issue_date = COALESCE(issue_date, invoice_date, CURRENT_DATE),
    subtotal_amount = COALESCE(subtotal_amount, total_ht, 0.00),
    tax_amount = COALESCE(tax_amount, total_tva, 0.00),
    total_amount = COALESCE(total_amount, total_ttc, 0.00)
WHERE issue_date IS NULL OR subtotal_amount IS NULL OR tax_amount IS NULL OR total_amount IS NULL;

-- Étape 4: Ajouter les contraintes NOT NULL sur les nouvelles colonnes
ALTER TABLE invoices MODIFY COLUMN issue_date DATE NOT NULL;
ALTER TABLE invoices MODIFY COLUMN total_amount DECIMAL(10,2) NOT NULL;

-- Étape 5: Supprimer les anciennes colonnes
ALTER TABLE invoices 
    DROP COLUMN IF EXISTS invoice_date,
    DROP COLUMN IF EXISTS total_ht,
    DROP COLUMN IF EXISTS total_tva,
    DROP COLUMN IF EXISTS total_ttc;

-- Étape 6: Ajouter les index manquants
ALTER TABLE invoices ADD INDEX IF NOT EXISTS idx_invoice_case_id (case_id);
ALTER TABLE invoices ADD INDEX IF NOT EXISTS idx_invoice_payment_status (payment_status);

-- Étape 7: Ajouter la contrainte de clé étrangère pour case_id
ALTER TABLE invoices 
    ADD CONSTRAINT fk_invoice_case 
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL;
