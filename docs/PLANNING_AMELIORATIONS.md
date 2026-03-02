# 📅 PLANNING D'AMÉLIORATION - DocAvocat

**Application GED Avocat (DocAvocat)**  
**Version 1.0.0**  
**Date : 1er mars 2026**

---

## 📊 RÉSUMÉ EXÉCUTIF

Ce document présente le **planning détaillé** pour appliquer les améliorations identifiées dans les 4 phases d'audit, **sans risque de régression** pour l'application en production.

**Durée totale recommandée : 4 semaines**
- ✅ Semaine 1 : Priorité HAUTE + MOYENNE → **Mise en Production**
- ✅ Semaines 2-4 : Priorité BASSE (améliorations continues)

**Coût estimé : 7 200€** (1 développeur senior solo)  
**ROI attendu : 60 000€/an** (80h économisées/an × 600€/jour)

---

## ⏱️ ESTIMATION TEMPORELLE PAR PRIORITÉ

### 🔴 PRIORITÉ HAUTE (Critique - Avant MEP)

| # | Action | Temps | Risque | Compétence | Référence |
|---|--------|-------|--------|------------|-----------|
| 1 | **Supprimer dump SQL de Git** | 30 min | 🟢 Faible | Junior | Phase 4 §2.3 |
| 2 | **Update Spring Boot 3.2.2 → 3.2.4** | 2-3h | 🟡 Moyen | Intermédiaire | Phase 4 §3.4 |
| 3 | **Supprimer dépendances inutiles** | 1-2h | 🟢 Faible | Intermédiaire | Phase 4 §3.3 |
| - | **Tests de régression complets** | 2-3h | - | - | - |

**⏰ Total Priorité HAUTE : 1 journée (6-8h)**

**Justification :**
- **Action 1** : Violation RGPD potentielle (données personnelles dans Git)
- **Action 2** : Correctif sécurité CVE-2024-22243
- **Action 3** : Réduction surface d'attaque (-2 dépendances)

---

### 🟡 PRIORITÉ MOYENNE (Semaine 1)

| # | Action | Temps | Risque | Compétence | Référence |
|---|--------|-------|--------|------------|-----------|
| 4 | **Réorganiser docs Markdown** | 1h | 🟢 Nul | Junior | Phase 4 §2.2 |
| 5 | **Créer README.md professionnel** | 2h | 🟢 Nul | Intermédiaire | Phase 4 §6.1 |
| 6 | **Désactiver console H2 par défaut** | 15 min | 🟢 Nul | Junior | Phase 4 §2.7 |
| 7 | **Configurer clés Stripe/Yousign prod** | 1h | 🟡 Moyen | Senior | Phase 3 §5.2 |
| - | **Tests fonctionnels (paiement, signature)** | 2h | - | - | - |

**⏰ Total Priorité MOYENNE : 1 journée (6h)**

**Justification :**
- **Action 4-5** : Professionnalisme GitHub, onboarding facilité
- **Action 6** : Sécurité défensive (console DB non exposée)
- **Action 7** : Passage en mode production (billing réel)

---

### 🟢 PRIORITÉ BASSE (Semaines 2-4)

| # | Action | Temps | Risque | Compétence | Référence |
|---|--------|-------|--------|------------|-----------|
| 8 | **Améliorer couverture tests (40% → 60%)** | 3-5 jours | 🟢 Nul | Intermédiaire | Phase 4 §10.3 |
| 9 | **Activer cache Hibernate (second-level)** | 1-2 jours | 🟡 Moyen | Senior | Phase 3 §7.2 |
| 10 | **Nginx compression + cache headers** | 0.5 jour | 🟢 Faible | Intermédiaire | Phase 4 §7.2 |
| 11 | **Refactoring SRP (AdminController)** | 1-2 jours | 🟡 Moyen | Senior | Phase 4 §5.3 |
| 12 | **Validation upload avancée (magic bytes)** | 1 jour | 🟢 Faible | Intermédiaire | Phase 3 §6.2 |
| - | **Tests E2E régression** | 1 jour | - | - | - |

**⏰ Total Priorité BASSE : 8-12 jours**

**Justification :**
- **Action 8** : Qualité code, détection bugs précoce
- **Action 9** : Performance API (-60% requêtes SQL)
- **Action 10** : Performance frontend (-70% bande passante)
- **Action 11** : Maintenabilité, respect SOLID
- **Action 12** : Sécurité upload (détection spoofing MIME)

---

## 📅 PLANNING DÉTAILLÉ (4 SEMAINES)

### 📆 SEMAINE 1 : MISE EN PRODUCTION

#### 🗓️ JOUR 1 : Priorité HAUTE

**Objectif :** Appliquer corrections critiques sécurité/RGPD

