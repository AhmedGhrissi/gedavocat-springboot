-- Migration : table des tokens d'archive légale pour la suppression de client
-- À exécuter en production (ddl-auto=validate ne crée pas automatiquement les tables)
-- Durée de conservation : 5 ans (art. 11 RIN — obligation avocat).

CREATE TABLE IF NOT EXISTS `client_archive_tokens` (
  `id`             varchar(36)   NOT NULL,
  `token`          varchar(64)   NOT NULL,
  `client_id`      varchar(36)   NOT NULL,
  `client_name`    varchar(255)  DEFAULT NULL,
  `client_email`   varchar(255)  DEFAULT NULL,
  `lawyer_id`      varchar(36)   NOT NULL,
  `lawyer_email`   varchar(255)  DEFAULT NULL,
  `storage_key`    varchar(500)  NOT NULL,
  `created_at`     datetime(6)   DEFAULT CURRENT_TIMESTAMP(6),
  `expires_at`     datetime(6)   NOT NULL,
  `download_count` int           NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_archive_token` (`token`),
  KEY `idx_client_archive_lawyer_id` (`lawyer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
