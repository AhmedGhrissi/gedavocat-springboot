-- ===================================================================
-- Migration: Correction de la table cases
-- Remplace le champ 'title' par 'name' et ajoute les champs manquants
-- ===================================================================

USE gedavocat;

-- Vérifier si la colonne 'title' existe et la renommer en 'name'
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'gedavocat' 
    AND TABLE_NAME = 'cases' 
    AND COLUMN_NAME = 'title'
);

-- Renommer 'title' en 'name' si elle existe
SET @sql = IF(@column_exists > 0,
    'ALTER TABLE cases CHANGE COLUMN title name VARCHAR(200) NOT NULL',
    'SELECT "Colonne title n\'existe pas, aucune action nécessaire" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Vérifier si la colonne 'name' existe (si title n'existait pas)
SET @name_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'gedavocat' 
    AND TABLE_NAME = 'cases' 
    AND COLUMN_NAME = 'name'
);

-- Ajouter 'name' si elle n'existe pas du tout
SET @sql2 = IF(@name_exists = 0,
    'ALTER TABLE cases ADD COLUMN name VARCHAR(200) NOT NULL AFTER client_id',
    'SELECT "Colonne name existe déjà" AS message'
);

PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Modifier le type ENUM du statut pour inclure IN_PROGRESS si nécessaire
ALTER TABLE cases MODIFY COLUMN status ENUM('OPEN', 'IN_PROGRESS', 'CLOSED', 'ARCHIVED') NOT NULL DEFAULT 'OPEN';

-- Afficher le résultat
SELECT 'Migration terminée avec succès !' AS message;
SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'gedavocat' 
AND TABLE_NAME = 'cases' 
ORDER BY ORDINAL_POSITION;