```
08h00-09h00 : Préparation et backups
├─ Git tag v1.0.0-pre-ameliorations
├─ Backup MySQL : mysqldump > backup_20260301.sql
└─ Documentation état initial

09h00-10h00 : Suppression dump SQL du Git
├─ Vérification présence dans historique Git
├─ git filter-branch (suppression historique)
├─ git push --force (coordination équipe)
└─ Mise à jour .gitignore

10h00-12h00 : Update Spring Boot 3.2.4
├─ Lecture Migration Guide Spring Boot
├─ Modification pom.xml (3.2.2 → 3.2.4)
├─ mvn clean package
├─ Résolution conflits dépendances
└─ Tests unitaires (mvn test)

14h00-15h00 : Suppression dépendances inutiles
├─ Suppression openpdf du pom.xml
├─ Suppression commons-fileupload du pom.xml
├─ mvn dependency:analyze
└─ Build final (mvn clean package)

15h00-17h00 : Tests de régression complets
├─ Tests unitaires : mvn test
├─ Tests E2E Playwright (40 scénarios)
├─ Tests manuels : login, upload, signature
└─ Vérification logs (aucune erreur)

17h00-18h00 : Validation et documentation
├─ Healthcheck /actuator/health → UP
├─ Git commit + tag v1.0.0-haute-prio-done
├─ Documentation changements
└─ Réunion équipe (bilan jour 1)
```

**Livrables :**
- ✅ Application sans dump SQL dans Git (RGPD conforme)
- ✅ Spring Boot 3.2.4 (CVE corrigée)
- ✅ Dépendances nettoyées (-2 libs)
- ✅ Tests 100% OK

---

#### 🗓️ JOUR 2 : Priorité MOYENNE (Documentation)

**Objectif :** Professionnaliser le repository GitHub

```
08h00-09h00 : Réorganisation documentation
├─ mkdir docs/historical-sessions
├─ mv AMELIORATIONS_*.md docs/historical-sessions/
├─ mv CALENDAR_*.md COOKIE_*.md docs/historical-sessions/
├─ mv GIT_*.md MOBILE_*.md SESSION_*.md docs/historical-sessions/
└─ Création docs/historical-sessions/README.md

09h00-11h00 : Création README.md professionnel
├─ Badges (License, Spring Boot, Java, Security)
├─ Section Fonctionnalités
├─ Section Installation
├─ Section Documentation (liens Phase 1-4)
├─ Section Sécurité (niveau bancaire)
└─ Section Support

11h00-11h15 : Désactivation console H2
├─ Modification application-h2.properties
└─ spring.h2.console.enabled=false

11h15-12h00 : Tests et validation
├─ Vérification repository GitHub
├─ Tests démarrage profils (dev, h2, prod)
└─ Validation documentation

14h00-16h00 : Documentation technique supplémentaire
├─ Création docs/DEPLOYMENT.md
├─ Création docs/DATABASE_SCRIPTS.md
├─ Documentation variables d'environnement
└─ Guide troubleshooting

16h00-17h00 : Revue et finalisation
├─ Relecture README.md
├─ Vérification liens internes
├─ Git commit + tag v1.0.0-docs-done
└─ Bilan documentation
```

**Livrables :**
- ✅ README.md professionnel
- ✅ Documentation réorganisée
- ✅ Console H2 sécurisée
- ✅ Guides déploiement et troubleshooting

---

#### 🗓️ JOUR 3 : Priorité MOYENNE (Configuration Production)

**Objectif :** Configurer services externes production

```
08h00-09h00 : Récupération clés Stripe production
├─ Connexion Stripe Dashboard
├─ Récupération sk_live_XXXXX
├─ Récupération pk_live_XXXXX
├─ Configuration webhook production
└─ Récupération whsec_XXXXX

09h00-10h00 : Configuration Yousign production
├─ Connexion Yousign Dashboard
├─ Récupération API Key production
├─ Vérification quotas
└─ Test API sandbox → production

10h00-11h00 : Configuration .env.prod
├─ Mise à jour STRIPE_SECRET_KEY
├─ Mise à jour STRIPE_PUBLISHABLE_KEY
├─ Mise à jour STRIPE_WEBHOOK_SECRET
├─ Mise à jour YOUSIGN_API_KEY
└─ Vérification JWT_SECRET (rotation si nécessaire)

11h00-12h00 : Première vérification
├─ Tests API Stripe (mode test)
├─ Tests API Yousign (mode sandbox)
└─ Vérification logs (aucune erreur)

14h00-16h00 : Tests paiement Stripe production
├─ Création session checkout test
├─ Paiement avec carte test Stripe
├─ Vérification webhook reçu
├─ Vérification abonnement activé
└─ Test annulation abonnement

16h00-17h00 : Tests signature Yousign
├─ Création demande signature test
├─ Envoi email signature
├─ Signature test (sandbox)
└─ Vérification callback

17h00-18h00 : Validation finale et documentation
├─ Tests E2E paiement + signature
├─ Documentation configuration production
├─ Git commit + tag v1.0.0-prod-config-done
└─ Bilan configuration
```

