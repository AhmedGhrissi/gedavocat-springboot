-- ========================================
-- MIGRATION V4: REFRESH TOKENS + JWT RS256
-- Référence: docs/RAPPORT_AUDIT_SECURITE_Phase1.md §6.3
-- Date: 2026-02-27
-- Objectif: Système de refresh tokens pour renouvellement sécurisé
-- ========================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token TEXT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME,
    device_fingerprint VARCHAR(500),
    ip_address VARCHAR(45),
    
    -- Contrainte d'unicité sur le token
    UNIQUE KEY unique_token (token(255)),
    
    -- Index
    INDEX idx_refresh_token_user_id (user_id),
    INDEX idx_refresh_token_expires_at (expires_at),
    
    -- Clé étrangère
    CONSTRAINT fk_refresh_token_user 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- FIN DE LA MIGRATION V4
-- ========================================
