-- Migration SQL pour les tables de facturation
-- Date: 2026-02-19
-- Description: Création des tables invoices et invoice_items

-- Table des factures
CREATE TABLE IF NOT EXISTS invoices (
    id VARCHAR(36) PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    client_id VARCHAR(36) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    paid_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_ht DECIMAL(10, 2) NOT NULL,
    total_tva DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_ttc DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    notes TEXT,
    payment_method VARCHAR(50),
    document_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    INDEX idx_invoice_client_id (client_id),
    INDEX idx_invoice_number (invoice_number),
    INDEX idx_invoice_status (status),
    INDEX idx_invoice_date (invoice_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table des lignes de facture
CREATE TABLE IF NOT EXISTS invoice_items (
    id VARCHAR(36) PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL,
    unit_price_ht DECIMAL(10, 2) NOT NULL,
    tva_rate DECIMAL(5, 2) NOT NULL DEFAULT 20.00,
    total_ht DECIMAL(10, 2),
    total_tva DECIMAL(10, 2),
    total_ttc DECIMAL(10, 2),
    display_order INT,
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    INDEX idx_invoice_item_invoice_id (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Commentaires sur les tables
ALTER TABLE invoices COMMENT = 'Table des factures liées aux clients';
ALTER TABLE invoice_items COMMENT = 'Table des lignes de facture (détails des prestations)';
