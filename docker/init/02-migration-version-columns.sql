-- ============================================================
-- DocAvocat — Migration : ajout des colonnes version (optimistic locking)
-- Date : 2026-03-02
-- Contexte : ajout de @Version sur toutes les entités pour le
--            verrouillage optimiste.
--            Nécessaire car ddl-auto=validate en prod.
-- Compatible MySQL 5.7+ (pas de IF NOT EXISTS sur ALTER TABLE)
-- ============================================================

USE gedavocat;

DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_not_exists //

CREATE PROCEDURE add_column_if_not_exists(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

DELIMITER ;

-- ── Tables avec @Version (colonne = entity_version) ─────────

CALL add_column_if_not_exists('users',            'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('clients',          'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('cases',            'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('documents',        'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('appointments',     'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('invoices',         'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('signatures',       'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('notifications',    'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('permissions',      'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('document_shares',  'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('case_share_links', 'entity_version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('labft_checks',     'entity_version', 'BIGINT DEFAULT 0');

-- ── Colonnes users manquantes ────────────────────────────────

CALL add_column_if_not_exists('users', 'email_verified',  'TINYINT(1) NOT NULL DEFAULT 1');
CALL add_column_if_not_exists('users', 'account_enabled', 'TINYINT(1) NOT NULL DEFAULT 1');

-- Nettoyage
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