**Livrables :**
- ✅ Stripe production configuré et testé
- ✅ Yousign production configuré et testé
- ✅ Webhooks fonctionnels
- ✅ Documentation configuration

---

#### 🗓️ JOUR 4 : Tests et Validation Finale

**Objectif :** Valider l'ensemble des fonctionnalités

```
08h00-10h00 : Tests E2E complets (40 scénarios)
├─ Playwright : npx playwright test
├─ Scénario login (avocat, client, admin)
├─ Scénario création dossier
├─ Scénario upload document
├─ Scénario partage document
├─ Scénario signature électronique
├─ Scénario paiement Stripe
├─ Scénario gestion clients
└─ Vérification 40/40 OK

10h00-12h00 : Tests manuels critiques
├─ Parcours avocat complet (création dossier → facturation)
├─ Parcours client (consultation documents, signature)
├─ Parcours admin (statistiques, gestion users)
├─ Test upload 50 MB (limite)
├─ Test génération facture PDF
└─ Test emails (SMTP production)

14h00-15h00 : Revue sécurité
├─ Test headers sécurité : curl -I https://docavocat.fr
├─ Test SSL Labs : ssllabs.com/ssltest
├─ Test securityheaders.com → Objectif A+
├─ Vérification HSTS, CSP, X-Frame-Options
└─ Test OWASP ZAP (scan automatique)

15h00-16h30 : Tests de charge
├─ Préparation JMeter/k6
├─ Simulation 50 utilisateurs simultanés
├─ Simulation 100 utilisateurs simultanés
├─ Vérification temps réponse < 500ms
├─ Vérification mémoire JVM stable
└─ Vérification logs (aucune erreur)

16h30-17h30 : Analyse performance
├─ Vérification métriques Grafana
├─ Analyse logs Loki
├─ Vérification healthcheck Docker
├─ Monitoring CPU/RAM
└─ Vérification MySQL queries

17h30-18h00 : Bilan et go/no-go MEP
├─ Checklist mise en production (60 points)
├─ Décision go/no-go
├─ Documentation tests
└─ Git tag v1.0.0-pre-mep
```

**Livrables :**
- ✅ Tests E2E 40/40 OK
- ✅ Tests manuels OK
- ✅ Sécurité A+ (headers, SSL)
- ✅ Performance validée (100 users)
- ✅ Go/No-Go MEP

---

#### 🗓️ JOUR 5 : Déploiement Production

**Objectif :** Mise en production sécurisée

```
08h00-09h00 : Préparation déploiement
├─ Backup final base de données
├─ Build image Docker finale
├─ Push GitLab Container Registry
├─ Vérification image pullable
└─ Communication équipe (MEP en cours)

09h00-10h00 : Déploiement production
├─ Connexion serveur Hetzner (SSH)
├─ cd /opt/gedavocat
├─ docker compose pull app
├─ docker compose up -d --no-deps app
├─ Vérification démarrage : docker logs -f docavocat-app
└─ Attente healthcheck healthy

10h00-11h00 : Vérification post-déploiement
├─ curl https://docavocat.fr/actuator/health
├─ Test login avocat
├─ Test création dossier
├─ Test upload document
├─ Test paiement Stripe (mode live)
└─ Vérification emails SMTP

11h00-12h00 : Monitoring initial
├─ Grafana : vérification métriques
├─ Loki : vérification logs (aucune erreur)
├─ Uptime Robot : activation alerte
├─ MySQL : vérification connexions
└─ Disk usage : vérification espace

14h00-16h00 : Tests utilisateurs réels
├─ Invitation 5 utilisateurs bêta
├─ Suivi parcours utilisateur
├─ Collecte feedback
├─ Correction bugs mineurs (si nécessaire)
└─ Monitoring continu

16h00-17h00 : Optimisations mineures
├─ Ajustements config Nginx (si besoin)
├─ Ajustements JVM (si besoin)
├─ Ajustements alertes Grafana
└─ Documentation incidents

17h00-18h00 : Bilan MEP et communication
├─ Réunion équipe (bilan MEP)
├─ Documentation déploiement
├─ Git tag v1.0.0-production
├─ Communication clients (MEP réussie)
└─ Planning Semaine 2-4 (Priorité BASSE)
```

**Livrables :**
- ✅ Application en production
- ✅ Monitoring actif 24/7
- ✅ Users beta OK
- ✅ Documentation déploiement
- ✅ MEP réussie

---

### 📆 SEMAINE 2 : Optimisations Performance

#### 🗓️ Lundi-Mardi : Nginx Compression + Cache Headers

**Objectif :** Améliorer performance frontend

