-- ============================================================
-- DocAvocat — Migration : ajout des colonnes version (optimistic locking)
-- Date : 2026-03-02
-- Contexte : ajout de @Version sur toutes les entités pour le
--            verrouillage optimiste.
--            Nécessaire car ddl-auto=validate en prod.
-- Idempotent : ADD COLUMN IF NOT EXISTS
-- ============================================================

USE gedavocat;

-- ── Tables avec @Version ─────────────────────────────────────

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE clients
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE cases
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE documents
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE appointments
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE invoices
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE signatures
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE permissions
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE document_shares
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE case_share_links
  ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- ── Colonnes users manquantes ────────────────────────────────

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS email_verified TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS account_enabled TINYINT(1) NOT NULL DEFAULT 1;
