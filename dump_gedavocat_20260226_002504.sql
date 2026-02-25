-- MySQL dump 10.13  Distrib 8.0.45, for Linux (aarch64)
--
-- Host: localhost    Database: gedavocat
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `appointments`
--

DROP TABLE IF EXISTS `appointments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `appointments` (
  `id` varchar(36) NOT NULL,
  `appointment_date` datetime(6) NOT NULL,
  `color` varchar(7) DEFAULT NULL,
  `court_name` varchar(200) DEFAULT NULL,
  `court_room` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `end_date` datetime(6) DEFAULT NULL,
  `judge_name` varchar(100) DEFAULT NULL,
  `location` varchar(200) DEFAULT NULL,
  `notes` text,
  `reminder_minutes_before` int DEFAULT NULL,
  `reminder_sent` bit(1) DEFAULT NULL,
  `send_reminder` bit(1) DEFAULT NULL,
  `status` enum('SCHEDULED','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','RESCHEDULED','NO_SHOW') NOT NULL,
  `title` varchar(200) NOT NULL,
  `type` enum('CLIENT_MEETING','COURT_HEARING','INTERNAL_MEETING','PHONE_CALL','VIDEO_CONFERENCE','SITE_VISIT','OTHER') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `video_conference_link` varchar(500) DEFAULT NULL,
  `client_id` varchar(36) DEFAULT NULL,
  `lawyer_id` varchar(36) NOT NULL,
  `case_id` varchar(36) DEFAULT NULL,
  `client_confirmed_at` datetime(6) DEFAULT NULL,
  `reschedule_requested_by` varchar(10) DEFAULT NULL,
  `proposed_date` datetime(6) DEFAULT NULL,
  `reschedule_message` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appointment_lawyer` (`lawyer_id`),
  KEY `idx_appointment_client` (`client_id`),
  KEY `idx_appointment_case` (`case_id`),
  KEY `idx_appointment_date` (`appointment_date`),
  KEY `idx_appointment_status` (`status`),
  CONSTRAINT `FK8rliyvm03pviul5c1r4ioj5yf` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`),
  CONSTRAINT `FKarhp3urr2l0hiifrdjk8ae0ru` FOREIGN KEY (`lawyer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKfbl6cciquyyvv5s1e31qmflkb` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `appointments`
--

LOCK TABLES `appointments` WRITE;
/*!40000 ALTER TABLE `appointments` DISABLE KEYS */;
/*!40000 ALTER TABLE `appointments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` varchar(36) NOT NULL,
  `action` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `details` text,
  `entity_id` varchar(36) DEFAULT NULL,
  `entity_type` varchar(50) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` text,
  `user_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action` (`action`),
  KEY `idx_entity_type` (`entity_type`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `FKjs4iimve3y0xssbtve5ysyef0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
INSERT INTO `audit_logs` VALUES ('01d5b1d0-b6f0-4368-8454-94b4f286ff7a','DOCUMENT_DOWNLOADED','2026-02-25 14:58:30.327399','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('048e23b4-5d8e-42ba-a185-3f23fd729012','DOCUMENT_UPLOADED','2026-02-25 17:05:18.477459','Upload du document: SECURITY-AUDIT-REPORT.pdf','8a482e3a-9612-4a81-8385-242a398861af','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','c859826f-db2d-42b9-80a2-a31492cb61d4'),('04d31233-4b7f-4d82-9af5-736f53f4616a','CASE_CLOSED','2026-02-24 22:23:07.293313','Fermeture du dossier: litige','4724198a-794c-4fc5-a37d-2c0eafcd9609','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('04da5b5f-5411-4af0-97b7-e173686757ab','CLIENT_DELETED','2026-02-25 00:17:45.288076','Suppression du client: DUFAYET Jean-Christophe','b70176ea-4793-46f9-abb8-a9ca744c06ae','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('0b48386b-e85a-41cd-8fba-bf41393a4ecc','DOCUMENT_DOWNLOADED','2026-02-23 22:46:34.439446','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','88d53ce6-031e-4f33-8890-b4904bd47e39','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('0c56784d-d788-4c55-91a3-4976d5606d69','DOCUMENT_DOWNLOADED','2026-02-23 22:46:46.243293','T├®l├®chargement du document: DocAvocat-Documentation-Technique.pdf','6362bcf3-a0cb-42e4-9673-7052d1c4c660','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('10bc32c7-e26c-43f4-8ff2-7578ff2d624b','DOCUMENT_DOWNLOADED','2026-02-25 18:00:57.631993','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','320906d1-8037-4f7d-9f04-2ca2a0bee177','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','db924060-e627-44e6-85d0-5ae5590255c2'),('13480ec0-d4bb-4496-ad95-aa5220112009','CASE_CREATED','2026-02-25 15:27:06.275539','Cr├®ation du dossier: test 2','cfbc927e-c8d3-4e05-8cde-66ddcd6843b4','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('1440d24f-20cd-4ec5-a48e-84fca8e8a3c8','CASE_CREATED','2026-02-25 10:15:09.805726','Cr├®ation du dossier: Litige commercial','0c67bca5-3c01-40b0-8b96-2661014e5712','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('1befdfda-0d29-417b-855e-8e529db4fd52','DOCUMENT_DOWNLOADED','2026-02-23 22:46:37.282068','T├®l├®chargement du document: DocAvocat-Documentation-Technique.pdf','6362bcf3-a0cb-42e4-9673-7052d1c4c660','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('1c59b87d-6bec-457a-a897-687456fb81ce','DOCUMENT_DOWNLOADED','2026-02-25 00:27:49.895860','T├®l├®chargement du document: import-2026-02-24-3127.pdf','65ab3aba-29f6-4ddb-9afd-bd88832d3c5c','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('1d42150e-5466-4744-a5cc-8461e632bf5f','DOCUMENT_DOWNLOADED','2026-02-25 17:03:59.231737','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','39ef1729-02c5-417b-9394-85bd576030a7','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','2d4117ac-8599-46eb-8c9d-68bda976c94a'),('2181ec91-ceec-4a00-9e6b-7e48465d2ec7','DOCUMENT_DOWNLOADED','2026-02-25 15:08:23.548727','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','0f1ddde1-1d7f-479b-bbbe-19916739b0c8'),('256d6561-7f40-49d6-977e-2e67ab115098','DOCUMENT_DELETED','2026-02-23 22:46:53.948992','Suppression du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','88d53ce6-031e-4f33-8890-b4904bd47e39','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('2573fe30-03b9-4e3d-920f-ae395f0f713e','DOCUMENT_DOWNLOADED','2026-02-25 15:24:33.546691','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','d11a8aac-778d-4d2d-8e26-fa1466240d0f','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('27278f1e-fb8d-48a0-b285-2e7634893324','CLIENT_CREATED','2026-02-25 00:18:14.304814','Cr├®ation du client: DUFAYET JC','24ad2adc-321c-4f64-89ca-90b448c55ba0','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('28d00ecf-17c9-4fc0-8ce1-8971d1fe24fb','CASE_CREATED','2026-02-25 00:24:56.445118','Cr├®ation du dossier: test','4d98b345-576e-4e27-a975-f199c6497cd2','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('2ee6969e-5c5d-4aac-a3e3-4aa1fc3e4fbf','DOCUMENT_DOWNLOADED','2026-02-25 17:58:30.451914','T├®l├®chargement du document: facture-20260039489.pdf','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','Document','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('3003f0a1-7368-4057-8011-9fea10569482','DOCUMENT_DOWNLOADED','2026-02-25 17:59:11.878586','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','320906d1-8037-4f7d-9f04-2ca2a0bee177','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','62d78f4f-0e93-4c3d-be2d-e4530078a65a'),('30a813db-db43-49bb-a5cd-1d91f3bcaee5','DOCUMENT_DOWNLOADED','2026-02-25 17:05:37.942694','T├®l├®chargement du document: guide_vps_hetzner_o2switch.pdf','5fe48373-6ee6-4198-bc21-ab8162086541','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','c859826f-db2d-42b9-80a2-a31492cb61d4'),('30ce6536-24b9-46ae-8abc-f6d0d7c3c71d','DOCUMENT_DOWNLOADED','2026-02-25 16:58:36.096750','T├®l├®chargement du document: facture-20260039489.pdf','d02d9993-80e6-4abe-b1d7-62c8c470e4bf','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('310b50ec-3cba-4a1f-8ea7-27a36f026867','DOCUMENT_DOWNLOADED','2026-02-25 14:58:30.231707','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('3a254860-eadf-41ec-9f57-00e51ae2db29','CLIENT_DELETED','2026-02-25 00:20:54.551666','Suppression du client: DUFAYET JC','24ad2adc-321c-4f64-89ca-90b448c55ba0','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('40bf3662-62b5-4bcf-bddf-9992d427a2ab','CLIENT_CREATED','2026-02-21 23:55:54.238592','Cr├®ation du client: DUFAYET Jean Fraon├ºois','b70176ea-4793-46f9-abb8-a9ca744c06ae','Client','178.211.131.73','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('43372f66-cf30-4d7c-8002-a765e37e5d26','CLIENT_CREATED','2026-02-25 15:11:54.217740','Cr├®ation du client: Machin truic','fd4b973c-113e-4300-84f0-47d463bafa67','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('45307d05-523e-4ab9-9c26-b22be93d14fb','DOCUMENT_UPLOADED','2026-02-23 22:36:47.488174','Upload du document: DocAvocat-Documentation-Technique.pdf','6362bcf3-a0cb-42e4-9673-7052d1c4c660','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('46314b2d-8934-4173-9b00-32aefe7c588b','CLIENT_DELETED','2026-02-25 15:12:03.558011','Suppression du client: DUFAYET JC','4dd0574b-ff30-4c38-bb30-06d6bdc3994f','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('4693e152-6fe3-45e0-af7a-ddb2daf51e4d','DOCUMENT_UPLOADED','2026-02-25 17:58:16.582955','Upload du document: facture-20260039489.pdf','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','62d78f4f-0e93-4c3d-be2d-e4530078a65a'),('52ca04b2-5be1-4fd7-a221-84a4ea609e92','CASE_DELETED','2026-02-25 10:14:54.437170','Suppression du dossier: test','4d98b345-576e-4e27-a975-f199c6497cd2','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('54c7f54b-86bf-4b76-a864-b2648f4fa9c7','DOCUMENT_DOWNLOADED','2026-02-23 20:33:07.129229','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','88d53ce6-031e-4f33-8890-b4904bd47e39','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('55827eae-8160-4389-8c83-721f38479e16','CASE_CREATED','2026-02-25 00:19:46.582530','Cr├®ation du dossier: Litige commercial','30d9a23c-b2c8-4e7e-924f-c3ea56c0e990','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('5a9a0a4c-fc8b-4b92-8e35-68aa02945b0d','DOCUMENT_UPLOADED','2026-02-25 13:32:10.592050','Upload du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('5aa6870b-5594-4cdd-a500-2aa9c6bc31cd','DOCUMENT_DOWNLOADED','2026-02-25 00:26:22.677251','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','7f348914-a727-4212-8b7f-c87e05725a0f','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('5b123018-004f-405b-b711-447b222bc850','DOCUMENT_DOWNLOADED','2026-02-23 22:47:04.071240','T├®l├®chargement du document: facture-20260039489.pdf','ddb805e3-1c17-4582-8eed-51589257ab4e','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('5d1c0daa-9b6d-4033-81a2-7c4ea0381017','DOCUMENT_DOWNLOADED','2026-02-25 18:00:59.672823','T├®l├®chargement du document: facture-20260039489.pdf','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','db924060-e627-44e6-85d0-5ae5590255c2'),('5f5e4155-2d4f-4cf3-aa09-8c6fc425bb5d','CLIENT_DELETED','2026-02-25 00:17:42.995232','Suppression du client: Jean Dupont','53c95ace-8f21-459e-8b24-3a09f2120aca','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('6802b437-1d86-4299-9764-ff201342fac8','DOCUMENT_DOWNLOADED','2026-02-25 16:58:18.244748','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','d11a8aac-778d-4d2d-8e26-fa1466240d0f','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('694253f0-de6d-42a3-831e-2ea0f924825a','DOCUMENT_UPLOADED','2026-02-25 00:27:46.040242','Upload du document: import-2026-02-24-3127.pdf','65ab3aba-29f6-4ddb-9afd-bd88832d3c5c','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('69bae3fd-60a0-4b52-a34d-cf69298b7ffe','DOCUMENT_DOWNLOADED','2026-02-25 17:58:46.445670','T├®l├®chargement du document: facture-20260039489.pdf','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','62d78f4f-0e93-4c3d-be2d-e4530078a65a'),('6b27a244-2e97-4966-a4a7-80725bc389b4','CASE_DELETED','2026-02-25 00:17:35.125376','Suppression du dossier: Ahmed le d├®linquant (toujours les memes)','b5d41276-aaec-470b-b2b9-cee3979a6e80','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('6cbfe169-8619-43db-97db-4f1b6c4c6325','DOCUMENT_DELETED','2026-02-23 22:46:52.101510','Suppression du document: DocAvocat-Documentation-Technique.pdf','6362bcf3-a0cb-42e4-9673-7052d1c4c660','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('71c604c0-413a-489b-87e2-b2e3ceb71f08','CASE_CREATED','2026-02-21 23:57:17.839789','Cr├®ation du dossier: litige','4724198a-794c-4fc5-a37d-2c0eafcd9609','Case','178.211.131.73','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('72846dc9-5996-409a-95bb-167330775524','CLIENT_CREATED','2026-02-25 17:00:17.150051','Cr├®ation du client: Jean-Christophe DUFAYET','87fe16da-3a09-41cf-916f-a94cea7da20d','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('7d8659bb-15c8-49c1-a7ee-d961861d224f','CASE_DELETED','2026-02-25 15:11:27.667570','Suppression du dossier: Litige commercial','0c67bca5-3c01-40b0-8b96-2661014e5712','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('7e247d1d-f343-41e7-82af-e0e39bf7897e','CLIENT_DELETED','2026-02-25 17:51:45.183767','Suppression du client: Jean-Christophe DUFAYET','87fe16da-3a09-41cf-916f-a94cea7da20d','Client','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('80d39ace-8752-49ab-bf08-dc0f87be2ae9','CLIENT_CREATED','2026-02-25 00:23:30.353884','Cr├®ation du client: DUFAYET JC','4dd0574b-ff30-4c38-bb30-06d6bdc3994f','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('83cceeb8-420c-4cd4-a93b-324eaf17ff97','DOCUMENT_DOWNLOADED','2026-02-25 15:03:16.525478','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('84ae5927-d315-462b-8b59-0a5505e2a3fe','CASE_CREATED','2026-02-25 00:04:41.633230','Cr├®ation du dossier: Ahmed le d├®linquant (toujours les memes)','b5d41276-aaec-470b-b2b9-cee3979a6e80','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('86af2f32-072d-436c-9e25-1c6d26eb0f10','DOCUMENT_UPLOADED','2026-02-25 23:32:03.720700','Upload du document: facture-20260039489.pdf','57c5203f-05f2-4d1c-997b-1c5c6ba8e474','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('89959436-99cb-48dd-a5ad-a80c70702c91','CASE_CREATED','2026-02-25 15:23:53.963138','Cr├®ation du dossier: Litige avec Ahmed','46aa6eb9-f4fc-4deb-b8c7-39cedef27770','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('9801b63e-e52e-429d-881b-ce961dad0042','DOCUMENT_UPLOADED','2026-02-23 22:47:02.599332','Upload du document: facture-20260039489.pdf','ddb805e3-1c17-4582-8eed-51589257ab4e','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('9b879d32-56f8-4d5c-9ad7-76681424353c','CASE_CREATED','2026-02-25 17:57:50.149454','Cr├®ation du dossier: divorce','f8d84bb7-72d1-4aec-af75-1bef3c960f61','Case','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('9d8f7cc1-3b4b-4004-8794-773fb3f77f96','DOCUMENT_UPLOADED','2026-02-25 17:05:36.202896','Upload du document: guide_vps_hetzner_o2switch.pdf','5fe48373-6ee6-4198-bc21-ab8162086541','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','c859826f-db2d-42b9-80a2-a31492cb61d4'),('9e08a55e-4bc0-47b3-9382-7feb4d76c796','DOCUMENT_UPLOADED','2026-02-25 17:59:07.631073','Upload du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','320906d1-8037-4f7d-9f04-2ca2a0bee177','Document','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('9fe15a92-6e74-4879-bfe2-0b4198776f6f','DOCUMENT_DOWNLOADED','2026-02-23 22:46:28.092473','T├®l├®chargement du document: DocAvocat-Documentation-Technique.pdf','6362bcf3-a0cb-42e4-9673-7052d1c4c660','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('a0e54a7a-9629-4d9b-bec5-f7f238682188','DOCUMENT_DOWNLOADED','2026-02-25 17:58:26.918117','T├®l├®chargement du document: facture-20260039489.pdf','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','62d78f4f-0e93-4c3d-be2d-e4530078a65a'),('a392fc78-d6b5-4e58-90c6-b7487e4e8a5d','DOCUMENT_DOWNLOADED','2026-02-23 22:46:49.278207','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','88d53ce6-031e-4f33-8890-b4904bd47e39','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('a4fdb7d7-1ed7-49c3-8482-612cc117e8d2','DOCUMENT_DOWNLOADED','2026-02-24 20:25:41.342926','T├®l├®chargement du document: facture-20260039489.pdf','ddb805e3-1c17-4582-8eed-51589257ab4e','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('a7b7c1d9-9f24-4be4-828e-59871c7c65bb','DOCUMENT_UPLOADED','2026-02-25 17:02:30.514051','Upload du document: SECURITY-AUDIT-REPORT.pdf','39ef1729-02c5-417b-9394-85bd576030a7','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('af00452c-8c3b-4ff4-8c4f-cb9b60c1ebfb','CASE_CREATED','2026-02-25 17:02:21.663846','Cr├®ation du dossier: Litige avec une entreprise [PUDHOMME]','6357cacd-b4e3-46e4-aba2-1bfa6244b57c','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('b3d2a223-f2c6-4cf1-9ea0-b975395e4878','DOCUMENT_DOWNLOADED','2026-02-23 22:37:21.696841','T├®l├®chargement du document: DocAvocat-Documentation-Technique.pdf','6362bcf3-a0cb-42e4-9673-7052d1c4c660','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('b4a5700e-1489-4319-9353-4a810a4524e5','DOCUMENT_DOWNLOADED','2026-02-25 17:02:31.709340','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','39ef1729-02c5-417b-9394-85bd576030a7','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('bbd4513e-ae88-40f1-9775-255256d03178','CLIENT_CREATED','2026-02-25 17:53:22.617738','Cr├®ation du client: Jean Valgeant','7a5cd9c3-196d-4e7b-9f34-1cf46197d8f3','Client','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('bd3ef4b7-5ff8-4132-acda-ee0ce0c6c5ef','DOCUMENT_DOWNLOADED','2026-02-25 17:05:23.357842','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','8a482e3a-9612-4a81-8385-242a398861af','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','c859826f-db2d-42b9-80a2-a31492cb61d4'),('bda24b5a-9264-4a3d-91cc-27eb5f5b497c','CLIENT_DELETED','2026-02-25 16:58:44.968091','Suppression du client: Machin truic','fd4b973c-113e-4300-84f0-47d463bafa67','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('c6a1256e-e6f3-4e3d-b455-98c2c83b3d92','CLIENT_UPDATED','2026-02-22 18:31:07.972486','Modification du client: DUFAYET Jean-Christophe','b70176ea-4793-46f9-abb8-a9ca744c06ae','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('cb19b1a1-79e4-471a-9c23-582af086c041','DOCUMENT_DOWNLOADED','2026-02-25 17:58:36.692261','T├®l├®chargement du document: facture-20260039489.pdf','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','Document','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('cc81d9b2-ce29-4cc3-9035-72f3cde721d0','CLIENT_CREATED','2026-02-24 22:18:52.634458','Cr├®ation du client: Jean Dupont','53c95ace-8f21-459e-8b24-3a09f2120aca','Client','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('d02b0245-7852-4442-863a-ea79aacf04fc','CLIENT_DELETED','2026-02-25 17:55:10.319627','Suppression du client: Jean Valgeant','7a5cd9c3-196d-4e7b-9f34-1cf46197d8f3','Client','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('d1117d13-48cc-47cc-8f9f-b474e08a2388','DOCUMENT_DOWNLOADED','2026-02-25 17:05:20.328675','T├®l├®chargement du document: SECURITY-AUDIT-REPORT.pdf','39ef1729-02c5-417b-9394-85bd576030a7','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','c859826f-db2d-42b9-80a2-a31492cb61d4'),('d3710756-b10a-4d9f-b45a-ebada75bf2a1','DOCUMENT_DOWNLOADED','2026-02-25 13:32:49.223869','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('d3d1ba61-b2a4-425a-b2db-701a34f4b958','DOCUMENT_DOWNLOADED','2026-02-25 15:04:13.063250','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('e22acf52-1ffb-4d6b-861a-298b373fe2c3','CASE_DELETED','2026-02-25 00:17:31.886933','Suppression du dossier: litige','4724198a-794c-4fc5-a37d-2c0eafcd9609','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('e34de1ec-ae27-49fd-bf8d-6f6c86f73e05','DOCUMENT_DOWNLOADED','2026-02-25 15:03:16.440950','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('e3b28260-bf33-4f2d-b565-0642f6c2c686','DOCUMENT_DOWNLOADED','2026-02-25 15:06:51.841415','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('e553ff4e-3a38-45fd-9b93-51a9d01bd401','CASE_DELETED','2026-02-25 17:51:34.781690','Suppression du dossier: Litige avec une entreprise [PUDHOMME]','6357cacd-b4e3-46e4-aba2-1bfa6244b57c','Case','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('e74bb814-8c96-4ea2-9758-b2f5edf4d920','CASE_DELETED','2026-02-25 16:58:44.865490','Suppression du dossier: Litige avec Ahmed','46aa6eb9-f4fc-4deb-b8c7-39cedef27770','Case','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('ec9549f8-2fcf-4b7a-b03a-bf100e0639a5','DOCUMENT_UPLOADED','2026-02-25 16:58:33.545252','Upload du document: facture-20260039489.pdf','d02d9993-80e6-4abe-b1d7-62c8c470e4bf','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('ef8372cf-d934-4060-86b3-efbbd55f6b18','DOCUMENT_DOWNLOADED','2026-02-23 22:36:35.310762','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','88d53ce6-031e-4f33-8890-b4904bd47e39','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('f0edd9e2-75e5-434e-8894-375b66ee52c4','DOCUMENT_DOWNLOADED','2026-02-25 15:04:13.124486','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('f2c1d1f2-fd76-4266-a6be-9f24cfaae8e6','DOCUMENT_DOWNLOADED','2026-02-23 23:02:26.112578','T├®l├®chargement du document: facture-20260039489.pdf','ddb805e3-1c17-4582-8eed-51589257ab4e','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('f87dfe44-0d63-4b86-87b0-e76e30126756','DOCUMENT_DOWNLOADED','2026-02-25 15:01:20.526771','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('fb4ee9b6-3e4e-4e6d-a067-5179a0751130','DOCUMENT_DOWNLOADED','2026-02-25 15:01:20.612697','T├®l├®chargement du document: RAPPORT-PENTEST-GEDAVOCAT.pdf','00fc425a-ceb2-4538-bbd3-2aa7c512f333','Document','172.18.0.1','curl/8.5.0','e81efeae-a750-4a8c-a509-e1f31ee4e7ae'),('fb6e3b00-6bdd-4ac5-b792-1d3e732eeda0','DOCUMENT_UPLOADED','2026-02-25 15:24:19.559265','Upload du document: SECURITY-AUDIT-REPORT.pdf','d11a8aac-778d-4d2d-8e26-fa1466240d0f','Document','90.66.164.208','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','3fd5cfa2-503d-4530-8988-12ec2bec7ecf'),('fe7d9ad9-51f6-4857-bdae-e0f5743c2bf7','CLIENT_CREATED','2026-02-25 17:56:18.752075','Cr├®ation du client: degroubobabu','fdadb03f-17af-419e-8fb2-e6ed27212039','Client','37.252.225.110','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36','7837d9f6-14bb-4bdf-890f-7160fc43911e');
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_share_links`
--

DROP TABLE IF EXISTS `case_share_links`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_share_links` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `access_count` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `max_access_count` int DEFAULT NULL,
  `revoked` bit(1) NOT NULL,
  `token` varchar(72) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `case_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `invited_at` datetime(6) DEFAULT NULL,
  `recipient_email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_role` enum('ADMIN','LAWYER','CLIENT','LAWYER_SECONDARY','HUISSIER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_csl_token` (`token`),
  KEY `idx_csl_case` (`case_id`),
  KEY `idx_csl_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_share_links`
--

LOCK TABLES `case_share_links` WRITE;
/*!40000 ALTER TABLE `case_share_links` DISABLE KEYS */;
INSERT INTO `case_share_links` VALUES ('80299d10-cb54-42e2-9e49-7f6d40768bde',0,'2026-02-25 17:59:49.502723','GEDAVOCAT',NULL,NULL,_binary '','808362ee2222470b892d9898cb9ef2f13e29519d','7837d9f6-14bb-4bdf-890f-7160fc43911e','f8d84bb7-72d1-4aec-af75-1bef3c960f61','2026-02-25 17:59:47.321844','gofrexeufrupru-4641@yopmail.com',NULL);
/*!40000 ALTER TABLE `case_share_links` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cases`
--

DROP TABLE IF EXISTS `cases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cases` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `name` varchar(255) NOT NULL,
  `status` enum('OPEN','IN_PROGRESS','CLOSED','ARCHIVED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `client_id` varchar(36) NOT NULL,
  `lawyer_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_case_lawyer_id` (`lawyer_id`),
  KEY `idx_case_client_id` (`client_id`),
  KEY `idx_case_status` (`status`),
  CONSTRAINT `FK3xkk4p4hvu6kwe763a6i1hgu0` FOREIGN KEY (`lawyer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKntx229f8r4ppy1vthdl8fp59p` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cases`
--

LOCK TABLES `cases` WRITE;
/*!40000 ALTER TABLE `cases` DISABLE KEYS */;
INSERT INTO `cases` VALUES ('f8d84bb7-72d1-4aec-af75-1bef3c960f61','2026-02-25 17:57:50.146292','divorce','divorce','OPEN','2026-02-25 17:57:50.146337','fdadb03f-17af-419e-8fb2-e6ed27212039','7837d9f6-14bb-4bdf-890f-7160fc43911e');
/*!40000 ALTER TABLE `cases` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `clients`
--

DROP TABLE IF EXISTS `clients`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `clients` (
  `id` varchar(36) NOT NULL,
  `access_ends_at` datetime(6) DEFAULT NULL,
  `address` text,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `invitation_id` varchar(36) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `client_user_id` varchar(36) DEFAULT NULL,
  `lawyer_id` varchar(36) NOT NULL,
  `client_type` enum('INDIVIDUAL','PROFESSIONAL') DEFAULT NULL,
  `company_name` varchar(200) DEFAULT NULL,
  `siret` varchar(20) DEFAULT NULL,
  `invited_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_client_lawyer_id` (`lawyer_id`),
  KEY `idx_client_email` (`email`),
  KEY `FKnu31n9saq5x5mj38qw6ul0df` (`client_user_id`),
  CONSTRAINT `FKalp9ocasuvwaknl3xq6ymcx2` FOREIGN KEY (`lawyer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKnu31n9saq5x5mj38qw6ul0df` FOREIGN KEY (`client_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `clients`
--

LOCK TABLES `clients` WRITE;
/*!40000 ALTER TABLE `clients` DISABLE KEYS */;
INSERT INTO `clients` VALUES ('fdadb03f-17af-419e-8fb2-e6ed27212039',NULL,'21 BOULEVARD HONORE DE BALZAC\r\n','2026-02-25 17:56:18.749142','degroubobabu-2311@yopmail.com','f05f7100-74ba-4114-9e64-4fe418a18c8f','degroubobabu','0650261919','2026-02-25 17:56:43.061529','62d78f4f-0e93-4c3d-be2d-e4530078a65a','7837d9f6-14bb-4bdf-890f-7160fc43911e','INDIVIDUAL','','','2026-02-25 17:56:18.767855');
/*!40000 ALTER TABLE `clients` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_shares`
--

DROP TABLE IF EXISTS `document_shares`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_shares` (
  `id` varchar(36) NOT NULL,
  `document_id` varchar(36) NOT NULL,
  `case_id` varchar(36) NOT NULL,
  `target_role` enum('ADMIN','LAWYER','CLIENT','LAWYER_SECONDARY','HUISSIER') NOT NULL,
  `can_download` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_docshare_doc_role` (`document_id`,`target_role`),
  UNIQUE KEY `UK4q7st89f7n5lpy9g3ns2rvn27` (`document_id`,`target_role`),
  KEY `idx_docshare_document` (`document_id`),
  KEY `idx_docshare_case` (`case_id`),
  KEY `idx_docshare_role` (`target_role`),
  CONSTRAINT `document_shares_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`) ON DELETE CASCADE,
  CONSTRAINT `document_shares_ibfk_2` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_shares`
--

LOCK TABLES `document_shares` WRITE;
/*!40000 ALTER TABLE `document_shares` DISABLE KEYS */;
INSERT INTO `document_shares` VALUES ('02c71731-3dd5-4a8a-9888-9155e27c1bbc','320906d1-8037-4f7d-9f04-2ca2a0bee177','f8d84bb7-72d1-4aec-af75-1bef3c960f61','HUISSIER',0,'2026-02-25 21:57:02.277154'),('65a6fd9e-72d7-4573-ad02-c4b003ef003f','320906d1-8037-4f7d-9f04-2ca2a0bee177','f8d84bb7-72d1-4aec-af75-1bef3c960f61','LAWYER_SECONDARY',0,'2026-02-25 21:56:42.196798'),('66bbde02-da75-4d79-8ac4-a262715ae790','ca922e9e-6c92-4785-b4f5-57f6d7f33e05','f8d84bb7-72d1-4aec-af75-1bef3c960f61','HUISSIER',0,'2026-02-25 21:56:43.228510');
/*!40000 ALTER TABLE `document_shares` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `documents`
--

DROP TABLE IF EXISTS `documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `documents` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `filename` varchar(255) NOT NULL,
  `is_latest` bit(1) NOT NULL,
  `mimetype` varchar(100) DEFAULT NULL,
  `original_name` varchar(255) NOT NULL,
  `path` text NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `uploader_role` varchar(20) NOT NULL,
  `version` int NOT NULL,
  `case_id` varchar(36) NOT NULL,
  `parent_document_id` varchar(36) DEFAULT NULL,
  `uploaded_by` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_document_case_id` (`case_id`),
  KEY `idx_document_uploaded_by` (`uploaded_by`),
  KEY `idx_document_deleted_at` (`deleted_at`),
  KEY `FK60dm4aap7dopqi9s25b69lhin` (`parent_document_id`),
  CONSTRAINT `FK1ugacya4ssi0ilf8a9tjycgs6` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK60dm4aap7dopqi9s25b69lhin` FOREIGN KEY (`parent_document_id`) REFERENCES `documents` (`id`),
  CONSTRAINT `FKg5da0gvm8l4a5ryls4rq63mw5` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `documents`
--

LOCK TABLES `documents` WRITE;
/*!40000 ALTER TABLE `documents` DISABLE KEYS */;
INSERT INTO `documents` VALUES ('320906d1-8037-4f7d-9f04-2ca2a0bee177','2026-02-25 17:59:07.627868',NULL,34634,'6eadb434-8153-4f65-8812-0d7d8f9b22d9.pdf',_binary '','application/pdf','RAPPORT-PENTEST-GEDAVOCAT.pdf','/opt/gedavocat/uploads/documents/6eadb434-8153-4f65-8812-0d7d8f9b22d9.pdf','2026-02-25 17:59:07.627952','LAWYER',1,'f8d84bb7-72d1-4aec-af75-1bef3c960f61',NULL,'7837d9f6-14bb-4bdf-890f-7160fc43911e'),('57c5203f-05f2-4d1c-997b-1c5c6ba8e474','2026-02-25 23:32:03.693875',NULL,14306,'d32cac20-436e-46b4-aa6a-e90f495a4cb8.pdf',_binary '','application/pdf','facture-20260039489.pdf','/opt/gedavocat/uploads/documents/d32cac20-436e-46b4-aa6a-e90f495a4cb8.pdf','2026-02-25 23:32:03.693951','LAWYER',1,'f8d84bb7-72d1-4aec-af75-1bef3c960f61',NULL,'7837d9f6-14bb-4bdf-890f-7160fc43911e'),('ca922e9e-6c92-4785-b4f5-57f6d7f33e05','2026-02-25 17:58:16.578414',NULL,14306,'e150a4be-a3c2-4e14-9966-ee8b42c99c14.pdf',_binary '','application/pdf','facture-20260039489.pdf','/opt/gedavocat/uploads/documents/e150a4be-a3c2-4e14-9966-ee8b42c99c14.pdf','2026-02-25 17:58:16.578451','CLIENT',1,'f8d84bb7-72d1-4aec-af75-1bef3c960f61',NULL,'62d78f4f-0e93-4c3d-be2d-e4530078a65a');
/*!40000 ALTER TABLE `documents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `invoice_items`
--

DROP TABLE IF EXISTS `invoice_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_items` (
  `id` varchar(36) NOT NULL,
  `description` varchar(500) NOT NULL,
  `display_order` int DEFAULT NULL,
  `quantity` decimal(10,2) NOT NULL,
  `total_ht` decimal(10,2) DEFAULT NULL,
  `total_ttc` decimal(10,2) DEFAULT NULL,
  `total_tva` decimal(10,2) DEFAULT NULL,
  `tva_rate` decimal(5,2) NOT NULL,
  `unit_price_ht` decimal(10,2) NOT NULL,
  `invoice_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_invoice_item_invoice_id` (`invoice_id`),
  CONSTRAINT `FK46ae0lhu1oqs7cv91fn6y9n7w` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invoice_items`
--

LOCK TABLES `invoice_items` WRITE;
/*!40000 ALTER TABLE `invoice_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `invoice_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `invoices`
--

DROP TABLE IF EXISTS `invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoices` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) DEFAULT NULL,
  `document_url` varchar(500) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `invoice_date` date NOT NULL,
  `invoice_number` varchar(50) NOT NULL,
  `notes` text,
  `paid_date` date DEFAULT NULL,
  `payment_method` varchar(50) DEFAULT NULL,
  `status` enum('DRAFT','SENT','PAID','OVERDUE','CANCELLED') NOT NULL,
  `total_ht` decimal(10,2) NOT NULL,
  `total_ttc` decimal(10,2) NOT NULL,
  `total_tva` decimal(10,2) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `client_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_invoice_number` (`invoice_number`),
  KEY `idx_invoice_client_id` (`client_id`),
  KEY `idx_invoice_status` (`status`),
  KEY `idx_invoice_date` (`invoice_date`),
  CONSTRAINT `FK9ioqm804urbgy986pdtwqtl0x` FOREIGN KEY (`client_id`) REFERENCES `clients` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invoices`
--

LOCK TABLES `invoices` WRITE;
/*!40000 ALTER TABLE `invoices` DISABLE KEYS */;
/*!40000 ALTER TABLE `invoices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `link` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `is_read` bit(1) NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(36) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_id`),
  KEY `idx_notif_read` (`is_read`),
  KEY `idx_notif_created` (`created_at`),
  CONSTRAINT `FK_notif_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES ('42217a2a-60d4-4ca7-8eca-c817d95684db','success','2026-02-26 00:16:35.954809','fa-check-circle','/signatures/1983fdb7-f741-4f22-89a2-acf653818836','Paul degroubobabu a sign├® ┬½ facture-20260039489.pdf ┬╗',_binary '','Document sign├® !','SIGNATURE_SIGNED','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('76f648f5-e4e7-450c-b3b4-52a12c4fea00','success','2026-02-26 00:16:35.976322','fa-check-circle','/signatures/154a7222-7a4e-4e79-9550-a4542692c97e','jean degroubobabu a sign├® ┬½ RAPPORT-PENTEST-GEDAVOCAT.pdf ┬╗',_binary '','Document sign├® !','SIGNATURE_SIGNED','7837d9f6-14bb-4bdf-890f-7160fc43911e'),('f1ad3547-dc4b-4519-ae17-8b0c8ec01600','warning','2026-02-26 00:06:54.544252','fa-file-signature','/signatures/1983fdb7-f741-4f22-89a2-acf653818836','Demande de signature envoy├®e ├á Paul degroubobabu pour ┬½ facture-20260039489.pdf ┬╗',_binary '','Signature envoy├®e','SIGNATURE_PENDING','7837d9f6-14bb-4bdf-890f-7160fc43911e');
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `id` varchar(36) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `billing_period` enum('MONTHLY','YEARLY') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payplug_payment_id` varchar(255) DEFAULT NULL,
  `status` enum('PENDING','PAID','FAILED','REFUNDED') NOT NULL,
  `subscription_plan` enum('SOLO','CABINET','ENTERPRISE') NOT NULL,
  `user_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_1rpr2koni2lp3ad7h9xeeta0a` (`payplug_payment_id`),
  KEY `idx_payment_user` (`user_id`),
  KEY `idx_payment_status` (`status`),
  KEY `idx_payment_payplug` (`payplug_payment_id`),
  CONSTRAINT `FKj94hgy9v5fw1munb90tar2eje` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payments`
--

LOCK TABLES `payments` WRITE;
/*!40000 ALTER TABLE `payments` DISABLE KEYS */;
/*!40000 ALTER TABLE `payments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permissions`
--

DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` varchar(36) NOT NULL,
  `can_read` bit(1) NOT NULL,
  `can_upload` bit(1) NOT NULL,
  `can_write` bit(1) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `granted_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `case_id` varchar(36) NOT NULL,
  `granted_by` varchar(36) NOT NULL,
  `lawyer_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1u3df3rlp9k91rog6li47rdkj` (`case_id`,`lawyer_id`),
  KEY `idx_permission_case_id` (`case_id`),
  KEY `idx_permission_lawyer_id` (`lawyer_id`),
  KEY `idx_permission_granted_by` (`granted_by`),
  CONSTRAINT `FK1bi3gt1kq5kxy275y5ej18rqg` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`),
  CONSTRAINT `FK7diih5y5mnl22sssp112oyb0f` FOREIGN KEY (`lawyer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKrhwhqi139h5ie6odks75k65l7` FOREIGN KEY (`granted_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permissions`
--

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
INSERT INTO `permissions` VALUES ('0eae8add-ac70-496d-9c42-ac73565e3455',_binary '',_binary '\0',_binary '\0',NULL,'2026-02-25 18:00:16.357166',_binary '',NULL,'f8d84bb7-72d1-4aec-af75-1bef3c960f61','7837d9f6-14bb-4bdf-890f-7160fc43911e','db924060-e627-44e6-85d0-5ae5590255c2');
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rpva_communications`
--

DROP TABLE IF EXISTS `rpva_communications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rpva_communications` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `jurisdiction` varchar(255) NOT NULL,
  `reference_number` varchar(100) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('DRAFT','SENT','DELIVERED','READ','FAILED') NOT NULL,
  `type` enum('ASSIGNATION','CONCLUSIONS','MEMOIRE','PIECE','NOTIFICATION') NOT NULL,
  `case_id` varchar(36) NOT NULL,
  `sent_by` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rpva_case` (`case_id`),
  KEY `idx_rpva_status` (`status`),
  KEY `idx_rpva_reference` (`reference_number`),
  KEY `FKrgaby1yj1phdnnw3kf3501ij5` (`sent_by`),
  CONSTRAINT `FKrgaby1yj1phdnnw3kf3501ij5` FOREIGN KEY (`sent_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsinomj05d1e6fbls1aknldvpn` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rpva_communications`
--

LOCK TABLES `rpva_communications` WRITE;
/*!40000 ALTER TABLE `rpva_communications` DISABLE KEYS */;
/*!40000 ALTER TABLE `rpva_communications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `signatures`
--

DROP TABLE IF EXISTS `signatures`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signatures` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `document_name` varchar(255) NOT NULL,
  `signed_at` datetime(6) DEFAULT NULL,
  `signer_email` varchar(255) NOT NULL,
  `signer_name` varchar(255) NOT NULL,
  `status` enum('DRAFT','PENDING','SIGNED','REJECTED','EXPIRED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `yousign_signature_request_id` varchar(255) DEFAULT NULL,
  `document_id` varchar(36) NOT NULL,
  `requested_by` varchar(36) NOT NULL,
  `signature_level` varchar(32) DEFAULT NULL,
  `case_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_gr16sflr92d7ppxc24ss5n73e` (`yousign_signature_request_id`),
  KEY `idx_signature_document` (`document_id`),
  KEY `idx_signature_status` (`status`),
  KEY `idx_signature_yousign` (`yousign_signature_request_id`),
  KEY `FK3if6ak3tyutx3y2iky2jqfkb4` (`requested_by`),
  KEY `FKcweyiqfth2exrdryms6krq2fk` (`case_id`),
  CONSTRAINT `FK3if6ak3tyutx3y2iky2jqfkb4` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK7h3u1gbbulfoe5x33ao8hvfvw` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`),
  CONSTRAINT `FKcweyiqfth2exrdryms6krq2fk` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `signatures`
--

LOCK TABLES `signatures` WRITE;
/*!40000 ALTER TABLE `signatures` DISABLE KEYS */;
INSERT INTO `signatures` VALUES ('154a7222-7a4e-4e79-9550-a4542692c97e','2026-02-25 23:50:22.262391','RAPPORT-PENTEST-GEDAVOCAT.pdf','2026-02-26 00:16:35.934474','dufayet.jc@gmail.com','jean degroubobabu','SIGNED','2026-02-26 00:16:36.027164','154a7222-7a4e-4e79-9550-a4542692c97e','320906d1-8037-4f7d-9f04-2ca2a0bee177','7837d9f6-14bb-4bdf-890f-7160fc43911e','qualified','f8d84bb7-72d1-4aec-af75-1bef3c960f61'),('1983fdb7-f741-4f22-89a2-acf653818836','2026-02-26 00:06:54.519131','facture-20260039489.pdf','2026-02-26 00:16:35.820265','dufayet.jc@gmail.com','Paul degroubobabu','SIGNED','2026-02-26 00:16:36.012963','1983fdb7-f741-4f22-89a2-acf653818836','57c5203f-05f2-4d1c-997b-1c5c6ba8e474','7837d9f6-14bb-4bdf-890f-7160fc43911e','simple','f8d84bb7-72d1-4aec-af75-1bef3c960f61');
/*!40000 ALTER TABLE `signatures` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` varchar(36) NOT NULL,
  `access_ends_at` datetime(6) DEFAULT NULL,
  `bar_number` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `first_name` varchar(100) DEFAULT NULL,
  `gdpr_consent_at` datetime(6) DEFAULT NULL,
  `invitation_id` varchar(36) DEFAULT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `max_clients` int DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `role` enum('ADMIN','LAWYER','CLIENT','LAWYER_SECONDARY','HUISSIER') NOT NULL,
  `subscription_ends_at` datetime(6) DEFAULT NULL,
  `subscription_plan` enum('ESSENTIEL','PROFESSIONNEL','CABINET_PLUS') DEFAULT NULL,
  `subscription_start_date` datetime(6) DEFAULT NULL,
  `subscription_status` enum('ACTIVE','INACTIVE','CANCELLED','TRIAL','PAYMENT_FAILED') DEFAULT NULL,
  `terms_accepted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `account_enabled` bit(1) NOT NULL DEFAULT b'1',
  `email_verified` bit(1) NOT NULL DEFAULT b'1',
  `reset_token` varchar(36) DEFAULT NULL,
  `reset_token_expiry` datetime(6) DEFAULT NULL,
  `stripe_customer_id` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  KEY `idx_user_email` (`email`),
  KEY `idx_user_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('0f1ddde1-1d7f-479b-bbbe-19916739b0c8',NULL,NULL,'2026-02-25 15:08:05.477985','laprelleicece-4501@yopmail.com','LA',NULL,NULL,'PPP',10,'LA PPP','$2a$12$W6DatKymcp1YPoguu5tiX.HGKwBcRUu0VzQbuGm/YVdrrY6Z2BWlC',NULL,'LAWYER_SECONDARY',NULL,NULL,NULL,NULL,NULL,'2026-02-25 15:08:05.478013',_binary '',_binary '',NULL,NULL,NULL),('2d4117ac-8599-46eb-8c9d-68bda976c94a',NULL,NULL,'2026-02-25 17:03:39.980537','froijuvouwouhou-9708@yopmail.com','Jean',NULL,NULL,'DUPONT',10,'Jean DUPONT','$2a$12$4lfW.2ceu58B/NfjPNIcH.qjjS6UY2m0SZNmznqxVAMzkRML0XBWe',NULL,'LAWYER_SECONDARY',NULL,NULL,NULL,NULL,NULL,'2026-02-25 17:03:39.980570',_binary '',_binary '',NULL,NULL,NULL),('3fd5cfa2-503d-4530-8988-12ec2bec7ecf',NULL,NULL,'2026-02-25 15:23:00.068435','cureweukeppa-4728@yopmail.com','Machin',NULL,NULL,'truic',10,'Machin truic','$2a$12$NT2ShlNIeRFLfiBazx0zFe.mdwa18ve2GQwXQFokstX6E4UqvYTAi',NULL,'CLIENT',NULL,NULL,NULL,NULL,NULL,'2026-02-25 15:23:00.068459',_binary '',_binary '',NULL,NULL,NULL),('62d78f4f-0e93-4c3d-be2d-e4530078a65a',NULL,NULL,'2026-02-25 17:56:43.053456','degroubobabu-2311@yopmail.com','degroubobabu',NULL,NULL,'',10,'degroubobabu','$2a$12$E8Cd7hDGvbaFB.v34Tpvfega81P7J9fcpJXJUHOQfjP7KgoowUQMq',NULL,'CLIENT',NULL,NULL,NULL,NULL,NULL,'2026-02-25 17:56:43.053499',_binary '',_binary '',NULL,NULL,NULL),('7837d9f6-14bb-4bdf-890f-7160fc43911e',NULL,NULL,'2026-02-21 23:53:42.911292','ghrissi.ahmed@gmail.com','Ahmed','2026-02-21 23:53:42.828187',NULL,'GHRISSI',10,'Ahmed GHRISSI','$2a$10$v9B14uxDvxqqnv9lYZWLZ.p1DhWf8oa1O8Wdrs8RIQ0ad0.T9KuVG',NULL,'LAWYER',NULL,'ESSENTIEL','2026-02-21 23:53:42.828218','ACTIVE','2026-02-21 23:53:42.828147','2026-02-25 15:30:27.188077',_binary '',_binary '',NULL,NULL,NULL),('c859826f-db2d-42b9-80a2-a31492cb61d4',NULL,NULL,'2026-02-25 17:00:53.380293','wuyeddauppeproi-6646@yopmail.com','Jean-Christophe',NULL,NULL,'DUFAYET',10,'Jean-Christophe DUFAYET','$2a$12$TbQMjLh2rVCpte3oIckU3eayv.yyOVaxcfvQiMyGy76UAfhZ6wPrG',NULL,'CLIENT',NULL,NULL,NULL,NULL,NULL,'2026-02-25 17:00:53.380337',_binary '',_binary '',NULL,NULL,NULL),('db924060-e627-44e6-85d0-5ae5590255c2',NULL,NULL,'2026-02-25 18:00:16.308912','gofrexeufrupru-4641@yopmail.com','Collabe',NULL,NULL,'Avocat',10,'Collabe Avocat','$2a$12$/7WtBSh8GymjRorzC6zktOJCogeRNBJQw.VeFsPqiAnd4AOazYJEW',NULL,'LAWYER_SECONDARY',NULL,NULL,NULL,NULL,NULL,'2026-02-25 18:00:16.308948',_binary '',_binary '',NULL,NULL,NULL),('e81efeae-a750-4a8c-a509-e1f31ee4e7ae',NULL,NULL,'2026-02-20 17:14:11.000000','admin@docavocat.fr','Admin',NULL,NULL,'GED',2147483647,'Admin GED','$2a$12$TtqASEIhOtIVlmNvkriV6egrTHZxFA.nb/uZHqMC0RFcXuJ1Htjvu',NULL,'ADMIN',NULL,'CABINET_PLUS',NULL,'ACTIVE',NULL,'2026-02-20 17:14:11.000000',_binary '',_binary '',NULL,NULL,NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'gedavocat'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-02-25 23:25:05