```
Lundi :
08h-10h : Configuration Nginx compression
├─ Activation gzip (niveau 6)
├─ Activation brotli
├─ Configuration types MIME
└─ Redémarrage nginx

10h-12h : Configuration cache headers
├─ Cache static assets (1 an)
├─ Cache CSS/JS (1 an)
├─ Cache images (1 an)
├─ Cache WebJars (1 an)
└─ Immutable directive

14h-16h : Tests performance
├─ Lighthouse : score avant/après
├─ GTmetrix : analyse performance
├─ Mesure bande passante (curl)
├─ Vérification compression (gzip/brotli)
└─ Tests navigateur (cache)

16h-17h : Validation et monitoring
├─ Vérification Grafana (bande passante -70%)
├─ Tests utilisateurs (chargement plus rapide)
└─ Documentation configuration

Mardi :
08h-12h : Fine-tuning et optimisations
├─ Ajustements compression (types additionnels)
├─ Optimisation buffer Nginx
├─ Configuration proxy cache (optionnel)
└─ Tests charge (vérification perf)

14h-17h : Tests régression
├─ Tests E2E (vérification aucune casse)
├─ Tests manuels (upload, download)
└─ Git commit + documentation
```

**Gains Attendus :**
- ⚡ Bande passante : -70%
- ⚡ Vitesse chargement : 2-3x plus rapide
- ⚡ Score Lighthouse : 90+ → 95+

---

#### 🗓️ Mercredi-Jeudi : Tests Unitaires (Couverture +20%)

**Objectif :** Améliorer qualité code

```
Mercredi :
08h-10h : Analyse couverture actuelle
├─ mvn test jacoco:report
├─ Analyse target/site/jacoco/index.html
├─ Identification classes non testées
└─ Priorisation classes critiques

10h-12h : Tests Services (DocumentService)
├─ DocumentServiceTest.java
├─ Tests upload, download, delete
├─ Tests versioning, soft delete
├─ Tests validation multi-niveaux
└─ mvn test (vérification)

14h-17h : Tests Services (CaseService, InvoiceService)
├─ CaseServiceTest.java (CRUD dossiers)
├─ InvoiceServiceTest.java (génération factures)
├─ Tests isolation multi-tenant
└─ mvn test (vérification)

Jeudi :
08h-12h : Tests Controllers et Security
├─ DocumentControllerTest.java
├─ CaseControllerTest.java
├─ Tests authentification JWT
├─ Tests autorisation rôles
└─ mvn test (vérification)

14h-16h : Analyse finale
├─ mvn test jacoco:report
├─ Vérification couverture 60%+
└─ Identification gaps restants

16h-17h : Git commit + documentation
```

**Gains Attendus :**
- ✅ Couverture : 40% → 60%
- ✅ Bugs détectés : +50%
- ✅ Confiance refactoring : +80%

---

#### 🗓️ Vendredi : Validation Performance

**Objectif :** Mesurer gains performance

```
08h-10h : Benchmarks avant/après
├─ Temps réponse API (avant/après cache)
├─ Temps chargement pages (avant/après compression)
├─ Bande passante consommée
└─ Documentation résultats

10h-12h : Tests charge
├─ JMeter : 100 users simultanés
├─ Vérification mémoire stable
├─ Vérification temps réponse < 500ms
└─ Analyse Grafana

14h-16h : Optimisations additionnelles
├─ Ajustements JVM (si nécessaire)
├─ Ajustements pool connexions MySQL
└─ Tests finaux

16h-17h : Bilan semaine 2
├─ Documentation gains performance
├─ Réunion équipe
└─ Planning semaine 3
```

---

### 📆 SEMAINE 3 : Cache Hibernate Second-Level

#### 🗓️ Lundi-Mercredi : Implémentation Cache

**Objectif :** Réduire requêtes SQL de 60%

```
Lundi :
08h-10h : Ajout dépendances
├─ hibernate-jcache dans pom.xml
├─ ehcache dans pom.xml
├─ mvn clean package
└─ Vérification build OK

10h-12h : Configuration Spring
├─ application-prod.properties (cache properties)
├─ Création ehcache.xml
├─ Configuration cache regions
└─ Tests démarrage

14h-17h : Annotation entités
├─ @Cacheable sur User
├─ @Cacheable sur Client
├─ @Cacheable sur Case
├─ @Cache(usage=READ_WRITE) sur entités
└─ Tests unitaires

Mardi :
08h-12h : Tests et validation
├─ Tests requêtes SQL (avant/après)
├─ Vérification cache hit/miss
├─ Tests invalidation cache
└─ Monitoring cache stats

14h-17h : Fine-tuning
├─ Ajustements TTL cache
├─ Ajustements taille cache
├─ Tests charge (vérification perf)
└─ Documentation configuration

Mercredi :
08h-12h : Tests régression
├─ Tests E2E complets (40 scénarios)
├─ Tests création/modification/suppression
├─ Vérification cohérence données
└─ Tests isolation multi-tenant

14h-17h : Monitoring et optimisations
├─ Grafana : vérification requêtes SQL -60%
├─ Ajustements cache eviction
├─ Git commit + documentation
└─ Bilan cache Hibernate
```

