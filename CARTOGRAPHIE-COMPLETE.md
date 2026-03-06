# CARTOGRAPHIE COMPLÈTE — GedAvocat Spring Boot

> **Généré le** : 2025 — Application GedAvocat (SaaS de gestion de cabinets d'avocats)  
> **Stack** : Spring Boot 3 + Thymeleaf + Spring Security + JPA/Hibernate + Lombok  
> **Architecture** : Multi-tenant (isolation par `firmId`), abonnement Stripe/PayPlug  

---

## TABLE DES MATIÈRES

1. [Contrôleurs (34)](#1-contrôleurs-34)
2. [Routes (toutes)](#2-routes-complètes)
3. [Templates Thymeleaf (81)](#3-templates-thymeleaf-81)
4. [Assets statiques (32)](#4-assets-statiques-32)
5. [Entités / Modèles (18)](#5-entités--modèles-18)
6. [Services (30)](#6-services-30)
7. [Repositories (18)](#7-repositories-18)
8. [Configuration sécurité](#8-configuration-sécurité)
9. [Filtres et intercepteurs (7)](#9-filtres-et-intercepteurs-7)
10. [Rôles et permissions](#10-rôles-et-permissions)

---

## 1. CONTRÔLEURS (34)

| # | Classe | Type | Base Path | Accès |
|---|--------|------|-----------|-------|
| 1 | `AuthController` | `@Controller` | `/` | Public + API |
| 2 | `AdminController` | `@Controller` | `/admin` | ADMIN |
| 3 | `AdminApiController` | `@RestController` | `/api/admin` | ADMIN |
| 4 | `DashboardController` | `@Controller` | `/` | Authentifié |
| 5 | `CaseController` | `@Controller` | `/cases` | LAWYER, ADMIN, LAWYER_SECONDARY |
| 6 | `ClientController` | `@Controller` | `/clients` | LAWYER, ADMIN, LAWYER_SECONDARY |
| 7 | `DocumentController` | `@Controller` | `/documents` | LAWYER, ADMIN, LAWYER_SECONDARY |
| 8 | `InvoiceController` | `@RestController` | `/api/invoices` | LAWYER, CLIENT, ADMIN |
| 9 | `InvoiceWebController` | `@Controller` | `/invoices` | LAWYER, CLIENT, ADMIN |
| 10 | `AppointmentController` | `@Controller` | `/appointments` | LAWYER, ADMIN |
| 11 | `AppointmentClientController` | `@Controller` | `/client/appointments` | CLIENT |
| 12 | `SignatureController` | `@Controller` | `/signatures` | LAWYER, ADMIN |
| 13 | `RPVAController` | `@Controller` | `/rpva` | LAWYER, ADMIN |
| 14 | `SettingsController` | `@Controller` | `/settings` | LAWYER, ADMIN |
| 15 | `ClientPortalController` | `@Controller` | `/my-cases` | CLIENT |
| 16 | `CollaboratorPortalController` | `@Controller` | `/my-cases-collab` | LAWYER_SECONDARY |
| 17 | `HuissierPortalController` | `@Controller` | `/my-cases-huissier` | HUISSIER |
| 18 | `CollaboratorInvitationController` | `@Controller` | `/collaborators` | Public |
| 19 | `HuissierInvitationController` | `@Controller` | `/huissiers` | Public |
| 20 | `PaymentController` | `@Controller` | `/payment` | Authentifié + Public (webhook) |
| 21 | `SubscriptionController` | `@Controller` | `/subscription` | Public + Authentifié |
| 22 | `PasswordResetController` | `@Controller` | `/` | Public |
| 23 | `NotificationController` | `@Controller` | `/api/notifications` | Authentifié |
| 24 | `DocumentShareController` | `@RestController` | `/api/document-shares` | LAWYER, ADMIN |
| 25 | `CaseShareController` | `@Controller` | `/cases` | LAWYER, ADMIN + Public |
| 26 | `ComplianceController` | `@RestController` | `/api/compliance` | ADMIN, COMPLIANCE_OFFICER |
| 27 | `ClientFeaturesController` | `@Controller` | `/` | CLIENT |
| 28 | `MaintenanceController` | `@Controller` | `/` | Public + ADMIN |
| 29 | `FaviconController` | `@Controller` | `/` | Public |
| 30 | `SitemapController` | `@Controller` | `/` | Public |
| 31 | `LegalController` | `@Controller` | `/legal` | Public |
| 32 | `RgpdController` | `@Controller` | `/rgpd` | Authentifié |
| 33 | `SecurityAuditController` | `@RestController` | `/api/security` | ADMIN, DPO |
| 34 | `SecurityMonitoringController` | `@Controller` | `/admin/security-monitoring` | ADMIN |
| 35 | `SecurityAdminController` | `@RestController` | `/api/security/admin` | ADMIN, DPO |
| 36 | `TestDataController` | `@RestController` | `/test` | dev/local profile only |
| 37 | `DatabaseMigrationController` | `@RestController` | `/api/admin/migration` | ADMIN |

---

## 2. ROUTES COMPLÈTES

### 2.1 Authentification (`AuthController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/login` | `auth/login` | Public |
| `GET` | `/register` | `auth/register` | Public |
| `POST` | `/register` | redirect `/verify-email` | Public |
| `GET` | `/verify-email` | `auth/verify-email` | Public |
| `POST` | `/verify-email` | redirect Stripe checkout | Public |
| `POST` | `/verify-email/resend` | redirect `/verify-email` | Public |
| `POST` | `/api/auth/login` | JSON (JWT) | Public |
| `POST` | `/api/auth/register` | JSON | Public |
| `POST` | `/api/auth/refresh` | JSON (new JWT) | Public |
| `POST` | `/api/auth/logout` | JSON | Authentifié |

### 2.2 Mot de passe oublié (`PasswordResetController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/forgot-password` | `auth/forgot-password` | Public |
| `POST` | `/forgot-password` | redirect `/login` | Public |
| `GET` | `/reset-password` | `auth/reset-password` | Public |
| `POST` | `/reset-password` | redirect `/login` | Public |

### 2.3 Tableau de bord (`DashboardController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/` | `landing` | Public |
| `GET` | `/welcome` | `landing` | Public |
| `GET` | `/dashboard` | Redirection basée sur le rôle | Authentifié |

Redirections dashboard par rôle :
- **ADMIN** → `/admin`
- **LAWYER** → `dashboard/index`
- **CLIENT** → `/my-cases`
- **LAWYER_SECONDARY** → `/my-cases-collab`
- **HUISSIER** → `/my-cases-huissier`

### 2.4 Administration (`AdminController` + `AdminApiController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/admin` | `admin/dashboard` | ADMIN |
| `GET` | `/admin/system` | `admin/system` | ADMIN |
| `GET` | `/admin/logs` | `admin/logs` | ADMIN |
| `GET` | `/admin/users` | `admin/users` | ADMIN |
| `GET` | `/admin/users/{id}` | JSON (user detail) | ADMIN |
| `POST` | `/admin/users/{id}/edit` | redirect `/admin/users` | ADMIN |
| `POST` | `/admin/users/{id}/delete` | redirect `/admin/users` | ADMIN |
| `POST` | `/admin/users/{id}/toggle-block` | redirect `/admin/users` | ADMIN |
| `GET` | `/admin/database` | `admin/database` | ADMIN |
| `GET` | `/admin/statistics` | `admin/statistics` | ADMIN |
| `GET` | `/admin/settings` | `admin/settings` | ADMIN |
| `GET` | `/admin/security` | `admin/security` | ADMIN |
| `POST` | `/admin/settings/toggle-maintenance` | redirect `/admin/settings` | ADMIN |
| `POST` | `/admin/users/create` | redirect `/admin/users` | ADMIN |
| `POST` | `/admin/users/send-invitation` | redirect `/admin/users` | ADMIN |
| `POST` | `/api/admin/gc` | JSON | ADMIN |
| `POST` | `/api/admin/backup` | JSON | ADMIN |
| `POST` | `/api/admin/clean-audit-logs` | JSON | ADMIN |

### 2.5 Dossiers (`CaseController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/cases` | `cases/list` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/cases/new` | `cases/form` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/cases` | redirect `/cases/{id}` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/cases/{id}` | `cases/view` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/cases/{id}/edit` | `cases/form` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/cases/{id}` | redirect `/cases/{id}` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/cases/{id}/close` | redirect `/cases/{id}` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/cases/{id}/archive` | redirect `/cases/{id}` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/cases/{id}/delete` | redirect `/cases` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/cases/{caseId}/permissions/{permissionId}/revoke` | redirect `/cases/{caseId}` | LAWYER, ADMIN, LAWYER_SECONDARY |

### 2.6 Partage de dossiers (`CaseShareController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/cases/{id}/share` | `cases/share` | LAWYER, ADMIN |
| `POST` | `/cases/{id}/share` | redirect `/cases/{id}/share` | LAWYER, ADMIN |
| `POST` | `/cases/{id}/share/{linkId}/revoke` | redirect `/cases/{id}/share` | LAWYER, ADMIN |
| `GET` | `/cases/shared` | `cases/shared-view` ou `cases/shared-expired` | Public |

### 2.7 Clients (`ClientController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/clients` | `clients/list` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/clients/new` | `clients/form` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/clients` | redirect `/clients/{id}` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/clients/{id}` | `clients/view` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/clients/{id}/edit` | `clients/form` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/clients/{id}` | redirect `/clients/{id}` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/clients/{id}/delete` | redirect `/clients` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/clients/accept-invitation` | `clients/accept-invitation` | Public |
| `POST` | `/clients/accept-invitation` | redirect `/login` | Public |

### 2.8 Documents (`DocumentController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/documents` | `documents/index` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/documents/case/{caseId}` | `documents/list` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/documents/case/{caseId}/trash` | `documents/trash` | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/documents/case/{caseId}/upload` | redirect | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/documents/case/{caseId}/upload-ajax` | JSON | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/documents/{documentId}/upload-version` | redirect | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/documents/{id}/download` | Téléchargement fichier | LAWYER, ADMIN, LAWYER_SECONDARY |
| `GET` | `/documents/{id}/preview` | Aperçu inline | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/documents/{id}/delete` | redirect | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/documents/{id}/restore` | redirect | LAWYER, ADMIN, LAWYER_SECONDARY |
| `POST` | `/documents/{id}/delete-permanent` | redirect | LAWYER, ADMIN, LAWYER_SECONDARY |

### 2.9 Partage de documents (`DocumentShareController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `POST` | `/api/document-shares/toggle` | JSON | LAWYER, ADMIN |
| `POST` | `/api/document-shares/bulk-toggle` | JSON | LAWYER, ADMIN |

### 2.10 Factures — API REST (`InvoiceController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `POST` | `/api/invoices` | JSON | LAWYER |
| `PUT` | `/api/invoices/{invoiceId}` | JSON | LAWYER |
| `GET` | `/api/invoices/{invoiceId}` | JSON | LAWYER, CLIENT, ADMIN |
| `GET` | `/api/invoices/client/{clientId}` | JSON | LAWYER, ADMIN |
| `GET` | `/api/invoices/lawyer/{lawyerId}` | JSON | LAWYER, ADMIN |
| `GET` | `/api/invoices/lawyer/{lawyerId}/overdue` | JSON | LAWYER, ADMIN |
| `PATCH` | `/api/invoices/{invoiceId}/mark-as-paid` | JSON | LAWYER |
| `DELETE` | `/api/invoices/{invoiceId}` | JSON | LAWYER |
| `GET` | `/api/invoices/generate-number` | JSON | LAWYER |

### 2.11 Factures — Web (`InvoiceWebController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/invoices` | `invoices/index` | LAWYER, ADMIN |
| `GET` | `/invoices/new` | `invoices/new` | LAWYER, ADMIN |
| `POST` | `/invoices/import` | redirect `/invoices` | LAWYER, ADMIN |
| `GET` | `/invoices/{id}` | `invoices/show` | LAWYER, CLIENT |
| `GET` | `/invoices/{id}/edit` | `invoices/edit` | LAWYER, ADMIN |
| `GET` | `/invoices/my-invoices` | `invoices/my-invoices` | CLIENT |
| `GET` | `/invoices/{id}/pdf` | Téléchargement PDF | LAWYER, CLIENT, ADMIN |

### 2.12 Rendez-vous — Avocat (`AppointmentController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/appointments` | `appointments/calendar` | LAWYER, ADMIN |
| `GET` | `/appointments/list` | `appointments/list` | LAWYER, ADMIN |
| `GET` | `/appointments/new` | `appointments/form` | LAWYER, ADMIN |
| `POST` | `/appointments/create` | redirect | LAWYER, ADMIN |
| `GET` | `/appointments/{id}/edit` | `appointments/form` | LAWYER, ADMIN |
| `POST` | `/appointments/{id}/update` | redirect | LAWYER, ADMIN |
| `POST` | `/appointments/{id}/delete` | redirect | LAWYER, ADMIN |
| `POST` | `/appointments/{id}/cancel` | redirect | LAWYER, ADMIN |
| `POST` | `/appointments/{id}/confirm` | redirect | LAWYER, ADMIN |

### 2.13 Rendez-vous — Client (`AppointmentClientController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/client/appointments` | `client-portal/appointments` | CLIENT |
| `POST` | `/client/appointments/{id}/confirm` | redirect | CLIENT |
| `POST` | `/client/appointments/{id}/refuse` | redirect | CLIENT |
| `POST` | `/client/appointments/{id}/propose-date` | redirect | CLIENT |

### 2.14 Fonctionnalités client (`ClientFeaturesController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/my-appointments` | `client-portal/appointments` | CLIENT |
| `GET` | `/my-signatures` | `client-portal/signatures` | CLIENT |
| `POST` | `/my-appointments/{id}/confirm` | redirect | CLIENT |
| `POST` | `/my-appointments/{id}/request-reschedule` | redirect | CLIENT |

### 2.15 Signatures électroniques (`SignatureController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/signatures` | `signatures/index` | LAWYER, ADMIN |
| `GET` | `/signatures/new` | `signatures/new` | LAWYER, ADMIN |
| `GET` | `/signatures/api/cases/{caseId}/documents` | JSON | LAWYER, ADMIN |
| `POST` | `/signatures/create` | redirect | LAWYER, ADMIN |
| `GET` | `/signatures/{signatureId}` | `signatures/view` | LAWYER, ADMIN |

### 2.16 RPVA / e-Barreau (`RPVAController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/rpva` | `rpva/index` | LAWYER, ADMIN |
| `GET` | `/rpva/received` | `rpva/received` | LAWYER, ADMIN |
| `GET` | `/rpva/send` | `rpva/send` | LAWYER, ADMIN |
| `POST` | `/rpva/send` | redirect `/rpva` | LAWYER, ADMIN |
| `GET` | `/rpva/communications/{communicationId}` | `rpva/view` | LAWYER, ADMIN |
| `GET` | `/rpva/communications/{communicationId}/receipt` | Téléchargement PDF | LAWYER, ADMIN |
| `GET` | `/rpva/jurisdictions/search` | `rpva/search-results` | LAWYER, ADMIN |
| `POST` | `/rpva/cases/register` | redirect `/rpva` | LAWYER, ADMIN |

### 2.17 Paramètres (`SettingsController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/settings` | `settings/index` | LAWYER, ADMIN |
| `POST` | `/settings/yousign` | redirect ou JSON | LAWYER, ADMIN |
| `POST` | `/settings/yousign/test` | JSON | LAWYER, ADMIN |
| `POST` | `/settings/profile` | redirect ou JSON | LAWYER, ADMIN |
| `POST` | `/settings/password` | redirect | LAWYER, ADMIN |
| `GET` | `/settings/user-data` | JSON | LAWYER, ADMIN |
| `POST` | `/settings/change-password` | JSON | LAWYER, ADMIN |

### 2.18 Portail client (`ClientPortalController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/my-cases` | `client-portal/cases` | CLIENT |
| `GET` | `/my-cases/{caseId}` | `client-portal/case-detail` | CLIENT |
| `GET` | `/my-cases/{caseId}/documents` | `client-portal/documents` | CLIENT |
| `POST` | `/my-cases/{caseId}/upload` | redirect | CLIENT |
| `POST` | `/my-cases/{caseId}/upload-ajax` | JSON | CLIENT |
| `GET` | `/my-cases/documents/{documentId}/download` | Téléchargement | CLIENT |
| `GET` | `/my-cases/documents/{documentId}/preview` | Aperçu inline | CLIENT |
| `GET` | `/my-cases/{caseId}/export-zip` | Téléchargement ZIP | CLIENT |
| `GET` | `/my-cases/profile` | `client-portal/profile` | CLIENT |
| `POST` | `/my-cases/profile` | redirect `/my-cases/profile` | CLIENT |

### 2.19 Portail collaborateur (`CollaboratorPortalController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/my-cases-collab` | `collaborator-portal/cases` | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/{caseId}` | `collaborator-portal/case-detail` | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/{caseId}/documents` | `collaborator-portal/documents` | LAWYER_SECONDARY |
| `POST` | `/my-cases-collab/{caseId}/upload` | redirect | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/documents/{documentId}/download` | 403 Forbidden | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/documents/{documentId}/preview` | 403 Forbidden | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/{caseId}/export-zip` | 403 Forbidden | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/calendar` | `collaborator-portal/calendar` | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/calendar/api/events` | JSON | LAWYER_SECONDARY |
| `GET` | `/my-cases-collab/profile` | `collaborator-portal/profile` | LAWYER_SECONDARY |
| `POST` | `/my-cases-collab/profile` | redirect | LAWYER_SECONDARY |

### 2.20 Portail huissier (`HuissierPortalController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/my-cases-huissier` | `huissier-portal/cases` | HUISSIER |
| `GET` | `/my-cases-huissier/{caseId}` | `huissier-portal/case-detail` | HUISSIER |
| `GET` | `/my-cases-huissier/calendar` | `huissier-portal/calendar` | HUISSIER |
| `GET` | `/my-cases-huissier/calendar/api/events` | JSON | HUISSIER |
| `GET` | `/my-cases-huissier/profile` | `huissier-portal/profile` | HUISSIER |
| `POST` | `/my-cases-huissier/profile` | redirect | HUISSIER |

### 2.21 Invitations collaborateur (`CollaboratorInvitationController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/collaborators/accept-invitation` | `collaborators/accept-invitation` | Public |
| `POST` | `/collaborators/accept-invitation` | redirect `/login` | Public |
| `GET` | `/collaborators/invitation-info` | 404 (désactivé) | Public |

### 2.22 Invitations huissier (`HuissierInvitationController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/huissiers/accept-invitation` | `huissier-portal/accept-invitation` | Public |
| `POST` | `/huissiers/accept-invitation` | redirect `/login` | Public |
| `GET` | `/huissiers/invitation-info` | 404 (désactivé) | Public |

### 2.23 Paiement PayPlug (`PaymentController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/payment/pricing` | redirect `/subscription/pricing` | Public |
| `GET` | `/payment/checkout` | redirect `/subscription/checkout` | Authentifié |
| `GET` | `/payment/success` | `payment/success` | Public |
| `GET` | `/payment/cancel` | `payment/cancel` | Public |
| `POST` | `/payment/webhook` | JSON (HMAC vérifié) | Public |
| `GET` | `/payment/manage` | `payment/manage` | Authentifié |
| `POST` | `/payment/cancel-subscription` | redirect `/payment/manage` | Authentifié |

### 2.24 Abonnement Stripe (`SubscriptionController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/subscription/pricing` | `subscription/pricing` | Public |
| `GET` | `/subscription/checkout` | redirect Stripe Checkout | Authentifié |
| `GET` | `/subscription/success` | `payment/success` | Public |
| `GET` | `/subscription/cancel` | `payment/cancel` | Public |
| `GET` | `/subscription/manage` | `payment/manage` | Authentifié |
| `GET` | `/subscription/change-plan` | `subscription/change-plan` | Authentifié |
| `POST` | `/subscription/change-plan` | redirect | Authentifié |
| `POST` | `/subscription/webhook` | JSON (Stripe signature) | Public |

### 2.25 Notifications (`NotificationController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/api/notifications` | JSON | Authentifié |
| `POST` | `/api/notifications/mark-read` | JSON | Authentifié |
| `POST` | `/api/notifications/{id}/read` | JSON | Authentifié |

### 2.26 Conformité (`ComplianceController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/api/compliance/score` | JSON | ADMIN, COMPLIANCE_OFFICER |
| `GET` | `/api/compliance/internal/compliance-score` | JSON | Public (monitoring) |
| `GET` | `/api/compliance/acpr` | JSON | ADMIN, COMPLIANCE_OFFICER |
| `GET` | `/api/compliance/rgpd` | JSON | ADMIN, COMPLIANCE_OFFICER |
| `GET` | `/api/compliance/iso27001` | JSON | ADMIN, COMPLIANCE_OFFICER |
| `GET` | `/api/compliance/eidas` | JSON | ADMIN, COMPLIANCE_OFFICER |

### 2.27 Maintenance (`MaintenanceController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/maintenance` | `maintenance` | Public |
| `GET` | `/api/admin/maintenance/status` | JSON | ADMIN |
| `POST` | `/api/admin/maintenance/toggle` | JSON | ADMIN |

### 2.28 Pages légales (`LegalController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/legal/privacy` | `legal/privacy` | Public |
| `GET` | `/legal/terms` | `legal/terms` | Public |

### 2.29 RGPD (`RgpdController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/rgpd/export` | JSON (export données Art. 20) | Authentifié |
| `POST` | `/rgpd/delete-account` | redirect `/login` (suppression Art. 17) | Authentifié |

### 2.30 Audit sécurité (`SecurityAuditController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/api/security/audit/technical/run` | JSON | ADMIN, DPO |
| `GET` | `/api/security/audit/technical/report` | Téléchargement texte | ADMIN, DPO |
| `GET` | `/api/security/dashboard` | JSON | ADMIN, DPO |
| `GET` | `/api/security/audit/domain/{domainName}` | JSON | ADMIN, DPO |
| `POST` | `/api/security/pentest/{testType}` | JSON | ADMIN, DPO |

### 2.31 Monitoring sécurité (`SecurityMonitoringController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/admin/security-monitoring` | `admin/security-monitoring` | ADMIN |
| `GET` | `/admin/security-monitoring/api/metrics` | JSON | ADMIN |
| `GET` | `/admin/security-monitoring/api/events` | JSON | ADMIN |
| `GET` | `/admin/security-monitoring/export` | JSON (rapport) | ADMIN |

### 2.32 Administration sécurité (`SecurityAdminController`)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/api/security/admin/dashboard` | JSON | ADMIN, DPO |
| `POST` | `/api/security/admin/mfa/{userId}/setup` | JSON | ADMIN |
| `POST` | `/api/security/admin/mfa/{userId}/validate` | JSON | ADMIN, DPO |
| `DELETE` | `/api/security/admin/mfa/{userId}` | JSON | ADMIN |
| `GET` | `/api/security/admin/mfa/status` | JSON | ADMIN, DPO |

### 2.33 Favicon et SEO

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `GET` | `/favicon.ico` | redirect `/favicon.svg` | Public |
| `GET` | `/sitemap.xml` | XML dynamique | Public |

### 2.34 Test / Migration (dev uniquement)

| Méthode | Route | Template/Réponse | Rôle requis |
|---------|-------|-----------------|-------------|
| `POST` | `/test/seed` | JSON | dev/local profile |
| `POST` | `/test/login` | JSON | dev/local profile |
| `POST` | `/api/admin/migration/invoice-items-schema` | JSON | ADMIN |
| `GET` | `/api/admin/migration/invoice-items-schema/verify` | JSON | ADMIN |

---

## 3. TEMPLATES THYMELEAF (81)

```
templates/
├── dashboard-institutional.html
├── error.html
├── home.html
├── landing.html
├── layout.html                          ← Layout principal (Thymeleaf Layout Dialect)
├── maintenance.html
│
├── admin/
│   ├── dashboard.html
│   ├── database.html
│   ├── logs.html
│   ├── security-monitoring.html
│   ├── settings.html
│   ├── statistics.html
│   ├── system.html
│   └── users.html
│
├── appointments/
│   ├── calendar.html
│   ├── form.html
│   └── list.html
│
├── auth/
│   ├── forgot-password.html
│   ├── login.html
│   ├── register.html
│   ├── reset-password.html
│   └── verify-email.html
│
├── cases/
│   ├── form.html
│   ├── list.html
│   ├── share.html
│   ├── shared-expired.html
│   ├── shared-view.html
│   └── view.html
│
├── client/
│   └── appointments.html
│
├── client-portal/
│   ├── appointments.html
│   ├── case-detail.html
│   ├── cases.html
│   ├── documents.html
│   ├── pending.html
│   ├── profile.html
│   └── signatures.html
│
├── clients/
│   ├── accept-invitation.html
│   ├── form.html
│   ├── invitation-expired.html
│   ├── list.html
│   └── view.html
│
├── collaborator-portal/
│   ├── calendar.html
│   ├── case-detail.html
│   ├── cases.html
│   ├── documents.html
│   ├── pending.html
│   └── profile.html
│
├── collaborators/
│   └── accept-invitation.html
│
├── dashboard/
│   └── index.html
│
├── documents/
│   ├── index.html
│   ├── list.html
│   └── trash.html
│
├── huissier-portal/
│   ├── accept-invitation.html
│   ├── calendar.html
│   ├── case-detail.html
│   ├── cases.html
│   ├── pending.html
│   └── profile.html
│
├── invoices/
│   ├── edit.html
│   ├── index.html
│   ├── my-invoices.html
│   ├── new.html
│   └── show.html
│
├── legal/
│   ├── privacy.html
│   └── terms.html
│
├── payment/
│   ├── cancel.html
│   ├── manage.html
│   ├── pricing.html
│   └── success.html
│
├── rpva/
│   ├── index.html
│   ├── received.html
│   ├── search-results.html
│   ├── send.html
│   └── view.html
│
├── settings/
│   └── index.html
│
├── signatures/
│   ├── index.html
│   ├── new.html
│   └── view.html
│
└── subscription/
    ├── change-plan.html
    ├── pricing.html
    └── success.html
```

---

## 4. ASSETS STATIQUES (32)

```
static/
├── favicon.svg
├── robots.txt
├── .well-known/
│   └── appspecific/
│       └── com.chrome.devtools.json
│
├── css/
│   ├── app.css
│   ├── buttons-fix-critical.css
│   ├── calendar-modern.css
│   ├── global-unified-theme.css
│   ├── layout.css
│   └── pages/
│       ├── admin.css
│       ├── appointments.css
│       ├── auth.css
│       ├── auth-institutional.css
│       ├── cases.css
│       ├── client-portal.css
│       ├── collaborator-portal.css
│       ├── dashboard.css
│       ├── dashboard-institutional.css
│       ├── documents.css
│       ├── home.css
│       ├── landing.css
│       ├── payment-manage.css
│       ├── pricing.css
│       ├── rpva.css
│       ├── settings.css
│       ├── signatures.css
│       ├── signatures-new.css
│       └── subscription-success.css
│
├── img/
│   └── og-gedavocat.svg
│
└── js/
    ├── app.js
    ├── main.js
    ├── scanner.js
    └── theme.js
```

---

## 5. ENTITÉS / MODÈLES (18)

### 5.1 `User` — Utilisateur du système

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 chars |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `name` | `String` | `name` | NOT NULL, 100 |
| `firstName` | `String` | `first_name` | NOT NULL, 2-100 |
| `lastName` | `String` | `last_name` | NOT NULL, 2-100 |
| `phone` | `String` | `phone` | Pattern validation, 20 |
| `barNumber` | `String` | `bar_number` | Pattern validation, 50 |
| `emailSignature` | `String` | `email_signature` | TEXT |
| `email` | `String` | `email` | NOT NULL, UNIQUE, 255, @Email |
| `password` | `String` | `password` | NOT NULL, 255, @JsonIgnore |
| `role` | `UserRole` (enum) | `role` | NOT NULL, STRING |
| `firm` | `Firm` (ManyToOne) | `firm_id` | FK, LAZY |
| `subscriptionPlan` | `SubscriptionPlan` | `subscription_plan` | STRING, 20 |
| `subscriptionStatus` | `SubscriptionStatus` | `subscription_status` | STRING, 20 |
| `maxClients` | `Integer` | `max_clients` | default 10 |
| `subscriptionStartDate` | `LocalDateTime` | `subscription_start_date` | |
| `subscriptionEndsAt` | `LocalDateTime` | `subscription_ends_at` | |
| `stripeCustomerId` | `String` | `stripe_customer_id` | 100, @JsonIgnore |
| `stripeSubscriptionId` | `String` | `stripe_subscription_id` | 100, @JsonIgnore |
| `billingPeriod` | `String` | `billing_period` | 20 |
| `gdprConsentAt` | `LocalDateTime` | `gdpr_consent_at` | |
| `termsAcceptedAt` | `LocalDateTime` | `terms_accepted_at` | |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |
| `emailVerified` | `boolean` | `email_verified` | NOT NULL, default false |
| `accountEnabled` | `boolean` | `account_enabled` | NOT NULL, default true |
| `accessEndsAt` | `LocalDateTime` | `access_ends_at` | |
| `invitationId` | `String` | `invitation_id` | 36, @JsonIgnore |
| `resetToken` | `String` | `reset_token` | 36, @JsonIgnore |
| `resetTokenExpiry` | `LocalDateTime` | `reset_token_expiry` | @JsonIgnore |
| `mfaSecret` | `String` | `mfa_secret` | 512, AES-256-GCM encrypted |
| `mfaEnabled` | `Boolean` | `mfa_enabled` | default false |
| `mfaBackupCodes` | `String` | `mfa_backup_codes` | 1000 |
| `mfaTempSetup` | `LocalDateTime` | `mfa_temp_setup` | |
| `mfaLastUsed` | `LocalDateTime` | `mfa_last_used` | |

**Relations :**
- `OneToMany` → `Client` (mappedBy `lawyer`)
- `OneToMany` → `Case` (mappedBy `lawyer`)
- `OneToMany` → `AuditLog` (mappedBy `user`)

**Enums internes :**
- `UserRole` : `ADMIN`, `LAWYER`, `CLIENT`, `LAWYER_SECONDARY`, `HUISSIER`
- `SubscriptionPlan` : `SOLO` (29.99€, 10 clients), `CABINET` (99.99€, 75 clients), `ENTERPRISE` (299.99€, ∞), `ESSENTIEL` (legacy), `PROFESSIONNEL` (legacy), `CABINET_PLUS` (legacy)
- `SubscriptionStatus` : `ACTIVE`, `INACTIVE`, `CANCELLED`, `TRIAL`, `PAYMENT_FAILED`

**Index :** `idx_user_email`, `idx_user_role`, `idx_user_firm_id`

---

### 5.2 `Firm` — Cabinet d'avocats (multi-tenant)

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `name` | `String` | `name` | NOT NULL, 2-255 |
| `address` | `String` | `address` | 255 |
| `phone` | `String` | `phone` | 20 |
| `email` | `String` | `email` | 255, @Email |
| `siren` | `String` | `siren` | 14 |
| `tvaNumber` | `String` | `tva_number` | 20 |
| `subscriptionPlan` | `SubscriptionPlan` | `subscription_plan` | default SOLO |
| `subscriptionStatus` | `SubscriptionStatus` | `subscription_status` | default TRIAL |
| `subscriptionStartsAt` | `LocalDateTime` | `subscription_starts_at` | |
| `subscriptionEndsAt` | `LocalDateTime` | `subscription_ends_at` | |
| `maxLawyers` | `Integer` | `max_lawyers` | NOT NULL, default 1 |
| `maxClients` | `Integer` | `max_clients` | NOT NULL, default 10 |
| `stripeCustomerId` | `String` | `stripe_customer_id` | 100 |
| `stripeSubscriptionId` | `String` | `stripe_subscription_id` | 100 |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |

**Relations :**
- `OneToMany` → `User`, `Case`, `Document`, `Client`

**Enums internes :**
- `SubscriptionPlan` : `SOLO` (1 lawyer, 10 clients), `CABINET` (5, 75), `ENTERPRISE` (∞, ∞)
- `SubscriptionStatus` : `ACTIVE`, `TRIAL`, `INACTIVE`, `CANCELLED`, `PAYMENT_FAILED`

**Hibernate Filter :** `@FilterDef(name = "firmFilter")` — clause `firm_id = :firmId` appliquée à toutes les entités filtrées

---

### 5.3 `Client` — Client du cabinet

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `firm` | `Firm` | `firm_id` | FK, LAZY |
| `lawyer` | `User` | `lawyer_id` | FK, NOT NULL |
| `clientUser` | `User` | `client_user_id` | FK (compte portail client) |
| `firstName` | `String` | `first_name` | NOT NULL, 1-100 |
| `lastName` | `String` | `last_name` | NOT NULL, 1-100 |
| `name` | `String` | `name` | NOT NULL, 100 |
| `email` | `String` | `email` | NOT NULL, 255, @Email |
| `phone` | `String` | `phone` | Pattern, 20 |
| `address` | `String` | `address` | TEXT |
| `accessEndsAt` | `LocalDateTime` | `access_ends_at` | |
| `invitationId` | `String` | `invitation_id` | 36, @JsonIgnore |
| `invitedAt` | `LocalDateTime` | `invited_at` | |
| `clientType` | `ClientType` | `client_type` | default INDIVIDUAL |
| `companyName` | `String` | `company_name` | 200 |
| `siret` | `String` | `siret` | 14 digits, Pattern |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |

**Enums :** `ClientType` : `INDIVIDUAL`, `PROFESSIONAL`

**Relations :**
- `OneToMany` → `Case`, `Invoice`

**Hibernate Filter :** `@Filter(name = "firmFilter")`

---

### 5.4 `Case` — Dossier juridique

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `firm` | `Firm` | `firm_id` | FK |
| `lawyer` | `User` | `lawyer_id` | FK, NOT NULL |
| `client` | `Client` | `client_id` | FK, NOT NULL |
| `title` | `String` | `title` | NOT NULL, 255 |
| `name` | `String` | `name` | 255 (synced with title) |
| `reference` | `String` | `reference` | 50 |
| `caseType` | `CaseType` | `case_type` | STRING, 50 |
| `legacyType` | `CaseType` | `type` | NOT NULL (synced) |
| `description` | `String` | `description` | TEXT |
| `status` | `CaseStatus` | `status` | NOT NULL, default OPEN |
| `openedDate` | `LocalDateTime` | `opened_date` | |
| `closedDate` | `LocalDateTime` | `closed_date` | |
| `deadline` | `LocalDateTime` | `deadline` | |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |

**Enums :**
- `CaseStatus` : `OPEN`, `IN_PROGRESS`, `CLOSED`, `ARCHIVED`
- `CaseType` : `CIVIL`, `PENAL`, `COMMERCIAL`, `TRAVAIL`, `FAMILLE`, `IMMOBILIER`, `ADMINISTRATIF`, `FISCAL`, `SOCIAL`, `AUTRE`

**Relations :**
- `OneToMany` → `Document`, `Permission`

**Hibernate Filter :** `@Filter(name = "firmFilter")`

---

### 5.5 `Document` — Document stocké

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `firm` | `Firm` | `firm_id` | FK |
| `caseEntity` | `Case` | `case_id` | FK, NOT NULL |
| `uploadedBy` | `User` | `uploaded_by` | FK |
| `uploaderRole` | `String` | `uploader_role` | NOT NULL, 20 |
| `filename` | `String` | `filename` | NOT NULL, 255 |
| `originalName` | `String` | `original_name` | NOT NULL, 255 |
| `mimetype` | `String` | `mimetype` | 100 |
| `fileSize` | `Long` | `file_size` | |
| `path` | `String` | `path` | NOT NULL, TEXT |
| `version` | `Integer` | `version` | NOT NULL, default 1 |
| `parentDocument` | `Document` | `parent_document_id` | FK (versioning) |
| `isLatest` | `Boolean` | `is_latest` | NOT NULL, default true |
| `deletedAt` | `LocalDateTime` | `deleted_at` | (soft delete) |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |

**Colonnes legacy synchronisées :** `original_filename`, `file_path`, `mime_type`

**Hibernate Filter :** `@Filter(name = "firmFilter")`

---

### 5.6 `Invoice` — Facture

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `invoiceNumber` | `String` | `invoice_number` | NOT NULL, UNIQUE, 50 |
| `client` | `Client` | `client_id` | FK, NOT NULL |
| `caseEntity` | `Case` | `case_id` | FK |
| `firm` | `Firm` | `firm_id` | FK |
| `invoiceDate` | `LocalDate` | `invoice_date` | default now |
| `dueDate` | `LocalDate` | `due_date` | |
| `paidDate` | `LocalDate` | `paid_date` | |
| `status` | `InvoiceStatus` | `status` | NOT NULL, default DRAFT |
| `paymentStatus` | `PaymentStatus` | `payment_status` | default UNPAID |
| `subtotalAmount` | `BigDecimal` | `subtotal_amount` | (10,2) |
| `taxAmount` | `BigDecimal` | `tax_amount` | (10,2) |
| `totalAmount` | `BigDecimal` | `total_amount` | (10,2) |
| `totalHT` | `BigDecimal` | `total_ht` | (10,2) legacy |
| `totalTVA` | `BigDecimal` | `total_tva` | (10,2) legacy |
| `totalTTC` | `BigDecimal` | `total_ttc` | (10,2) legacy |
| `paidAmount` | `BigDecimal` | `paid_amount` | (10,2) |
| `currency` | `String` | `currency` | default "EUR" |
| `notes` | `String` | `notes` | TEXT |
| `paymentMethod` | `String` | `payment_method` | 50 |
| `documentUrl` | `String` | `document_url` | 500 |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |

**Enums :**
- `InvoiceStatus` : `DRAFT`, `SENT`, `PAID`, `OVERDUE`, `CANCELLED`
- `PaymentStatus` : `UNPAID`, `PARTIAL`, `PAID`, `REFUNDED`

**Relations :**
- `OneToMany` → `InvoiceItem`

---

### 5.7 `InvoiceItem` — Ligne de facture

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `invoice` | `Invoice` | `invoice_id` | FK, NOT NULL |
| `description` | `String` | `description` | NOT NULL, 500 |
| `quantity` | `BigDecimal` | `quantity` | NOT NULL, (10,2) |
| `unitPriceHT` | `BigDecimal` | `unit_price_ht` | NOT NULL, (10,2) |
| `tvaRate` | `BigDecimal` | `tva_rate` | NOT NULL, default 20%, (5,2) |
| `totalHT` | `BigDecimal` | `total_ht` | (10,2) calculé |
| `totalTVA` | `BigDecimal` | `total_tva` | (10,2) calculé |
| `totalTTC` | `BigDecimal` | `total_ttc` | (10,2) calculé |
| `displayOrder` | `Integer` | `display_order` | |

---

### 5.8 `Appointment` — Rendez-vous

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `title` | `String` | `title` | NOT NULL, 200 |
| `description` | `String` | `description` | TEXT |
| `appointmentDate` | `LocalDateTime` | `appointment_date` | |
| `endDate` | `LocalDateTime` | `end_date` | |
| `isAllDay` | `Boolean` | `is_all_day` | default false |
| `type` | `AppointmentType` | `type` | NOT NULL, default CLIENT_MEETING |
| `status` | `AppointmentStatus` | `status` | NOT NULL, default SCHEDULED |
| `firm` | `Firm` | `firm_id` | FK |
| `lawyer` | `User` | `lawyer_id` | FK, NOT NULL |
| `client` | `Client` | `client_id` | FK (optionnel) |
| `relatedCase` | `Case` | `case_id` | FK (optionnel) |
| `location` | `String` | `location` | 200 |
| `courtName` | `String` | `court_name` | 200 |
| `courtRoom` | `String` | `court_room` | 50 |
| `judgeName` | `String` | `judge_name` | 100 |
| `sendReminder` | `Boolean` | `send_reminder` | default true |
| `reminderSent` | `Boolean` | `reminder_sent` | default false |
| `reminderMinutesBefore` | `Integer` | `reminder_minutes_before` | default 60 |
| `notes` | `String` | `notes` | TEXT |
| `videoConferenceLink` | `String` | `video_conference_link` | 500 |
| `clientConfirmedAt` | `LocalDateTime` | `client_confirmed_at` | |
| `rescheduleRequestedBy` | `String` | `reschedule_requested_by` | "CLIENT"/"LAWYER" |
| `proposedDate` | `LocalDateTime` | `proposed_date` | |
| `rescheduleMessage` | `String` | `reschedule_message` | 500 |
| `color` | `String` | `color` | 7, default "#3788d8" |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |
| `entityVersion` | `Long` | `entity_version` | `@Version` |

**Enums :**
- `AppointmentType` : `CLIENT_MEETING`, `COURT_HEARING`, `INTERNAL_MEETING`, `PHONE_CALL`, `VIDEO_CONFERENCE`, `SITE_VISIT`, `OTHER`
- `AppointmentStatus` : `SCHEDULED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `RESCHEDULED`, `NO_SHOW`

---

### 5.9 `Signature` — Signature électronique (Yousign)

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `yousignSignatureRequestId` | `String` | `yousign_signature_request_id` | UNIQUE, 255 |
| `documentName` | `String` | `document_name` | NOT NULL, 255 |
| `status` | `SignatureStatus` | `status` | NOT NULL, default DRAFT |
| `document` | `Document` | `document_id` | FK, NOT NULL |
| `caseEntity` | `Case` | `case_id` | FK |
| `requestedBy` | `User` | `requested_by` | FK, NOT NULL |
| `signerName` | `String` | `signer_name` | NOT NULL, 255 |
| `signerEmail` | `String` | `signer_email` | NOT NULL, 255, @Email |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `updatedAt` | `LocalDateTime` | `updated_at` | auto |
| `signedAt` | `LocalDateTime` | `signed_at` | |
| `level` | `String` | `signature_level` | 32 (simple/advanced/qualified) |

**Enums :** `SignatureStatus` : `DRAFT`, `PENDING`, `SIGNED`, `REJECTED`, `EXPIRED`

---

### 5.10 `Permission` — Droits d'accès partagés

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `caseEntity` | `Case` | `case_id` | FK, NOT NULL (UNIQUE avec lawyer_id) |
| `grantedBy` | `User` | `granted_by` | FK, NOT NULL |
| `lawyer` | `User` | `lawyer_id` | FK, NOT NULL |
| `canRead` | `Boolean` | `can_read` | NOT NULL, default false |
| `canWrite` | `Boolean` | `can_write` | NOT NULL, default false |
| `canUpload` | `Boolean` | `can_upload` | NOT NULL, default false |
| `isActive` | `Boolean` | `is_active` | NOT NULL, default true |
| `expiresAt` | `LocalDateTime` | `expires_at` | |
| `grantedAt` | `LocalDateTime` | `granted_at` | NOT NULL, auto |
| `revokedAt` | `LocalDateTime` | `revoked_at` | |
| `entityVersion` | `Long` | `entity_version` | `@Version` |

---

### 5.11 `Notification` — Notification in-app

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `user` | `User` | `user_id` | FK, NOT NULL |
| `type` | `String` | `type` | NOT NULL, 50 |
| `title` | `String` | `title` | NOT NULL, 255 |
| `message` | `String` | `message` | TEXT |
| `link` | `String` | `link` | 500 |
| `icon` | `String` | `icon` | 50 |
| `color` | `String` | `color` | 20 |
| `read` | `boolean` | `is_read` | NOT NULL, default false |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |

---

### 5.12 `Payment` — Historique des paiements

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `paypluGPaymentId` | `String` | `payplug_payment_id` | UNIQUE, 255 |
| `amount` | `BigDecimal` | `amount` | NOT NULL, (10,2) |
| `currency` | `String` | `currency` | default "EUR" |
| `status` | `PaymentStatus` | `status` | NOT NULL, default PENDING |
| `user` | `User` | `user_id` | FK, NOT NULL |
| `subscriptionPlan` | `User.SubscriptionPlan` | `subscription_plan` | NOT NULL |
| `billingPeriod` | `BillingPeriod` | `billing_period` | NOT NULL |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `paidAt` | `LocalDateTime` | `paid_at` | |

**Enums :**
- `PaymentStatus` : `PENDING`, `PAID`, `FAILED`, `REFUNDED`
- `BillingPeriod` : `MONTHLY`, `YEARLY`

---

### 5.13 `AuditLog` — Journal d'audit

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `user` | `User` | `user_id` | FK |
| `action` | `String` | `action` | NOT NULL, 100 |
| `entityType` | `String` | `entity_type` | 50 |
| `entityId` | `String` | `entity_id` | 36 |
| `details` | `String` | `details` | TEXT |
| `ipAddress` | `String` | `ip_address` | 45, @JsonIgnore |
| `userAgent` | `String` | `user_agent` | TEXT, @JsonIgnore |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |

**Enum `AuditAction` :** 27 actions tracées (LOGIN, LOGOUT, CRUD sur User/Client/Case/Document, permissions, abonnement, etc.)

---

### 5.14 `RpvaCommunication` — Communication RPVA (e-Barreau)

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `type` | `CommunicationType` | `type` | NOT NULL |
| `jurisdiction` | `String` | `jurisdiction` | NOT NULL, 255 |
| `referenceNumber` | `String` | `reference_number` | 100 |
| `status` | `CommunicationStatus` | `status` | NOT NULL, default DRAFT |
| `caseEntity` | `Case` | `case_id` | FK, NOT NULL |
| `sentBy` | `User` | `sent_by` | FK, NOT NULL |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `sentAt` | `LocalDateTime` | `sent_at` | |
| `deliveredAt` | `LocalDateTime` | `delivered_at` | |

**Enums :**
- `CommunicationType` : `ASSIGNATION`, `CONCLUSIONS`, `MEMOIRE`, `PIECE`, `NOTIFICATION`
- `CommunicationStatus` : `DRAFT`, `SENT`, `DELIVERED`, `READ`, `FAILED`

---

### 5.15 `RefreshToken` — Token de rafraîchissement JWT

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `token` | `String` | `token` | NOT NULL, UNIQUE, 512 |
| `user` | `User` | `user_id` | FK, NOT NULL |
| `expiresAt` | `LocalDateTime` | `expires_at` | NOT NULL |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |
| `revokedAt` | `LocalDateTime` | `revoked_at` | |
| `deviceFingerprint` | `String` | `device_fingerprint` | 500 |
| `ipAddress` | `String` | `ip_address` | 45 |

---

### 5.16 `DocumentShare` — Partage de document par rôle

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `document` | `Document` | `document_id` | FK, NOT NULL (UNIQUE avec target_role) |
| `caseEntity` | `Case` | `case_id` | FK, NOT NULL |
| `targetRole` | `UserRole` | `target_role` | NOT NULL (LAWYER_SECONDARY ou HUISSIER) |
| `canDownload` | `Boolean` | `can_download` | NOT NULL, default false |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |

---

### 5.17 `CaseShareLink` — Lien temporaire de partage de dossier

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36 |
| `sharedCase` | `Case` | `case_id` | FK, NOT NULL |
| `owner` | `User` | `owner_id` | FK, NOT NULL |
| `token` | `String` | `token` | NOT NULL, UNIQUE, 72 |
| `recipientEmail` | `String` | `recipient_email` | 255 |
| `recipientRole` | `UserRole` | `recipient_role` | |
| `invitedAt` | `LocalDateTime` | `invited_at` | |
| `description` | `String` | `description` | 500 |
| `expiresAt` | `LocalDateTime` | `expires_at` | |
| `maxAccessCount` | `Integer` | `max_access_count` | |
| `accessCount` | `int` | `access_count` | default 0 |
| `revoked` | `boolean` | `revoked` | NOT NULL, default false |
| `entityVersion` | `Long` | `entity_version` | `@Version` |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL, auto |

---

### 5.18 `LABFTCheck` — Contrôle Anti-Blanchiment (LAB-FT / ACPR)

| Champ | Type | Colonne | Contraintes |
|-------|------|---------|-------------|
| `id` | `String` (UUID) | `id` | PK, 36, auto-generated |
| `client` | `Client` | `client_id` | FK |
| `firm` | `Firm` | `firm_id` | FK, NOT NULL |
| `payment` | `Payment` | `payment_id` | FK |
| `checkType` | `CheckType` | `check_type` | NOT NULL |
| `riskLevel` | `RiskLevel` | `risk_level` | |
| `riskScore` | `Integer` | `risk_score` | 0-100 |
| `checkResult` | `CheckResult` | `check_result` | NOT NULL |
| `amount` | `BigDecimal` | `amount` | (10,2) |
| `transactionType` | `String` | `transaction_type` | 50 |
| `alertReasons` | `String` | `alert_reasons` | TEXT (JSON) |
| `riskFactors` | `String` | `risk_factors` | TEXT (JSON) |
| `pepDetected` | `boolean` | `pep_detected` | NOT NULL, default false |
| `sanctionsDetected` | `boolean` | `sanctions_detected` | NOT NULL, default false |
| `tracfinDeclared` | `boolean` | `tracfin_declared` | NOT NULL, default false |
| `tracfinReference` | `String` | `tracfin_reference` | 100 |
| `checkedBy` | `String` | `checked_by` | 36 |
| `automaticCheck` | `boolean` | `automatic_check` | NOT NULL, default true |
| `comments` | `String` | `comments` | TEXT |
| `createdAt` | `LocalDateTime` | `created_at` | NOT NULL |
| `updatedAt` | `LocalDateTime` | `updated_at` | |
| `nextReviewDate` | `LocalDate` | `next_review_date` | |
| `version` | `Long` | `entity_version` | `@Version` |

**Enums :**
- `CheckType` : `VIGILANCE_CLIENT`, `PEP_CHECK`, `SANCTIONS_CHECK`, `TRANSACTION_ANALYSIS`, `TRACFIN_DECLARATION`, `RISK_SCORING`, `DUE_DILIGENCE`
- `RiskLevel` : `FAIBLE`, `MODERE`, `ELEVE`, `CRITIQUE`
- `CheckResult` : `CONFORME`, `ALERTE`, `SUSPECT`, `BLOQUE`

---

## 6. SERVICES (30)

| # | Classe | Description |
|---|--------|-------------|
| 1 | `AdminMetricsService` | Métriques d'administration système |
| 2 | `AppointmentReminderService` | Rappels automatiques de rendez-vous |
| 3 | `AppointmentService` | CRUD rendez-vous |
| 4 | `AuditService` | Journal d'audit (traçabilité) |
| 5 | `AuthService` | Authentification (login, register, JWT) |
| 6 | `CaseService` | CRUD dossiers juridiques |
| 7 | `CaseShareService` | Partage de dossiers par lien |
| 8 | `ClientInvitationService` | Invitations client (portail) |
| 9 | `ClientService` | CRUD clients |
| 10 | `CollaboratorInvitationService` | Invitations collaborateurs |
| 11 | `DocumentService` | Upload, versioning, téléchargement, watermark |
| 12 | `DocumentShareService` | Partage de documents par rôle |
| 13 | `EIDASService` | Conformité eIDAS (niveaux de signature) |
| 14 | `EmailService` | Envoi d'emails (SMTP) |
| 15 | `EmailVerificationService` | Vérification d'email par code |
| 16 | `FirmService` | CRUD cabinets (multi-tenant) |
| 17 | `InvoiceService` | CRUD factures + calculs TVA |
| 18 | `LABFTService` | Contrôles anti-blanchiment ACPR/TRACFIN |
| 19 | `LogService` | Lecture des logs applicatifs |
| 20 | `MaintenanceService` | Mode maintenance (toggle) |
| 21 | `NotificationService` | Notifications in-app |
| 22 | `PasswordResetService` | Réinitialisation mot de passe |
| 23 | `PayPlugService` | Intégration PayPlug (legacy) |
| 24 | `PRAService` | Plan de Reprise d'Activité |
| 25 | `RefreshTokenService` | Gestion des refresh tokens JWT |
| 26 | `RPVAService` | Communications RPVA / e-Barreau |
| 27 | `SettingsService` | Paramètres utilisateur |
| 28 | `StripePaymentService` | Intégration Stripe (webhooks, checkout) |
| 29 | `StripeService` | API Stripe (sessions, subscriptions) |
| 30 | `UserService` | CRUD utilisateurs |
| 31 | `WatermarkService` | Watermark PDF (COPIE, CONFIDENTIEL) |
| 32 | `YousignService` | Intégration API Yousign (signatures) |

---

## 7. REPOSITORIES (18)

| # | Interface | Entité | Méthodes clés |
|---|-----------|--------|---------------|
| 1 | `UserRepository` | `User` | `findByEmail`, `findByResetToken`, `findByStripeCustomerId`, `existsByEmail`, `findByRole`, `findAllLawyers`, `findWithFilters` |
| 2 | `FirmRepository` | `Firm` | CRUD standard |
| 3 | `ClientRepository` | `Client` | `findByLawyerId`, `findByLawyerIdAndEmail`, `searchByLawyerAndNameOrEmail`, `countByLawyerId`, `findByInvitationId` |
| 4 | `CaseRepository` | `Case` | `findByLawyerId`, `findByClientId`, `findByLawyerIdAndStatus`, `searchByLawyerAndNameOrDescription` |
| 5 | `DocumentRepository` | `Document` | `findByCaseIdAndNotDeleted`, `findDeletedByCaseId`, `findLatestVersionsByCaseId`, `calculateTotalSizeByLawyer` |
| 6 | `DocumentShareRepository` | `DocumentShare` | CRUD partage documents |
| 7 | `InvoiceRepository` | `Invoice` | `findByClientId`, `findByInvoiceNumber`, `findByLawyerId`, `findByLawyerIdAndStatus`, `findOverdue` |
| 8 | `InvoiceItemRepository` | `InvoiceItem` | CRUD lignes de facture |
| 9 | `AppointmentRepository` | `Appointment` | CRUD rendez-vous |
| 10 | `SignatureRepository` | `Signature` | CRUD signatures |
| 11 | `PermissionRepository` | `Permission` | CRUD permissions |
| 12 | `NotificationRepository` | `Notification` | CRUD notifications |
| 13 | `PaymentRepository` | `Payment` | CRUD paiements |
| 14 | `AuditLogRepository` | `AuditLog` | CRUD audit logs |
| 15 | `RpvaCommunicationRepository` | `RpvaCommunication` | CRUD communications RPVA |
| 16 | `RefreshTokenRepository` | `RefreshToken` | CRUD refresh tokens |
| 17 | `CaseShareLinkRepository` | `CaseShareLink` | CRUD liens de partage |
| 18 | `LABFTCheckRepository` | `LABFTCheck` | CRUD contrôles LAB-FT |

---

## 8. CONFIGURATION SÉCURITÉ

### 8.1 `SecurityConfig.java` — Chaîne de filtres

**Authentification :**
- **Form login** : `/login` (username = email), redirection post-login basée sur le rôle
- **JWT API** : `JwtAuthenticationFilter` avant `UsernamePasswordAuthenticationFilter`
- **Session** : `SessionCreationPolicy.IF_REQUIRED`, session fixation `newSession`, max 1 session concurrente
- **Password encoder** : `BCryptPasswordEncoder` (strength 12)

**Firewall HTTP :**
- `StrictHttpFirewall` : bloque slashes encodés, pourcentages, backslashes, points dans les chemins

**CSRF :**
- Activé par défaut, **désactivé** pour : `/api/auth/**`, `/subscription/webhook`, `/payment/webhook`, `/api/webhooks/**`

**Headers de sécurité (ANSSI/OWASP) :**
- `Content-Security-Policy` : strict (nonces, Google Fonts, CDN Bootstrap/jQuery autorisés)
- `Strict-Transport-Security` : `max-age=63072000; includeSubDomains; preload`
- `X-Content-Type-Options` : `nosniff`
- `X-Frame-Options` : `DENY`
- `Referrer-Policy` : `strict-origin-when-cross-origin`
- `Cross-Origin-Opener-Policy` : `same-origin`
- `Cross-Origin-Resource-Policy` : `same-origin`
- `Permissions-Policy` : caméra, micro, géoloc désactivés

### 8.2 Autorisation par URL

| Pattern URL | Accès |
|-------------|-------|
| `/`, `/login`, `/register`, `/maintenance` | `permitAll` |
| `/subscription/pricing`, `/legal/**` | `permitAll` |
| `/api/auth/**`, `/forgot-password`, `/reset-password`, `/verify-email` | `permitAll` |
| `/css/**`, `/js/**`, `/images/**`, `/img/**`, `/favicon.*`, `/robots.txt`, `/sitemap.xml`, `/webjars/**` | `permitAll` |
| `/clients/accept-invitation`, `/collaborators/accept-invitation`, `/huissiers/accept-invitation` | `permitAll` |
| `/cases/shared`, `/payment/webhook`, `/api/webhooks/**` | `permitAll` |
| `/h2-console/**`, `/test/**`, `/api/debug-status` | `denyAll` |
| `/admin/**`, `/api/admin/**` | `ADMIN` |
| `GET /invoices/*`, `GET /invoices/*/pdf`, `/invoices/my-invoices` | `CLIENT, LAWYER, ADMIN` |
| `/invoices/**`, `/api/invoices/**` | `LAWYER, ADMIN` |
| `/documents/**`, `/api/documents/**` | `LAWYER, ADMIN` |
| `/dashboard`, `/clients/**`, `/cases/**`, `/signatures/**`, `/rpva/**` | `LAWYER, ADMIN, LAWYER_SECONDARY` |
| `/my-cases-huissier/**` | `HUISSIER` |
| `/my-cases/**`, `/my-documents/**`, `/my-appointments/**`, `/my-signatures` | `CLIENT, LAWYER, ADMIN` |
| Tout le reste | `authenticated` |

### 8.3 Services de sécurité complémentaires

| Service | Rôle |
|---------|------|
| `AccountLockoutService` | Verrouillage après 5 échecs de connexion (15 min) |
| `JwtService` | Génération/validation JWT (HMAC) |
| `JwtServiceRS256` | JWT avec clés RSA (RS256) |
| `JwtBlacklistService` | Révocation de tokens JWT |
| `UserDetailsServiceImpl` | Chargement `UserDetails` depuis la BDD |
| `UserPrincipal` | Implémentation `UserDetails` (Spring Security) |
| `SecurityAuditListener` | Journalisation des événements d'authentification |

---

## 9. FILTRES ET INTERCEPTEURS (7)

### 9.1 `MaintenanceFilter` (Order 1)
- **Package :** `com.gedavocat.config`
- **Extends :** `OncePerRequestFilter`
- **Rôle :** Redirige vers `/maintenance` si mode maintenance actif
- **Bypass :** assets statiques, `/login`, `/register`, `/subscription`, `/logout`, `/api/admin/maintenance`, et tout utilisateur authentifié

### 9.2 `JwtAuthenticationFilter`
- **Package :** `com.gedavocat.security`
- **Extends :** `OncePerRequestFilter`
- **Position :** Avant `UsernamePasswordAuthenticationFilter`
- **Rôle :** Extrait et valide le token JWT du header `Authorization: Bearer <token>`, authentifie l'utilisateur dans le `SecurityContext`
- **Vérifie aussi :** compte actif (`userDetails.isEnabled()`)

### 9.3 `MultiTenantFilter`
- **Package :** `com.gedavocat.security`
- **Extends :** `OncePerRequestFilter`
- **Rôle :** Active le filtre Hibernate `firmFilter` avec le `firmId` de l'utilisateur authentifié
- **Logique :**
  - ADMIN → pas de filtre (accès cross-tenant)
  - Utilisateur sans cabinet → 403 Forbidden
  - Erreur d'activation → 500 (fail-closed, pas de fuite inter-tenant)

### 9.4 `RateLimitingFilter`
- **Package :** `com.gedavocat.security`
- **Extends :** `OncePerRequestFilter`
- **Rôle :** Limite à 10 requêtes POST par minute par IP
- **Endpoints protégés :** `/login`, `/register`, `/api/auth/`, `/forgot-password`, `/reset-password`, `/verify-email`, `/collaborators/accept-invitation`, `/huissiers/accept-invitation`, `/subscription/webhook`, `/payment/webhook`, `/api/webhooks/`
- **Réponse :** HTTP 429 si dépassement

### 9.5 `SubscriptionEnforcementFilter`
- **Package :** `com.gedavocat.security`
- **Extends :** `OncePerRequestFilter`
- **Position :** Après `JwtAuthenticationFilter`
- **Rôle :** Vérifie l'abonnement actif pour les LAWYER uniquement
- **Logique :**
  - Abonnement actif → accès complet
  - Abonnement expiré (plan existant) → **lecture seule** (GET autorisé, POST/PUT/DELETE bloqués, téléchargements autorisés)
  - Jamais d'abonnement → redirection `/subscription/pricing`
  - ADMIN, CLIENT, HUISSIER, LAWYER_SECONDARY → pas de paywall
- **Routes payantes :** `/dashboard`, `/clients`, `/cases`, `/documents`, `/signatures`, `/rpva`, `/invoices`, `/permissions` et leurs `/api/` correspondants
- **Fail-closed :** utilisateur introuvable en BDD → accès refusé

### 9.6 `ErrorStatusInterceptor`
- **Package :** `com.gedavocat.config`
- **Implements :** `HandlerInterceptor`
- **Enregistrement :** `WebMvcConfig.addInterceptors()` sur `/**`
- **Rôle :** Corrige le code HTTP des pages d'erreur (vue nommée "error") pour qu'il corresponde au statut réel (évite les 200 sur des pages d'erreur)

### 9.7 `SecurityAuditListener`
- **Package :** `com.gedavocat.security`
- **Type :** `@EventListener` (Spring Security events)
- **Rôle :** Journalise toutes les tentatives de connexion (succès et échecs) avec l'IP — conformité ANSSI RGS / OWASP ASVS 2.2

---

## 10. RÔLES ET PERMISSIONS

### 10.1 Les 5 rôles (`User.UserRole`)

| Rôle | Display Name | Description |
|------|-------------|-------------|
| `ADMIN` | Administrateur | Super-admin système, accès cross-tenant, pas de filtre `firmId` |
| `LAWYER` | Avocat | Avocat titulaire du cabinet, soumis au paywall (abonnement), propriétaire des données |
| `CLIENT` | Client | Client du cabinet, accès portail client (`/my-cases`), limité à ses dossiers |
| `LAWYER_SECONDARY` | Collaborateur | Avocat collaborateur, accès via permissions sur des dossiers spécifiques, pas de paywall propre |
| `HUISSIER` | Huissier | Huissier de justice, accès portail dédié (`/my-cases-huissier`), dossiers partagés uniquement |

### 10.2 Matrice des accès par fonctionnalité

| Fonctionnalité | ADMIN | LAWYER | CLIENT | LAWYER_SECONDARY | HUISSIER |
|----------------|-------|--------|--------|-------------------|----------|
| Dashboard admin | ✅ | ❌ | ❌ | ❌ | ❌ |
| Gestion utilisateurs | ✅ | ❌ | ❌ | ❌ | ❌ |
| Monitoring sécurité | ✅ | ❌ | ❌ | ❌ | ❌ |
| Audit sécurité | ✅ (+ DPO) | ❌ | ❌ | ❌ | ❌ |
| Conformité | ✅ (+ CO) | ❌ | ❌ | ❌ | ❌ |
| Dossiers (CRUD) | ✅ | ✅ | ❌ | ✅ (si permission) | ❌ |
| Clients (CRUD) | ✅ | ✅ | ❌ | ✅ | ❌ |
| Documents (CRUD) | ✅ | ✅ | Upload seul | ✅ (upload, pas DL/preview) | ❌ |
| Factures (CRUD) | ✅ | ✅ | Lecture + PDF | ❌ | ❌ |
| Signatures | ✅ | ✅ | Lecture | ❌ | ❌ |
| RPVA | ✅ | ✅ | ❌ | ❌ | ❌ |
| Rendez-vous (gestion) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Rendez-vous (client) | ❌ | ❌ | ✅ | ❌ | ❌ |
| Portail client | ❌ | ❌ | ✅ | ❌ | ❌ |
| Portail collaborateur | ❌ | ❌ | ❌ | ✅ | ❌ |
| Portail huissier | ❌ | ❌ | ❌ | ❌ | ✅ |
| Paramètres | ✅ | ✅ | ❌ | ❌ | ❌ |
| Notifications | ✅ | ✅ | ✅ | ✅ | ✅ |
| RGPD (export/delete) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Abonnement | — | ✅ (paywall) | — | — | — |

### 10.3 Rôles supplémentaires (non persistés, vérifiés par `@PreAuthorize`)

| Rôle | Utilisé dans |
|------|-------------|
| `COMPLIANCE_OFFICER` | `ComplianceController` — accès aux scores de conformité ACPR/RGPD/ISO/eIDAS |
| `DPO` | `SecurityAuditController`, `SecurityAdminController` — audits de sécurité et gestion MFA |

> **Note :** Ces rôles n'apparaissent pas dans l'enum `UserRole` mais sont référencés dans les annotations `@PreAuthorize`. Ils seraient des rôles attribuables via une extension future ou des autorités personnalisées.

### 10.4 Permissions granulaires (`Permission` entity)

Les collaborateurs (`LAWYER_SECONDARY`) accèdent aux dossiers via des `Permission` avec des droits granulaires :

| Droit | Description |
|-------|-------------|
| `canRead` | Lecture du dossier et de ses documents |
| `canWrite` | Modification du dossier |
| `canUpload` | Upload de documents dans le dossier |

Les permissions peuvent :
- Être **révoquées** (`revokedAt` non null)
- Avoir une **date d'expiration** (`expiresAt`)
- Être vérifiées via `isValid()` (active + non expirée + non révoquée)

---

## RÉSUMÉ CHIFFRÉ

| Catégorie | Nombre |
|-----------|--------|
| Contrôleurs | 37 |
| Routes HTTP distinctes | ~150 |
| Templates Thymeleaf | 81 |
| Assets statiques | 32 |
| Entités JPA | 18 |
| Services | 32 |
| Repositories | 18 |
| Filtres / Intercepteurs | 7 |
| Rôles utilisateur | 5 (+2 non persistés) |
| Enums métier | ~25 |
