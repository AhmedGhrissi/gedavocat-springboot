# 🔧 CORRECTION BUG CONNEXION + PACKAGE COMPLET

## 🐛 CAUSE RACINE DU BUG DE CONNEXION

Le problème venait d'une **clé JWT mal formée** qui provoquait une exception silencieuse
au démarrage, rendant toute authentification impossible.

### Ce qui se passait

```
1. Formulaire login → POST /login → Spring Security
2. Spring Security → UserDetailsService → charge l'utilisateur OK
3. Spring Security → vérifie le mot de passe BCrypt → OK
4. Spring Security → crée le token JWT → 💥 EXCEPTION SILENCIEUSE
5. L'exception est avalée → Spring Security renvoie "bad credentials"
6. Résultat : /login?error=true sans aucune trace dans la console
```

### La clé fautive

```properties
# ❌ AVANT - chaîne texte brut, pas du Base64 valide
jwt.secret=gedavocat-super-secret-key-minimum-256-bits-pour-hs256-security-2024

# ✅ APRÈS - vraie clé encodée en Base64 (512 bits)
jwt.secret=5YGTYn++gP1BYYEu9echrStL3+udzI9yNLQFf58oIQqAkDIfVWHMtcC2zxTpJ1bVmUQ9BzDiBNKETQORcKTHGA==
```

`JwtService` fait `Decoders.BASE64.decode(secretKey)` → si la clé n'est pas du Base64
valide, `IllegalArgumentException` → Spring Security catch silencieusement → "bad credentials".

---

## ✅ TOUS LES CORRECTIFS APPLIQUÉS

### 1. `application.properties` — Clé JWT Base64 valide
```properties
jwt.secret=5YGTYn++gP1BYYEu9echrStL3+udzI9yNLQFf58oIQqAkDIfVWHMtcC2zxTpJ1bVmUQ9BzDiBNKETQORcKTHGA==
```

### 2. `SecurityConfig.java` — Session + usernameParameter
```java
// Session avec état (indispensable pour le formLogin MVC)
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
)
// Champ "email" dans le formulaire HTML (pas "username")
.formLogin(form -> form
    .loginProcessingUrl("/login")
    .usernameParameter("email")
    .passwordParameter("password")
)
```

### 3. `login.html` — Action du formulaire
```html
<!-- Pointe vers /login traité par Spring Security -->
<form th:action="@{/login}" method="post">
```

### 4. `data.sql` — Apostrophes supprimées + DELETE propres
```sql
-- Tous les noms de dossiers corrigés (plus d'apostrophes)
-- SET FOREIGN_KEY_CHECKS = 0/1 pour un import sans erreur
```

### 5. `schema.sql` — ENUM au lieu de CHECK
```sql
-- AVANT (ignoré par MySQL < 8.0.16)
role VARCHAR(20) CHECK (role IN (...))

-- APRÈS (toujours respecté)
role ENUM('ADMIN','LAWYER','CLIENT','LAWYER_SECONDARY')
```

---

## 🔐 CONNEXION — COMPTES DE TEST

**Mot de passe pour TOUS les comptes : `password123`**

| Email | Rôle | Accès |
|-------|------|-------|
| `admin@gedavocat.com` | Admin | Tout |
| `jean.dupont@gedavocat.com` | Avocat (Cabinet) | Dashboard, clients, dossiers |
| `marie.martin@gedavocat.com` | Avocat (Solo) | Dashboard, clients, dossiers |
| `pierre.bernard@gedavocat.com` | Avocat (Enterprise) | Dashboard, clients, dossiers |
| `sophie.lefebvre@gedavocat.com` | Collaboratrice | Dossiers partagés |
| `paul.durand@email.com` | Client | Ses dossiers uniquement |
| `claire.petit@email.com` | Cliente | Ses dossiers uniquement |

---

## 🧪 TESTS UNITAIRES INCLUS

### Structure des tests
```
src/test/
├── java/com/gedavocat/
│   ├── GedAvocatApplicationTest.java     ← Démarrage contexte Spring
│   ├── security/
│   │   ├── BCryptPasswordTest.java       ← Vérifie le hash data.sql
│   │   ├── JwtServiceTest.java           ← Génération/validation tokens
│   │   └── SecurityConfigTest.java       ← URLs publiques/protégées
│   ├── service/
│   │   ├── AuthServiceTest.java          ← Login, register, erreurs
│   │   └── ClientServiceTest.java        ← CRUD clients
│   └── controller/
│       └── AuthControllerTest.java       ← MockMvc formulaire login
└── resources/
    └── application-test.properties       ← H2 en mémoire pour les tests
```

