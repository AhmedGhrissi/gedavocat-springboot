-- ==================================================
-- Table APPOINTMENTS - Gestion des rendez-vous
-- ==================================================

CREATE TABLE IF NOT EXISTS appointments (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    appointment_date DATETIME NOT NULL,
    end_date DATETIME,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    
    -- Relations
    lawyer_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36),
    case_id VARCHAR(36),
    
    -- Lieu
    location VARCHAR(200),
    
    -- Informations tribunal
    court_name VARCHAR(200),
    court_room VARCHAR(50),
    judge_name VARCHAR(100),
    
    -- Notifications
    send_reminder BOOLEAN DEFAULT TRUE,
    reminder_sent BOOLEAN DEFAULT FALSE,
    reminder_minutes_before INT DEFAULT 60,
    
    -- Notes et liens
    notes TEXT,
    video_conference_link VARCHAR(500),
    
    -- Affichage
    color VARCHAR(7) DEFAULT '#3788d8',
    
    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Contraintes de clés étrangères
    CONSTRAINT fk_appointment_lawyer FOREIGN KEY (lawyer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL,
    CONSTRAINT fk_appointment_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
    
    -- Index pour les performances
    INDEX idx_appointment_lawyer (lawyer_id),
    INDEX idx_appointment_client (client_id),
    INDEX idx_appointment_case (case_id),
    INDEX idx_appointment_date (appointment_date),
    INDEX idx_appointment_status (status),
    INDEX idx_appointment_type (type),
    INDEX idx_reminder_check (send_reminder, reminder_sent, appointment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================================================
-- Données de test (optionnel - à supprimer en production)
-- ==================================================

-- Exemple de rendez-vous client
INSERT INTO appointments (id, title, description, appointment_date, type, status, lawyer_id, location, color, created_at)
SELECT 
    'appointment-001',
    'Consultation initiale - Divorce',
    'Premier rendez-vous avec client pour dossier de divorce',
    DATE_ADD(NOW(), INTERVAL 2 DAY),
    'CLIENT_MEETING',
    'SCHEDULED',
    u.id,
    'Cabinet - Bureau 2',
    '#3788d8',
    NOW()
FROM users u
WHERE u.role = 'LAWYER'
LIMIT 1;

-- Exemple d'audience au tribunal
INSERT INTO appointments (id, title, description, appointment_date, type, status, lawyer_id, court_name, court_room, judge_name, color, created_at)
SELECT 
    'appointment-002',
    'Audience référé',
    'Audience en référé pour demande de mesures conservatoires',
    DATE_ADD(NOW(), INTERVAL 5 DAY),
    'COURT_HEARING',
    'CONFIRMED',
    u.id,
    'Tribunal de Grande Instance de Paris',
    'Salle 12',
    'Juge Dupont',
    '#dc3545',
    NOW()
FROM users u
WHERE u.role = 'LAWYER'
LIMIT 1;

-- Exemple de visioconférence
INSERT INTO appointments (id, title, description, appointment_date, end_date, type, status, lawyer_id, video_conference_link, color, created_at)
SELECT 
    'appointment-003',
    'Réunion client en ligne',
    'Point d''avancement du dossier par visioconférence',
    DATE_ADD(NOW(), INTERVAL 1 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 DAY), INTERVAL 1 HOUR),
    'VIDEO_CONFERENCE',
    'SCHEDULED',
    u.id,
    'https://meet.google.com/abc-defg-hij',
    '#17a2b8',
    NOW()
FROM users u
WHERE u.role = 'LAWYER'
LIMIT 1;

-- ==================================================
-- Vues utiles
-- ==================================================

-- Vue des rendez-vous à venir avec détails
CREATE OR REPLACE VIEW v_upcoming_appointments AS
SELECT 
    a.id,
    a.title,
    a.appointment_date,
    a.end_date,
    a.type,
    a.status,
    a.location,
    a.court_name,
    u.name AS lawyer_name,
    u.email AS lawyer_email,
    c.name AS client_name,
    c.email AS client_email,
    ca.title AS case_title,
    a.send_reminder,
    a.reminder_sent,
    a.reminder_minutes_before
FROM appointments a
INNER JOIN users u ON a.lawyer_id = u.id
LEFT JOIN clients c ON a.client_id = c.id
LEFT JOIN cases ca ON a.case_id = ca.id
WHERE a.appointment_date > NOW()
    AND a.status NOT IN ('CANCELLED', 'COMPLETED')
ORDER BY a.appointment_date;

-- Vue des audiences au tribunal
CREATE OR REPLACE VIEW v_court_hearings AS
SELECT 
    a.id,
    a.title,
    a.appointment_date,
    a.court_name,
    a.court_room,
    a.judge_name,
    a.status,
    u.name AS lawyer_name,
    c.name AS client_name,
    ca.title AS case_title,
    ca.case_number
FROM appointments a
INNER JOIN users u ON a.lawyer_id = u.id
LEFT JOIN clients c ON a.client_id = c.id
LEFT JOIN cases ca ON a.case_id = ca.id
WHERE a.type = 'COURT_HEARING'
    AND a.appointment_date > NOW()
    AND a.status NOT IN ('CANCELLED')
ORDER BY a.appointment_date;

-- ==================================================
-- Procédures stockées
-- ==================================================

DELIMITER //

-- Procédure pour vérifier les conflits d'horaires
CREATE PROCEDURE check_appointment_conflict(
    IN p_lawyer_id VARCHAR(36),
    IN p_appointment_date DATETIME,
    IN p_exclude_id VARCHAR(36)
)
BEGIN
    SELECT 
        id,
        title,
        appointment_date,
        TIMESTAMPDIFF(MINUTE, appointment_date, p_appointment_date) AS minutes_diff
    FROM appointments
    WHERE lawyer_id = p_lawyer_id
        AND id != COALESCE(p_exclude_id, '')
        AND status NOT IN ('CANCELLED', 'COMPLETED')
        AND ABS(TIMESTAMPDIFF(MINUTE, appointment_date, p_appointment_date)) < 30;
END //

-- Procédure pour obtenir les rendez-vous nécessitant un rappel
CREATE PROCEDURE get_appointments_for_reminder()
BEGIN
    SELECT 
        a.id,
        a.title,
        a.appointment_date,
        a.reminder_minutes_before,
        u.email AS lawyer_email,
        u.name AS lawyer_name,
        c.email AS client_email,
        c.name AS client_name
    FROM appointments a
    INNER JOIN users u ON a.lawyer_id = u.id
    LEFT JOIN clients c ON a.client_id = c.id
    WHERE a.send_reminder = TRUE
        AND a.reminder_sent = FALSE
        AND a.appointment_date > NOW()
        AND a.status NOT IN ('CANCELLED', 'COMPLETED')
        AND TIMESTAMPDIFF(MINUTE, NOW(), a.appointment_date) <= a.reminder_minutes_before
        AND TIMESTAMPDIFF(MINUTE, NOW(), a.appointment_date) > 0;
END //

DELIMITER ;

-- ==================================================
-- Triggers
-- ==================================================

DELIMITER //

-- Trigger pour nettoyer les données liées lors de la suppression d'un rendez-vous
CREATE TRIGGER before_appointment_delete
BEFORE DELETE ON appointments
FOR EACH ROW
BEGIN
    -- Log l'événement dans audit_logs si la table existe
    INSERT INTO audit_logs (id, user_id, entity_type, entity_id, action, details, created_at)
    VALUES (
        UUID(),
        OLD.lawyer_id,
        'APPOINTMENT',
        OLD.id,
        'DELETE',
        CONCAT('Suppression du rendez-vous: ', OLD.title),
        NOW()
    );
END //

DELIMITER ;

-- ==================================================
-- Commentaires sur les colonnes
-- ==================================================

ALTER TABLE appointments 
    MODIFY COLUMN type VARCHAR(30) NOT NULL 
    COMMENT 'Type: CLIENT_MEETING, COURT_HEARING, INTERNAL_MEETING, PHONE_CALL, VIDEO_CONFERENCE, SITE_VISIT, OTHER';

ALTER TABLE appointments 
    MODIFY COLUMN status VARCHAR(30) NOT NULL 
    COMMENT 'Statut: SCHEDULED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, RESCHEDULED, NO_SHOW';
