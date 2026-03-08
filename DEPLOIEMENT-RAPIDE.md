# 🚀 Déploiement Rapide sur docavocat.fr - Pour Présentation

## ⚡ Option 1 : Déploiement Automatique via GitLab CI/CD (RECOMMANDÉ)

### **Sur GitLab.com :**

1. **Allez sur** : https://gitlab.com/ahmed.ghrissi/gedavocat-springboot
2. **Menu** : CI/CD → Pipelines
3. **Cliquez** : "Run Pipeline" (bouton bleu)
4. **Sélectionnez** : Branch `main`
5. **Cliquez** : "Run Pipeline"

Le pipeline va :
- ✅ Scanner la sécurité
- ✅ Compiler l'application
- ✅ Exécuter les tests
- ✅ Créer le package Docker
- ⏸️ Attendre votre validation pour le deploy

6. **Une fois à l'étape deploy** : Cliquez sur le bouton ▶️ "Play" pour déployer

---

## ⚡ Option 2 : Déploiement Manuel SSH (RAPIDE)

### **Commandes à exécuter sur le serveur :**

```bash
# 1. Se connecter au serveur
ssh user@docavocat.fr

# 2. Aller dans le répertoire du projet
cd /opt/docavocat  # Ou votre chemin

# 3. Récupérer les dernières modifications
git pull origin main

# 4. Configurer les variables d'environnement (PREMIÈRE FOIS SEULEMENT)
cd docker
cp .env.example .env
nano .env  # Modifier avec vos vraies clés

# 5. Démarrer/Redémarrer les services Docker

# Si c'est la PREMIÈRE fois (création BDD avec le dump)
docker-compose down -v
docker-compose up -d mysql
# Attendez 2-3 minutes que MySQL charge le dump
docker-compose logs -f mysql
# Quand vous voyez "ready for connections", faites Ctrl+C

# Démarrer l'application
docker-compose up -d app

# 6. Vérifier que tout tourne
docker-compose ps
docker-compose logs -f app
```

---

## ⚡ Option 3 : Import Dump Uniquement (Si MySQL existe déjà)

Si MySQL tourne déjà et vous voulez juste importer les nouvelles données :

### **Depuis votre PC Windows :**

```powershell
# Lancer le script d'import distant
.\scripts\import-dump-remote.ps1 -Server "docavocat.fr" -User "root"
```

Le script va :
1. Transférer le dump vers le serveur
2. L'importer dans MySQL
3. Redémarrer l'application

---

## ✅ Vérification Après Déploiement

### **Sur le serveur :**

```bash
# Vérifier MySQL
docker exec -it docavocat-mysql mysql -u doc_avocat -p
```

```sql
USE doc_avocat;
SHOW TABLES;  -- Doit afficher 22 tables
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM cases;
SELECT COUNT(*) FROM documents;
EXIT;
```

```bash
# Vérifier l'application
docker-compose logs app | tail -50

# Vérifier que le port est ouvert
curl http://localhost:8080
# Ou
curl https://docavocat.fr
```

---

## 🔧 Configuration des Variables d'Environnement

**Fichier** : `docker/.env`

### **OBLIGATOIRE pour la production :**

```bash
# Base de données
MYSQL_ROOT_PASSWORD=VotreMdpRootSecure2026!
MYSQL_PASSWORD=VotreMdpDocAvocatSecure2026!

# JWT (générer : openssl rand -base64 64)
JWT_SECRET=<votre_cle_jwt_generee>

# MFA (générer : openssl rand -base64 32)  
MFA_ENCRYPTION_KEY=<votre_cle_mfa_generee>

# Email Brevo
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=<votre_email>
MAIL_PASSWORD=<votre_cle_brevo>
MAIL_FROM=noreply@docavocat.fr

# URL
APP_BASE_URL=https://docavocat.fr

# Yousign (PROD - sans -sandbox)
YOUSIGN_API_KEY=<votre_cle_yousign>
YOUSIGN_API_URL=https://api.yousign.app

# Stripe (PROD)
STRIPE_API_KEY=sk_live_<votre_cle>
STRIPE_PUBLISHABLE_KEY=pk_live_<votre_cle>
STRIPE_WEBHOOK_SECRET=whsec_<votre_secret>

# RGPD
DPO_NAME=<Nom du DPO>
DPO_EMAIL=dpo@docavocat.fr
DPO_PHONE=<téléphone>
DPO_ADDRESS=<adresse>

# Grafana
GRAFANA_PASSWORD=<mdp_grafana_secure>
```

### **Générer les clés secrètes :**

```bash
# Sur le serveur
openssl rand -base64 64  # Pour JWT_SECRET
openssl rand -base64 32  # Pour MFA_ENCRYPTION_KEY
```

---

## 🆘 Dépannage Rapide

### **MySQL ne démarre pas :**
```bash
docker-compose logs mysql
# Si erreur, réinitialiser :
docker-compose down -v
docker-compose up -d mysql
```

### **L'application ne se connecte pas à MySQL :**
```bash
# Vérifier que MySQL est prêt
docker exec docavocat-mysql mysqladmin ping -h localhost

# Vérifier les variables d'environnement
docker exec docavocat-app env | grep DB_
```

### **Port déjà utilisé :**
```bash
# Voir ce qui utilise le port 8080
netstat -tulpn | grep 8080
# Tuer le processus ou changer le port dans docker-compose.yml
```

---

## 🎯 Checklist Avant Présentation

- [ ] Code poussé sur GitLab (`git push` ✅)
- [ ] Variables d'environnement configurées sur le serveur
- [ ] MySQL démarré avec le dump chargé
- [ ] Application démarrée (`docker-compose ps` → UP)
- [ ] Site accessible sur https://docavocat.fr
- [ ] Test de connexion (login/logout)
- [ ] SSL/TLS actif (cadenas dans le navigateur)

---

## 🚀 COMMANDE ULTRA-RAPIDE (Si tout est déjà configuré)

Sur le serveur :

```bash
ssh user@docavocat.fr "cd /opt/docavocat && git pull && cd docker && docker-compose up -d --force-recreate"
```

Ou si vous voulez tout réinitialiser :

```bash
ssh user@docavocat.fr "cd /opt/docavocat && git pull && cd docker && docker-compose down -v && docker-compose up -d"
```

---

**Quelle option préférez-vous pour déployer ?**
1. GitLab CI/CD (automatique)
2. SSH manuel
3. Script d'import du dump
