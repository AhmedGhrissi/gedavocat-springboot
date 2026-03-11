-- Migration : ajout colonne encrypted sur la table documents
-- Compatibilité : MySQL 8.0+
-- Contexte : chiffrement AES-256-GCM des fichiers au repos (RGPD art. 32)

ALTER TABLE documents ADD COLUMN IF NOT EXISTS encrypted TINYINT(1) NOT NULL DEFAULT 0;

-- Les documents existants ne sont pas chiffrés → valeur par défaut = 0 (false)
