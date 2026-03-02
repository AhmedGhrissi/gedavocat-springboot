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

-- ── Tables avec @Version ─────────────────────────────────────

CALL add_column_if_not_exists('users',            'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('clients',          'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('cases',            'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('documents',        'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('appointments',     'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('invoices',         'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('signatures',       'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('notifications',    'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('permissions',      'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('document_shares',  'version', 'BIGINT DEFAULT 0');
CALL add_column_if_not_exists('case_share_links', 'version', 'BIGINT DEFAULT 0');

-- ── Colonnes users manquantes ────────────────────────────────

CALL add_column_if_not_exists('users', 'email_verified',  'TINYINT(1) NOT NULL DEFAULT 1');
CALL add_column_if_not_exists('users', 'account_enabled', 'TINYINT(1) NOT NULL DEFAULT 1');

-- Nettoyage
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