### Lancer les tests
```bash
# Tous les tests
mvn test

# Un test spécifique
mvn test -Dtest=BCryptPasswordTest

# Avec rapport
mvn test surefire-report:report
# Rapport → target/site/surefire-report.html
```

### Test prioritaire à lancer en premier
```bash
mvn test -Dtest=BCryptPasswordTest
```
Ce test vérifie que le hash BCrypt dans `data.sql` correspond bien à `password123`.
S'il passe → le problème de connexion n'est pas le hash.

---

## 🚀 DÉPLOIEMENT O2SWITCH

### Prérequis O2Switch
- Hébergement mutualisé avec accès SSH ✅
- MySQL 8.0 via cPanel ✅
- Java 17 disponible (vérifier avec `java -version`)

### Étape 1 — Préparer la base MySQL (cPanel)
1. Ouvrir cPanel → **MySQL Databases**
2. Créer la base : `gedavocat`
3. Créer l'utilisateur : `gedavocat_user` + mot de passe fort
4. Attribuer **tous les droits** à l'utilisateur sur la base
5. Importer `database-init-CORRIGE.sql` via **phpMyAdmin**
   - Choisir la base `gedavocat`
   - Onglet **Importer** → choisir le fichier → **Exécuter**

### Étape 2 — Compiler localement
```bash
# Sur votre machine locale
cd gedavocat-springboot-final
mvn clean package -DskipTests

# Le JAR est ici :
ls -lh target/gedavocat-*.jar
```

### Étape 3 — Configurer application.properties pour O2Switch
Editer `application.properties` (ou créer `application-prod.properties`) :
```properties
# Base de données O2Switch
spring.datasource.url=jdbc:mysql://localhost:3306/VOTRE_USER_gedavocat?useSSL=false&serverTimezone=Europe/Paris&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
spring.datasource.username=VOTRE_USER_gedavocat
spring.datasource.password=VOTRE_MOT_DE_PASSE_DB

# JWT - CHANGER cette clé en production !
jwt.secret=5YGTYn++gP1BYYEu9echrStL3+udzI9yNLQFf58oIQqAkDIfVWHMtcC2zxTpJ1bVmUQ9BzDiBNKETQORcKTHGA==

# Hibernate - ne pas recréer les tables
spring.jpa.hibernate.ddl-auto=none

# Chemin upload sur O2Switch
app.upload.dir=/home/VOTRE_LOGIN/gedavocat/uploads/documents
```

### Étape 4 — Uploader les fichiers via SFTP
```
Connexion SFTP :
  Host     : votre-domaine.com
  Port     : 22
  User     : votre-login-o2switch
  Password : votre-mot-de-passe

Uploader dans : /home/VOTRE_LOGIN/gedavocat/
  - gedavocat-0.0.1-SNAPSHOT.jar  → renommer en app.jar
  - application.properties
  - scripts/start.sh
  - scripts/stop.sh
  - scripts/restart.sh
  - scripts/backup.sh
```

### Étape 5 — Démarrer via SSH
```bash
# Se connecter en SSH
ssh VOTRE_LOGIN@votre-domaine.com

# Créer les dossiers
mkdir -p ~/gedavocat/uploads/documents ~/gedavocat/logs

# Rendre les scripts exécutables
chmod +x ~/gedavocat/scripts/*.sh

# Démarrer l'application
~/gedavocat/scripts/start.sh

# Vérifier que ça tourne
tail -f ~/gedavocat/logs/app.log

# Accéder à l'application
# http://votre-domaine.com:8080
```

### Étape 6 — Configurer .htaccess (reverse proxy)
```apache
# Dans le dossier public_html
RewriteEngine On
RewriteRule ^(.*)$ http://127.0.0.1:8080/$1 [P,L]
```

### Commandes de gestion
```bash
# Démarrer
~/gedavocat/scripts/start.sh

# Arrêter
~/gedavocat/scripts/stop.sh

# Redémarrer
~/gedavocat/scripts/restart.sh

# Sauvegarder la base
~/gedavocat/scripts/backup.sh

# Voir les logs en direct
tail -f ~/gedavocat/logs/app.log

# Voir si l'app tourne
ps aux | grep gedavocat
```

### Cron jobs recommandés (cPanel → Cron Jobs)
```bash
# Backup quotidien à 2h du matin
0 2 * * * /home/VOTRE_LOGIN/gedavocat/scripts/backup.sh

# Vérification que l'app tourne (toutes les 5 min)
*/5 * * * * pgrep -f "gedavocat" || /home/VOTRE_LOGIN/gedavocat/scripts/start.sh
```

