-- ============================================================
-- DocAvocat — Migration : ajout des colonnes version (optimistic locking)
-- Date : 2026-03-02
-- Contexte : ajout de @Version sur Notification, Permission,
--            DocumentShare, CaseShareLink pour le verrouillage optimiste.
--            Nécessaire car ddl-auto=validate en prod.
-- ============================================================

USE gedavocat;

-- notifications
ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- permissions
ALTER TABLE permissions
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- document_shares
ALTER TABLE document_shares
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- case_share_links
ALTER TABLE case_share_links
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- email_verified / account_enabled sur users (si absents)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS email_verified TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS account_enabled TINYINT(1) NOT NULL DEFAULT 1;
