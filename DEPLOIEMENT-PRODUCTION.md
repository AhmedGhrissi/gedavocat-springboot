# 🚀 Guide de Déploiement sur docavocat.fr

## 📋 Vue d'ensemble

Ce guide explique comment déployer DocAvocat sur le serveur de production **docavocat.fr** avec la base de données complète (structure + données).

---

## 🎯 Méthodes de Déploiement

### **Méthode 1 : Fresh Install (Nouveau serveur)** ⭐ RECOMMANDÉE

Cette méthode déploie tout depuis zéro, idéal pour un nouveau serveur.

#### **Étapes :**

1. **Sur votre machine locale, poussez les changements :**
```powershell
git add .
git commit -m "feat: Ajout dump complet BDD pour production"
git push
```

2. **Sur le serveur docavocat.fr :**
```bash
# Se connecter au serveur
ssh user@docavocat.fr

# Cloner ou mettre à jour le projet
git clone https://gitlab.com/ahmed.ghrissi/gedavocat-springboot.git
cd gedavocat-springboot

# Ou si déjà cloné :
git pull

# Configurer les variables d'environnement
cp docker/.env.example docker/.env
nano docker/.env  # Modifier avec vos vraies clés

# Lancer Docker Compose
cd docker
docker-compose down -v  # Supprimer les anciens volumes
docker-compose up -d mysql

# Attendre que MySQL charge le dump (2-3 minutes)
docker-compose logs -f mysql
# Attendez de voir : "ready for connections"

# Démarrer l'application
docker-compose up -d app
```

✅ **La base de données sera automatiquement créée et remplie avec les données du dump !**

---

### **Méthode 2 : Import Manuel (Serveur existant)**

Si vous avez déjà un serveur avec MySQL en cours et voulez juste importer les données.

#### **Depuis Windows :**
```powershell
.\scripts\import-dump-remote.ps1 -Server "docavocat.fr" -User "root"
```

#### **Depuis Linux/Mac :**
```bash
chmod +x scripts/import-dump-remote.sh
./scripts/import-dump-remote.sh docavocat.fr root
```

Le script va :
1. Transférer le dump vers le serveur
2. L'importer dans MySQL
3. Nettoyer les fichiers temporaires

---

## 📊 Contenu du Dump

Le fichier `docker/init/01-complete-dump.sql` contient :

| Élément | Quantité |
|---------|----------|
| **Tables** | 22 |
| **Lignes SQL** | ~1000 |
| **Base de données** | doc_avocat |

### **Tables incluses :**
```
appointments          labft_checks
audit_logs           notifications
barreaux_france      payments
case_assignments     permissions
case_share_links     refresh_tokens
cases                rpva_communications
clients              signature_events
document_shares      signatures
documents            users
firm_members
firms
invoice_items
invoices
```

---

## 🔧 Configuration Production

### **Variables d'environnement critiques dans `docker/.env` :**

```bash
# Base de données
MYSQL_ROOT_PASSWORD=VotreMdpRootSecure2026!
MYSQL_PASSWORD=VotreMdpDocAvocatSecure2026!

# JWT (générer avec : openssl rand -base64 64)
JWT_SECRET=votre_cle_jwt_generee_64_caracteres

# MFA (générer avec : openssl rand -base64 32)
MFA_ENCRYPTION_KEY=votre_cle_mfa_32_caracteres

# Email Brevo
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=votre_email@docavocat.fr
MAIL_PASSWORD=votre_cle_smtp_brevo
MAIL_FROM=noreply@docavocat.fr

# URLs
APP_BASE_URL=https://docavocat.fr

# Yousign (production)
YOUSIGN_API_KEY=votre_cle_yousign_prod
YOUSIGN_API_URL=https://api.yousign.app  # Sans -sandbox

# Stripe (production)
STRIPE_API_KEY=sk_live_votre_cle_stripe
STRIPE_PUBLISHABLE_KEY=pk_live_votre_cle_publique
STRIPE_WEBHOOK_SECRET=whsec_votre_secret_webhook

# RGPD / DPO
DPO_NAME=Nom du DPO
DPO_EMAIL=dpo@docavocat.fr
DPO_PHONE=+33 X XX XX XX XX
DPO_ADDRESS=Adresse complète du cabinet
```