**Gains Attendus :**
- ⚡ Requêtes SQL : -60%
- ⚡ Temps réponse API : 3-5x plus rapide
- ⚡ Charge MySQL : -50%

---

#### 🗓️ Jeudi-Vendredi : Tests Performance

**Objectif :** Valider gains performance

```
Jeudi :
08h-12h : Benchmarks avant/après
├─ Mesure requêtes SQL (logs Hibernate)
├─ Mesure temps réponse API
├─ Tests charge 100 users
└─ Analyse Grafana/Loki

14h-17h : Tests stabilité
├─ Tests longue durée (4h)
├─ Vérification mémoire cache
├─ Vérification OOM (OutOfMemory)
└─ Ajustements si nécessaire

Vendredi :
08h-12h : Validation finale
├─ Tests E2E (vérification aucune régression)
├─ Tests manuels (vérification UX)
├─ Documentation gains performance
└─ Git commit + tag v1.1.0-cache

14h-16h : Bilan semaine 3
├─ Présentation gains équipe
├─ Documentation technique
└─ Planning semaine 4

16h-17h : Préparation semaine 4
```

---

### 📆 SEMAINE 4 : Refactoring et Validation Upload

#### 🗓️ Lundi-Mardi : Refactoring AdminController (SRP)

**Objectif :** Respect principe Single Responsibility

```
Lundi :
08h-10h : Analyse et design
├─ Analyse AdminController actuel
├─ Design nouveaux controllers
├─ AdminDashboardController
├─ AdminUserController
├─ AdminStatisticsController
├─ AdminLogsController
└─ AdminSettingsController

10h-12h : Création nouveaux controllers
├─ Création AdminDashboardController.java
├─ Extraction méthode dashboard()
├─ Création AdminUserController.java
├─ Extraction méthodes users()
└─ Tests compilation

14h-17h : Suite refactoring
├─ AdminStatisticsController.java
├─ AdminLogsController.java
├─ AdminSettingsController.java
├─ Mise à jour routes
└─ Tests unitaires

Mardi :
08h-12h : Mise à jour templates
├─ Mise à jour liens navigation
├─ Mise à jour formulaires
├─ Tests manuels parcours admin
└─ Ajustements CSS (si nécessaire)

14h-17h : Tests et validation
├─ Tests E2E admin (tous scénarios)
├─ Tests unitaires nouveaux controllers
├─ Vérification aucune régression
└─ Git commit + documentation
```

**Gains Attendus :**
- ✅ Maintenabilité : +40%
- ✅ Testabilité : +60%
- ✅ Respect SOLID (SRP)

---

#### 🗓️ Mercredi : Validation Upload Avancée (Magic Bytes)

**Objectif :** Sécurité upload renforcée

```
08h-10h : Implémentation FileValidationService
├─ Création FileValidationService.java
├─ Validation extension
├─ Validation MIME type
├─ Validation magic bytes (signatures binaires)
└─ Tests unitaires

10h-12h : Intégration dans controllers
├─ Injection service dans DocumentController
├─ Injection service dans ClientPortalController
├─ Remplacement validation actuelle
└─ Tests compilation

14h-16h : Tests sécurité upload
├─ Tests upload fichiers valides (PDF, DOCX)
├─ Tests upload fichiers spoofés (exe → pdf)
├─ Tests upload fichiers malveillants
├─ Vérification rejection fichiers invalides
└─ Documentation tests

16h-17h : Git commit + documentation
```

**Gains Attendus :**
- 🔒 Sécurité : Détection spoofing MIME
- 🔒 Protection : Anti-upload malveillant

---

#### 🗓️ Jeudi : Tests E2E Régression Complets

**Objectif :** Validation finale tous changements

```
08h-10h : Tests E2E automatisés
├─ npx playwright test (40 scénarios)
├─ Vérification 40/40 OK
└─ Analyse failures (si présents)

10h-12h : Tests manuels complets
├─ Parcours avocat (création dossier → facturation)
├─ Parcours client (consultation → signature)
├─ Parcours admin (tous nouveaux controllers)
├─ Tests upload (validation avancée)
└─ Tests paiement + signature

14h-16h : Tests non-fonctionnels
├─ Tests performance (benchmarks)
├─ Tests sécurité (OWASP ZAP)
├─ Tests charge (100 users)
└─ Tests stabilité (2h)

16h-17h : Analyse et documentation
├─ Documentation tests
├─ Analyse résultats
└─ Identification bugs mineurs
```

---

#### 🗓️ Vendredi : Bilan Final et Déploiement

**Objectif :** Déploiement version optimisée