---

## 📋 CHECKLIST FINALE

### Avant de déployer
- [ ] `mvn test` passe sans erreur
- [ ] `mvn clean package` génère le JAR
- [ ] Base MySQL importée avec `database-init-CORRIGE.sql`
- [ ] `application.properties` configuré avec les vraies valeurs

### Sur O2Switch
- [ ] Base `gedavocat` créée dans cPanel
- [ ] Utilisateur MySQL avec tous les droits
- [ ] Données importées via phpMyAdmin
- [ ] JAR uploadé via SFTP
- [ ] Scripts `chmod +x`
- [ ] Application démarrée
- [ ] `.htaccess` configuré
- [ ] SSL activé (Let's Encrypt dans cPanel)

### Après déploiement
- [ ] Connexion testée avec `admin@gedavocat.com` / `password123`
- [ ] **Changer le mot de passe admin immédiatement**
- [ ] Configurer Yousign (clé API)
- [ ] Configurer Stripe (clés API)
- [ ] Configurer RPVA (si certificat disponible)
- [ ] Activer les cron jobs (backup, monitoring)

---

## 🆘 DÉPANNAGE CONNEXION

### Si erreur de connexion après déploiement

**Étape 1 — Vérifier que les utilisateurs sont en base**
```sql
SELECT id, email, role, LEFT(password,20) as pwd_debut FROM users;
-- Doit retourner 7 lignes
-- pwd_debut doit commencer par $2a$10$
```

**Étape 2 — Vérifier le hash manuellement**
```sql
-- Dans MySQL, impossible de "décoder" BCrypt
-- Mais on peut vérifier via Spring Boot au démarrage :
-- Ajouter temporairement dans un @PostConstruct :
-- System.out.println(passwordEncoder.matches("password123", hashDeLaBase));
```

**Étape 3 — Activer les logs de debug Spring Security**
```properties
# Dans application.properties temporairement
logging.level.org.springframework.security=TRACE
```
Puis regarder les logs pour voir exactement où l'authentification échoue.

**Étape 4 — Vérifier la clé JWT**
```java
// Dans un test unitaire :
byte[] key = Decoders.BASE64.decode(votreClé);
// Si IllegalArgumentException → clé pas en Base64 valide
```

---

## 📁 FICHIERS LIVRÉS

```
📦 GED-AVOCAT-PACKAGE-FINAL/
├── 🔧 CORRECTION-BUG-CONNEXION.md    ← CE FICHIER
├── 📄 database-init-CORRIGE.sql       ← SQL à importer dans MySQL
├── 📦 GED-AVOCAT-ULTRA-COMPLET.zip   ← Tout le code source
└── gedavocat-springboot-final/
    ├── src/main/
    │   ├── java/com/gedavocat/
    │   │   ├── config/SecurityConfig.java        ✅ CORRIGÉ
    │   │   ├── controller/ (8 contrôleurs)
    │   │   ├── service/ (8 services)
    │   │   ├── model/ (6 entités)
    │   │   ├── repository/ (6 repos)
    │   │   └── security/ (JWT + UserDetails)
    │   └── resources/
    │       ├── application.properties            ✅ CORRIGÉ (clé JWT)
    │       ├── schema.sql                        ✅ CORRIGÉ (ENUM)
    │       ├── data.sql                          ✅ CORRIGÉ (apostrophes)
    │       └── templates/ (14 pages HTML)
    ├── src/test/
    │   ├── java/com/gedavocat/
    │   │   ├── GedAvocatApplicationTest.java     ✅ NOUVEAU
    │   │   ├── security/
    │   │   │   ├── BCryptPasswordTest.java       ✅ NOUVEAU
    │   │   │   ├── JwtServiceTest.java           ✅ NOUVEAU
    │   │   │   └── SecurityConfigTest.java       ✅ NOUVEAU
    │   │   ├── service/
    │   │   │   ├── AuthServiceTest.java          ✅ NOUVEAU
    │   │   │   └── ClientServiceTest.java        ✅ NOUVEAU
    │   │   └── controller/
    │   │       └── AuthControllerTest.java       ✅ NOUVEAU
    │   └── resources/
    │       └── application-test.properties       ✅ NOUVEAU (H2)
    ├── pom.xml                                   ✅ + H2 test scope
    ├── scripts/ (start/stop/restart/backup)
    └── deploy-o2switch/
```
