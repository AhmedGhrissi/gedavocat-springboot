-- ============================================
-- CORRECTION DE LA TABLE CASES
-- Supprime les colonnes title et case_number
-- ============================================

USE gedavocat;

-- Supprimer la colonne 'title' si elle existe
SET @column_title_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'gedavocat' 
    AND TABLE_NAME = 'cases' 
    AND COLUMN_NAME = 'title'
);

SET @sql_drop_title = IF(@column_title_exists > 0,
    'ALTER TABLE cases DROP COLUMN title',
    'SELECT "Colonne title déjà supprimée" AS message'
);

PREPARE stmt1 FROM @sql_drop_title;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- Supprimer la colonne 'case_number' si elle existe
SET @column_case_number_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'gedavocat' 
    AND TABLE_NAME = 'cases' 
    AND COLUMN_NAME = 'case_number'
);

SET @sql_drop_case_number = IF(@column_case_number_exists > 0,
    'ALTER TABLE cases DROP COLUMN case_number',
    'SELECT "Colonne case_number déjà supprimée" AS message'
);

PREPARE stmt2 FROM @sql_drop_case_number;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Vérifier que la colonne 'name' existe et a la bonne taille
ALTER TABLE cases MODIFY COLUMN name VARCHAR(255) NOT NULL;

-- Mettre à jour le ENUM du statut pour inclure IN_PROGRESS
ALTER TABLE cases MODIFY COLUMN status ENUM('OPEN','IN_PROGRESS','CLOSED','ARCHIVED') NOT NULL DEFAULT 'OPEN';

-- Afficher la structure finale
SELECT '✅ Table cases corrigée avec succès !' AS Status;
DESCRIBE cases;