```
08h-10h : Corrections bugs mineurs
├─ Correction issues identifiés jeudi
├─ Tests régression
└─ Validation finale

10h-11h : Préparation déploiement
├─ Git tag v1.1.0
├─ Build image Docker finale
├─ Push GitLab Container Registry
└─ Documentation release notes

11h-12h : Déploiement production
├─ Connexion serveur
├─ docker compose pull && up -d
├─ Vérification démarrage
└─ Tests post-déploiement

14h-16h : Monitoring post-MEP
├─ Grafana : vérification métriques
├─ Loki : vérification logs
├─ Tests utilisateurs real
└─ Collecte feedback

16h-17h30 : Bilan final 4 semaines
├─ Présentation gains équipe
├─ Documentation complète
├─ Rétrospective
└─ Planification maintenance

17h30-18h00 : Célébration 🎉
```

**Livrables Finaux :**
- ✅ Version 1.1.0 déployée
- ✅ Toutes améliorations appliquées
- ✅ Performance optimisée
- ✅ Sécurité renforcée
- ✅ Maintenabilité améliorée

---

## ⚠️ GESTION DES RISQUES

### Risques Identifiés et Mitigation

| Risque | Probabilité | Impact | Mitigation | Rollback |
|--------|-------------|--------|------------|----------|
| **Régression Spring Boot update** | 20% | 🟡 Moyen | Tests E2E complets avant MEP | Git tag + restore |
| **Problème Stripe production** | 15% | 🔴 Élevé | Mode test d'abord, validation webhook | Retour clés test |
| **Cache Hibernate bugs** | 30% | 🟡 Moyen | Tests invalidation, monitoring cache stats | Désactivation cache |
| **Refactoring casse routes** | 10% | 🟡 Moyen | Tests E2E + revue code | Git revert |
| **Upload validation trop stricte** | 25% | 🟢 Faible | Tests fichiers réels clients | Assouplir règles |
| **Performance dégradée (cache)** | 15% | 🟡 Moyen | Benchmarks avant/après, monitoring | Rollback config |

### Stratégie de Rollback

#### Rollback Application Complète

```bash
# 1. Backup de sécurité
git tag v1.1.0-backup-$(date +%Y%m%d-%H%M)
docker tag docavocat-app:latest docavocat-app:backup-$(date +%Y%m%d)

# 2. Retour version précédente
git checkout v1.0.0-production
docker compose down
docker tag docavocat-app:backup-20260301 docavocat-app:latest
docker compose up -d

# 3. Vérification
docker logs -f docavocat-app
curl https://docavocat.fr/actuator/health

# 4. Restauration base de données (si nécessaire)
mysql -u root -p gedavocat < backup_pre_update.sql
```

#### Rollback Partiel (Configuration)

```bash
# Rollback cache Hibernate
git checkout HEAD~1 -- src/main/resources/application-prod.properties
docker compose restart app

# Rollback Nginx
git checkout HEAD~1 -- /etc/nginx/sites-available/docavocat.conf
sudo systemctl restart nginx
```

### Procédure d'Urgence

**Contact Équipe :**
- 📞 Dev Senior : +33 6 XX XX XX XX
- 📧 Email : emergency@docavocat.fr
- 💬 Slack : #incidents-production

**Actions Immédiates :**

1. **Incident Critique (Down complet)**
   - Rollback immédiat version précédente
   - Communication clients (status page)
   - Post-mortem sous 24h

2. **Bug Non-Bloquant**
   - Hotfix dans les 4h
   - Tests régression
   - Déploiement patch

3. **Performance Dégradée**
   - Monitoring renforcé
   - Ajustements config (JVM, cache)
   - Si échec : rollback partiel

---

## 👥 RESSOURCES HUMAINES

### Option 1 : Solo (Recommandée)

**1 Développeur Senior Full-Stack**

- **Durée** : 4 semaines @ 80% = 16 jours/homme
- **Profil** :
  - Expérience Spring Boot 3.x (5+ ans)
  - Maîtrise Docker, Nginx
  - Connaissance Hibernate, JPA
  - Compétences sécurité (OWASP)
- **Avantages** :
  - Cohérence technique
  - Connaît le projet de bout en bout
  - Prise de décision rapide
- **Inconvénients** :
  - Bus factor = 1
  - Pas de revue de code peer

**Coût : 16j × 600€/j = 9 600€**

---

### Option 2 : Équipe (Plus Rapide)

**Équipe de 4 Personnes**

| Rôle | Durée | TJM | Responsabilités |
|------|-------|-----|-----------------|
| **Dev Senior Full-Stack** | 10j @ 100% | 600€ | Update Spring Boot, Cache Hibernate, Refactoring |
| **Dev Intermédiaire** | 5j @ 100% | 450€ | Tests unitaires, Validation upload, Documentation |
| **DevOps** | 3j @ 100% | 550€ | Nginx, Docker, Monitoring, Déploiement |
| **QA** | 5j @ 100% | 400€ | Tests E2E, Validation sécurité, Tests charge |

**Avantages :**
- Parallélisation des tâches
- Expertise spécialisée
- Revue de code croisée
- Finition en 2 semaines (vs 4)

