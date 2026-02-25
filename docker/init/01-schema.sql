-- ============================================================
-- DocAvocat — Schéma initial de la base de données
--
-- Ce fichier est une RÉFÉRENCE pour les installations from scratch.
-- Il est monté automatiquement par MySQL au premier démarrage
-- du conteneur (volume mysql_data inexistant).
--
-- Sur un système existant, Hibernate (ddl-auto=update) gère
-- les migrations automatiquement — ce fichier n'est pas rejoué.
--
-- Pour activer sur un fresh install, décommenter dans
-- docker/docker-compose.yml :
--   - ./init/01-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS gedavocat
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE gedavocat;

-- ── users ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NULL,
    last_name VARCHAR(100) NULL,
    phone VARCHAR(20) NULL,
    bar_number VARCHAR(50) NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','LAWYER','CLIENT','LAWYER_SECONDARY','HUISSIER') NOT NULL,
    subscription_plan ENUM('ESSENTIEL','PROFESSIONNEL','CABINET_PLUS') NULL,
    subscription_status ENUM('ACTIVE','INACTIVE','CANCELLED','TRIAL') NULL,
    subscription_start_date DATETIME NULL,
    subscription_ends_at DATETIME NULL,
    max_clients INT DEFAULT 10,
    gdpr_consent_at DATETIME NULL,
    terms_accepted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    access_ends_at DATETIME NULL,
    invitation_id VARCHAR(36) NULL,
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── clients ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS clients (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    phone VARCHAR(50) NULL,
    address TEXT NULL,
    notes TEXT NULL,
    client_type ENUM('INDIVIDUAL','COMPANY') DEFAULT 'INDIVIDUAL',
    lawyer_id VARCHAR(36) NULL,
    client_user_id VARCHAR(36) NULL,
    invitation_id VARCHAR(36) NULL,
    invited_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lawyer_id (lawyer_id),
    INDEX idx_client_user_id (client_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Les autres tables sont créées automatiquement par Hibernate (ddl-auto=update)
-- lors du premier démarrage de l'application.

SET FOREIGN_KEY_CHECKS = 1;
