-- =====================================================================
-- Migration : Ajout du rôle AVOCAT_ADMIN
-- Date      : 2026-03-07
-- Description : Ajoute la valeur 'AVOCAT_ADMIN' à la colonne role
--               de la table users pour permettre la gestion du cabinet.
-- =====================================================================

-- Si la colonne role est de type ENUM MySQL, modifier l'ENUM :
ALTER TABLE users
    MODIFY COLUMN role ENUM('ADMIN', 'LAWYER', 'AVOCAT_ADMIN', 'CLIENT', 'LAWYER_SECONDARY', 'HUISSIER') NOT NULL;

-- Si la colonne role est de type VARCHAR (vérifier avec : SHOW COLUMNS FROM users LIKE 'role';)
-- alors aucune modification de schéma n'est nécessaire, la valeur 'AVOCAT_ADMIN' (12 chars)
-- tient dans VARCHAR(20).

-- =====================================================================
-- Pour promouvoir un utilisateur existant en AVOCAT_ADMIN :
-- UPDATE users SET role = 'AVOCAT_ADMIN' WHERE email = 'avocat@exemple.fr';
-- =====================================================================