**Inconvénients :**
- Coordination équipe
- Coût plus élevé
- Communication overhead

**Coût Total : 11 900€**

---

### Option 3 : Minimal (Risquée)

**1 Développeur Junior + 1 Senior (Support)**

- **Junior** : 15j @ 100% (tests, docs, refactoring simple)
- **Senior** : 5j @ 50% (review, complexités)
- **Coût** : (15j × 350€) + (2.5j × 600€) = 6 750€

**⚠️ Risques :**
- Junior peut bloquer sur complexités
- Qualité code variable
- Tests insuffisants

**👎 Non recommandé pour production SaaS juridique**

---

## 💰 ANALYSE FINANCIÈRE

### Coûts Détaillés (Option Solo Recommandée)

| Phase | Durée | TJM | Coût |
|-------|-------|-----|------|
| **Semaine 1 : HAUTE+MOYENNE (MEP)** | 5j | 600€ | 3 000€ |
| **Semaine 2 : Performance** | 4j | 600€ | 2 400€ |
| **Semaine 3 : Cache Hibernate** | 4j | 600€ | 2 400€ |
| **Semaine 4 : Refactoring + Validation** | 3j | 600€ | 1 800€ |
| **TOTAL** | **16j** | **600€** | **9 600€** |

### ROI (Retour sur Investissement)

#### Gains Directs Annuels

| Gain | Calcul | Montant/an |
|------|--------|------------|
| **Maintenance facilitée** | 80h × 600€/j ÷ 7h | 6 857€ |
| **Onboarding accéléré** | 2 devs × 10h × 600€/j ÷ 7h | 1 714€ |
| **Bugs évités** | 5 bugs × 4h × 600€/j ÷ 7h | 1 714€ |
| **Performance serveur** | -20% CPU/RAM × 50€/mois | 600€ |
| **Bande passante** | -70% × 100€/mois | 840€ |
| **TOTAL GAINS** | | **11 725€/an** |

#### ROI sur 3 ans

```
Investissement initial : 9 600€
Gains annuels : 11 725€

ROI Année 1 : +2 125€ (+22%)
ROI Année 2 : +11 725€ (+122%)
ROI Année 3 : +11 725€ (+122%)

ROI 3 ans : +25 575€ (+266%)
```

### Gains Indirects (Non Chiffrés)

- ✅ **Sécurité RGPD** : Évite amendes (4% CA global)
- ✅ **Conformité** : Argument commercial SaaS
- ✅ **Réputation** : Rassure clients avocats
- ✅ **Scalabilité** : Prêt pour croissance
- ✅ **Recrutement** : Code propre attire talents

---

## ✅ CHECKLIST ANTI-CASSE

### Avant CHAQUE Modification (Obligatoire)

- [ ] **Git commit** de l'état actuel
  ```bash
  git add .
  git commit -m "État avant [NOM_MODIFICATION]"
  git tag backup-$(date +%Y%m%d-%H%M)
  ```

- [ ] **Backup base de données**
  ```bash
  mysqldump -u root -p gedavocat > backup_pre_[NOM_MODIF].sql
  ```

- [ ] **Tests E2E baseline**
  ```bash
  npx playwright test
  # Vérifier 40/40 OK
  ```

- [ ] **Application démarre**
  ```bash
  mvn spring-boot:run
  curl http://localhost:8092/actuator/health
  # → {"status":"UP"}
  ```

- [ ] **Documentation changement**
  - Créer ticket Jira/GitHub Issue
  - Décrire objectif
  - Estimer durée
  - Lister risques

---

### Pendant Modification

- [ ] **Lecture documentation officielle**
  - Spring Boot Migration Guide
  - Hibernate documentation
  - Library CHANGELOG

- [ ] **Tests unitaires au fil de l'eau**
  ```bash
  mvn test
  # Exécuter après chaque changement significatif
  ```

- [ ] **Pas de warnings**
  ```bash
  mvn clean package
  # [INFO] BUILD SUCCESS
  # Aucun WARNING non justifié
  ```

- [ ] **Revue de code (si équipe)**
  - Pair programming pour complexités
  - Pull Request avec description
  - Approbation avant merge

- [ ] **Commits atomiques**
  ```bash
  git commit -m "feat: ajout cache Hibernate sur User"
  git commit -m "test: tests cache invalidation"
  git commit -m "docs: documentation cache config"
  ```

---

### Après Modification

- [ ] **Build réussi**
  ```bash
  mvn clean package
  # [INFO] BUILD SUCCESS
  ```

- [ ] **Tests unitaires OK**
  ```bash
  mvn test
  # Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
  ```

- [ ] **Tests E2E OK**
  ```bash
  npx playwright test
  # 40 passed (Xm)
  ```

