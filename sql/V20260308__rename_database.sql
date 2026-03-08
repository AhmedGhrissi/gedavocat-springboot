-- Migration: Renommer la base gedavocat -> doc_avocat
-- Date: 2026-03-08
-- Attention: Cette migration nécessite des privilèges root MySQL

-- Créer la nouvelle base de données
CREATE DATABASE IF NOT EXISTS `doc_avocat` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Copier toutes les tables de gedavocat vers doc_avocat
-- Note: Cette approche nécessite de lister toutes les tables ou d'utiliser mysqldump
-- Pour l'instant, on suppose que les tables seront migrées par Flyway ou manuellement

-- Créer le nouvel utilisateur
CREATE USER IF NOT EXISTS 'doc_avocat'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';

-- Accorder tous les privilèges sur la nouvelle base
GRANT ALL PRIVILEGES ON `doc_avocat`.* TO 'doc_avocat'@'%';

-- Flush privileges
FLUSH PRIVILEGES;

-- Note: La suppression de l'ancienne base et de l'ancien utilisateur doit être faite manuellement
-- après vérification que la migration est réussie:
-- DROP DATABASE IF EXISTS `gedavocat`;
-- DROP USER IF EXISTS 'gedavocat'@'%';
