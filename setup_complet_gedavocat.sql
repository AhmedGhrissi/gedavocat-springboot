-- ============================================================================
-- SCRIPT COMPLET DE MISE À JOUR GEDAVOCAT
-- Date: 2026-03-06
-- Description: Script complet pour importer le dump et ajouter les utilisateurs
-- ============================================================================

-- Désactiver les vérifications de clés étrangères temporairement
SET FOREIGN_KEY_CHECKS=0;
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

-- ============================================================================
-- PARTIE 1: Charger le dump principal
-- ============================================================================
SOURCE dump_gedavocat_prod_20260305_backup.sql;

-- ============================================================================
-- PARTIE 2: Ajouter les nouveaux utilisateurs
-- ============================================================================

-- Super Administrateur
INSERT INTO `users` 
(`id`, `email`, `password`, `first_name`, `last_name`, `phone`, `role`, `firm_id`, 
 `enabled`, `account_non_locked`, `failed_login_attempts`, `last_login`, `password_changed_at`, 
 `created_at`, `updated_at`, `access_ends_at`, `account_enabled`, `bar_number`, `barreau_id`, 
 `email_signature`, `email_verified`, `gdpr_consent_at`, `invitation_id`, `max_clients`, 
 `name`, `reset_token`, `reset_token_expiry`, `stripe_customer_id`, `billing_period`, 
 `subscription_ends_at`, `subscription_plan`, `subscription_start_date`, `subscription_status`, 
 `terms_accepted_at`, `entity_version`, `mfa_backup_codes`, `mfa_enabled`, `mfa_last_used`, 
 `mfa_secret`, `mfa_temp_setup`, `stripe_subscription_id`)
VALUES 
('admin-super-001', 'superadmin@gedavocat.fr', '$2a$12$S0in8qlrAXxQeT1T9dwrfuiWMDQ1D7Q04vrkV5SMKR8lIUiB.aATi', 
 'Super', 'Admin', '+33 6 12 34 56 78', 'ADMIN', NULL, 
 1, 1, 0, '2026-03-06 10:00:00.000000', NULL, 
 '2026-03-06 10:00:00.000000', '2026-03-06 10:00:00.000000', NULL, _binary '', NULL, NULL, 
 NULL, _binary '', '2026-03-06 10:00:00.000000', NULL, NULL, 
 'Super Admin', NULL, NULL, NULL, NULL, 
 NULL, NULL, NULL, NULL, 
 '2026-03-06 10:00:00.000000', 0, NULL, _binary '', NULL, 
 NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE 
  email = VALUES(email),
  updated_at = VALUES(updated_at);

-- Client de démonstration
INSERT INTO `users` 
(`id`, `email`, `password`, `first_name`, `last_name`, `phone`, `role`, `firm_id`, 
 `enabled`, `account_non_locked`, `failed_login_attempts`, `last_login`, `password_changed_at`, 
 `created_at`, `updated_at`, `access_ends_at`, `account_enabled`, `bar_number`, `barreau_id`, 
 `email_signature`, `email_verified`, `gdpr_consent_at`, `invitation_id`, `max_clients`, 
 `name`, `reset_token`, `reset_token_expiry`, `stripe_customer_id`, `billing_period`, 
 `subscription_ends_at`, `subscription_plan`, `subscription_start_date`, `subscription_status`, 
 `terms_accepted_at`, `entity_version`, `mfa_backup_codes`, `mfa_enabled`, `mfa_last_used`, 
 `mfa_secret`, `mfa_temp_setup`, `stripe_subscription_id`)
VALUES 
('client-demo-002', 'client.demo@gedavocat.fr', '$2a$12$S0in8qlrAXxQeT1T9dwrfuiWMDQ1D7Q04vrkV5SMKR8lIUiB.aATi', 
 'Pierre', 'Martin', '+33 6 98 76 54 32', 'CLIENT', 'firm-demo-001', 
 1, 1, 0, '2026-03-06 09:30:00.000000', NULL, 
 '2026-03-06 09:30:00.000000', '2026-03-06 09:30:00.000000', '2027-03-06 09:30:00.000000', _binary '', NULL, NULL, 
 NULL, _binary '', '2026-03-06 09:30:00.000000', NULL, NULL, 
 'Pierre Martin', NULL, NULL, NULL, NULL, 
 NULL, NULL, NULL, NULL, 
 '2026-03-06 09:30:00.000000', 0, NULL, _binary '', NULL, 
 NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE 
  email = VALUES(email),
  updated_at = VALUES(updated_at);

-- ============================================================================
-- PARTIE 3: Vérification
-- ============================================================================

SELECT 
    '=== UTILISATEURS AJOUTÉS ===' AS info;

SELECT 
    id, 
    email, 
    CONCAT(first_name, ' ', last_name) AS nom_complet,
    role,
    firm_id,
    enabled AS actif,
    email_verified AS email_verifie,
    created_at AS date_creation
FROM users 
WHERE id IN ('admin-super-001', 'client-demo-002')
ORDER BY role, created_at;

SELECT 
    CONCAT('Total utilisateurs: ', COUNT(*)) AS statistiques
FROM users;

-- Réactiver les vérifications
SET FOREIGN_KEY_CHECKS=1;
COMMIT;

-- ============================================================================
-- INFORMATIONS DE CONNEXION
-- ============================================================================
-- 
-- Super Admin:
--   Email: superadmin@gedavocat.fr
--   Password: Test1234!
--   Rôle: ADMIN
--
-- Client Demo:
--   Email: client.demo@gedavocat.fr
--   Password: Test1234!
--   Rôle: CLIENT
--   Cabinet: firm-demo-001
-- ============================================================================