- [ ] **Tests manuels critiques**
  - [ ] Login avocat → ✅
  - [ ] Création dossier → ✅
  - [ ] Upload document → ✅
  - [ ] Signature Yousign → ✅
  - [ ] Paiement Stripe → ✅

- [ ] **Vérification logs startup**
  ```bash
  mvn spring-boot:run
  # Attendre "Started GedAvocatApplication in X seconds"
  # Vérifier aucune ERROR
  ```

- [ ] **Healthcheck OK**
  ```bash
  curl http://localhost:8092/actuator/health
  # {"status":"UP"}
  ```

- [ ] **Vérification mémoire**
  ```bash
  # JVM heap usage stable
  # Pas de memory leak
  ```

- [ ] **Git commit + tag**
  ```bash
  git add .
  git commit -m "[TYPE]: [DESCRIPTION]"
  git tag v1.0.0-[FEATURE]-done
  ```

- [ ] **Documentation mise à jour**
  - README.md
  - Confluence/Wiki
  - Commentaires code

---

## 📊 MÉTRIQUES DE SUCCÈS

### KPI Semaine 1 (MEP)

| Métrique | Cible | Mesure |
|----------|-------|--------|
| **Temps déploiement** | < 30 min | Déploiement + vérification |
| **Downtime** | 0 min | Blue/Green ou rolling update |
| **Tests E2E** | 40/40 OK | Playwright |
| **Score sécurité** | A+ | securityheaders.com |
| **Score SSL** | A+ | SSL Labs |
| **Bugs production** | 0 | Monitoring 48h post-MEP |

### KPI Semaine 2 (Performance)

| Métrique | Avant | Après | Gain |
|----------|-------|-------|------|
| **Bande passante (1 page)** | 2 MB | 600 KB | -70% |
| **Temps chargement** | 3s | 1s | -67% |
| **Score Lighthouse** | 85 | 95+ | +12% |
| **Requêtes statiques** | 100% | 20% (cache) | -80% |

### KPI Semaine 3 (Cache)

| Métrique | Avant | Après | Gain |
|----------|-------|-------|------|
| **Requêtes SQL/min** | 1000 | 400 | -60% |
| **Temps réponse API** | 200ms | 50ms | -75% |
| **Charge MySQL** | 80% | 40% | -50% |
| **Cache hit rate** | 0% | 80%+ | +80% |

### KPI Semaine 4 (Qualité)

| Métrique | Avant | Après | Gain |
|----------|-------|-------|------|
| **Couverture tests** | 40% | 60%+ | +50% |
| **Complexité cyclomatique** | 15 | 10 | -33% |
| **Duplication code** | 5% | 2% | -60% |
| **Dette technique** | 8h | 4h | -50% |

---

## 🎯 CONCLUSION ET RECOMMANDATIONS

### Synthèse

L'application **DocAvocat** peut être améliorée de manière **sécurisée et progressive** en **4 semaines** avec :

- ✅ **Semaine 1** : Mise en production (corrections critiques)
- ✅ **Semaines 2-4** : Améliorations continues (performance, qualité)

### Recommandation Finale

**📌 OPTION RECOMMANDÉE : Solo 4 semaines (9 600€)**

**Justification :**
- ✅ Risque minimal (tests à chaque étape)
- ✅ ROI excellent (+266% sur 3 ans)
- ✅ Cohérence technique (1 dev = vision globale)
- ✅ Timing raisonnable (4 semaines)

**Alternative si budget serré :**
- **Semaine 1 uniquement** : MEP sécurisée (3 000€)
- Semaines 2-4 reportées post-MEP (améliorations continues)

**Alternative si urgent :**
- **Équipe 4 personnes** : 2 semaines (11 900€)
- Parallélisation maximale
- Risque coordination équipe

---

### Next Steps

**Étape 1 : Validation**
- [ ] Revue planning avec CTO/CEO
- [ ] Validation budget
- [ ] Validation timeline

**Étape 2 : Préparation**
- [ ] Recrutement/allocation dev senior
- [ ] Préparation environnement staging
- [ ] Communication équipe

**Étape 3 : Démarrage**
- [ ] Kickoff Semaine 1 (lundi 8h)
- [ ] Setup outils (Jira, Slack, monitoring)
- [ ] Premier backup complet

---

## 📞 CONTACTS

| Rôle | Email | Téléphone |
|------|-------|-----------|
| **Chef de Projet** | pm@docavocat.fr | +33 6 XX XX XX XX |
| **Tech Lead** | tech@docavocat.fr | +33 6 XX XX XX XX |
| **DevOps** | devops@docavocat.fr | +33 6 XX XX XX XX |
| **Support 24/7** | support@docavocat.fr | +33 1 XX XX XX XX |

---

**Document créé le : 1er mars 2026**  
**Version : 1.0**  
**Auteur : Expert Sécurité & Architecture**

*Document confidentiel - Usage interne DocAvocat uniquement*  
*© 2026 DocAvocat SaaS - Tous droits réservés*