⚠️ **IMPORTANT** : Générez de vraies clés aléatoires, ne gardez pas les valeurs de dev !

---

## 🔒 Sécurité

### **Permissions du fichier .env :**
```bash
chmod 600 docker/.env
chown docavocat:docavocat docker/.env  # Votre utilisateur applicatif
```

### **SSL/TLS :**
Le fichier compose inclut Nginx avec Let's Encrypt. Configurez :
```bash
# Sur le serveur
certbot --nginx -d docavocat.fr -d www.docavocat.fr
```

---

## ✅ Vérification du Déploiement

### **1. Vérifier MySQL :**
```bash
docker exec -it docavocat-mysql mysql -u doc_avocat -p
```

```sql
USE doc_avocat;
SHOW TABLES;
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM cases;
SELECT COUNT(*) FROM documents;
```

### **2. Vérifier l'application :**
```bash
docker-compose logs -f app
```

### **3. Tester l'accès :**
```bash
curl https://docavocat.fr
```

---

## 🔄 Mise à Jour

Pour mettre à jour l'application sans perdre les données :

```bash
cd /path/to/gedavocat-springboot
git pull
cd docker
docker-compose pull app
docker-compose up -d --no-deps app
```

**Note :** Le volume MySQL (`mysql_data`) est persistant, vos données sont conservées.

---

## 🆘 Dépannage

### **Le dump ne se charge pas :**
```bash
# Vérifier les logs MySQL au démarrage
docker-compose logs mysql

# Importer manuellement
docker exec -i docavocat-mysql mysql -u root -p doc_avocat < docker/init/01-complete-dump.sql
```

### **L'application ne démarre pas :**
```bash
# Vérifier les logs
docker-compose logs app

# Vérifier les variables d'environnement
docker exec docavocat-app env | grep DB_
```

### **Erreur de connexion MySQL :**
```bash
# Vérifier que MySQL est prêt
docker exec docavocat-mysql mysqladmin ping -h localhost
```

---

## 📈 Monitoring

Grafana est inclus dans le compose et accessible sur `http://docavocat.fr:3000` (tunnel SSH recommandé).

```bash
# Tunnel SSH pour accès sécurisé à Grafana
ssh -L 3000:localhost:3000 user@docavocat.fr
# Puis ouvrez : http://localhost:3000
```

Identifiants par défaut :
- User: `admin`
- Pass: `${GRAFANA_PASSWORD}` (configuré dans `.env`)

---

## 🎯 Checklist Avant Production

- [ ] Variables d'environnement configurées (clés réelles, pas dev)
- [ ] SSL/TLS configuré avec certbot
- [ ] Firewall configuré (ports 80, 443 ouverts, 3306 fermé)
- [ ] Dump importé et vérifié
- [ ] Backup automatique configuré
- [ ] Monitoring Grafana accessible
- [ ] Logs centralisés (Loki)
- [ ] Tests de charge effectués
- [ ] Plan de reprise d'activité (PRA) documenté
- [ ] DPO contacté et informé
- [ ] Conformité RGPD vérifiée

---

## 📞 Support

En cas de problème, consultez :
- [DOCKER-MYSQL-SETUP.md](DOCKER-MYSQL-SETUP.md) - Configuration MySQL
- [BDD-INITIALISATION.md](BDD-INITIALISATION.md) - Initialisation BDD
- [GUIDE-DEMO.md](GUIDE-DEMO.md) - Tests locaux

---

**Date de dernière mise à jour** : 9 mars 2026  
**Version** : 1.0.0
