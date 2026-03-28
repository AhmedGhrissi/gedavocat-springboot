-- Migration: ajout table document_deletion_requests
-- Date: 2026-03-28
-- Contexte: workflow demande de suppression de document (client → avocat)

CREATE TABLE IF NOT EXISTS document_deletion_requests (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    requested_by_id VARCHAR(36) NOT NULL,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_id VARCHAR(36),
    reviewed_at DATETIME,
    review_comment TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ddr_document (document_id),
    INDEX idx_ddr_requester (requested_by_id),
    INDEX idx_ddr_status (status),
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (requested_by_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
