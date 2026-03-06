-- Migration pour ajouter un utilisateur admin et un utilisateur client
-- À exécuter après avoir importé dump_gedavocat_prod_20260305_backup.sql

USE gedavocat;

-- Ajout de l'utilisateur ADMIN (Super Admin)
INSERT INTO `users` VALUES (
    'admin-super-001',
    'superadmin@gedavocat.fr',
    '$2a$12$S0in8qlrAXxQeT1T9dwrfuiWMDQ1D7Q04vrkV5SMKR8lIUiB.aATi',  -- password: Test1234!
    'Super',
    'Admin',
    '+33 6 12 34 56 78',
    'ADMIN',
    NULL,  -- firm_id (admin n'appartient pas à un cabinet)
    1,     -- enabled
    1,     -- account_non_locked
    0,     -- failed_login_attempts
    '2026-03-06 10:00:00.000000',  -- last_login
    NULL,  -- password_changed_at
    '2026-03-06 10:00:00.000000',  -- created_at
    '2026-03-06 10:00:00.000000',  -- updated_at
    NULL,  -- access_ends_at
    _binary '',  -- account_enabled (true)
    NULL,  -- bar_number
    NULL,  -- barreau_id
    NULL,  -- email_signature
    _binary '',  -- email_verified (true)
    '2026-03-06 10:00:00.000000',  -- gdpr_consent_at
    NULL,  -- invitation_id
    NULL,  -- max_clients
    'Super Admin',  -- name
    NULL,  -- reset_token
    NULL,  -- reset_token_expiry
    NULL,  -- stripe_customer_id
    NULL,  -- billing_period
    NULL,  -- subscription_ends_at
    NULL,  -- subscription_plan
    NULL,  -- subscription_start_date
    NULL,  -- subscription_status
    '2026-03-06 10:00:00.000000',  -- terms_accepted_at
    0,     -- entity_version
    NULL,  -- mfa_backup_codes
    _binary '',  -- mfa_enabled (true)
    NULL,  -- mfa_last_used
    NULL,  -- mfa_secret
    NULL,  -- mfa_temp_setup
    NULL   -- stripe_subscription_id
);

-- Ajout de l'utilisateur CLIENT (Client de démonstration)
INSERT INTO `users` VALUES (
    'client-demo-002',
    'client.demo@gedavocat.fr',
    '$2a$12$S0in8qlrAXxQeT1T9dwrfuiWMDQ1D7Q04vrkV5SMKR8lIUiB.aATi',  -- password: Test1234!
    'Pierre',
    'Martin',
    '+33 6 98 76 54 32',
    'CLIENT',
    'firm-demo-001',  -- firm_id (appartient au cabinet de démonstration)
    1,     -- enabled
    1,     -- account_non_locked
    0,     -- failed_login_attempts
    '2026-03-06 09:30:00.000000',  -- last_login
    NULL,  -- password_changed_at
    '2026-03-06 09:30:00.000000',  -- created_at
    '2026-03-06 09:30:00.000000',  -- updated_at
    '2027-03-06 09:30:00.000000',  -- access_ends_at
    _binary '',  -- account_enabled (true)
    NULL,  -- bar_number
    NULL,  -- barreau_id
    NULL,  -- email_signature
    _binary '',  -- email_verified (true)
    '2026-03-06 09:30:00.000000',  -- gdpr_consent_at
    NULL,  -- invitation_id
    NULL,  -- max_clients
    'Pierre Martin',  -- name
    NULL,  -- reset_token
    NULL,  -- reset_token_expiry
    NULL,  -- stripe_customer_id
    NULL,  -- billing_period
    NULL,  -- subscription_ends_at
    NULL,  -- subscription_plan
    NULL,  -- subscription_start_date
    NULL,  -- subscription_status
    '2026-03-06 09:30:00.000000',  -- terms_accepted_at
    0,     -- entity_version
    NULL,  -- mfa_backup_codes
    _binary '',  -- mfa_enabled (true)
    NULL,  -- mfa_last_used
    NULL,  -- mfa_secret
    NULL,  -- mfa_temp_setup
    NULL   -- stripe_subscription_id
);

-- Vérification
SELECT id, email, first_name, last_name, role FROM users WHERE id IN ('admin-super-001', 'client-demo-002');
