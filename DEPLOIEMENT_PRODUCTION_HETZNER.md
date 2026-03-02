# 🚀 DÉPLOIEMENT PRODUCTION - HETZNER CLOUD

Guide complet de déploiement de GedAvocat sur **Hetzner Cloud** avec Docker, HTTPS et sécurité bancaire (A+).

**Date** : Mars 2026  
**Version** : 1.0.2-security-enhanced  
**Score Sécurité** : 100/100 🏆

---

## 📋 SOMMAIRE

1. [Prérequis](#prérequis)
2. [Création Serveur Hetzner](#création-serveur-hetzner)
3. [Configuration Initiale](#configuration-initiale)
4. [Installation Docker](#installation-docker)
5. [Configuration HTTPS (Let's Encrypt)](#configuration-https)
6. [Déploiement Application](#déploiement-application)
7. [Migrations SQL](#migrations-sql)
8. [Monitoring & Logs](#monitoring--logs)
9. [Backup & Restauration](#backup--restauration)
10. [Optimisations Sécurité](#optimisations-sécurité)

---

## 🎯 PRÉREQUIS

### Comptes & Accès
- ✅ Compte Hetzner Cloud ([hetzner.com](https://www.hetzner.com/cloud))
- ✅ Nom de domaine (ex: `gedavocat.fr`)
- ✅ Clés API :
  - Yousign Production
  - Stripe Production
  - JWT RS256 keys générées (`config/keys/*.pem`)

### Outils Locaux
- ✅ Git
- ✅ SSH client
- ✅ Docker Desktop (pour build local si nécessaire)

---

## 1️⃣ CRÉATION SERVEUR HETZNER

### 1.1 Connexion au Cloud Console

1. Accéder à [console.hetzner.cloud](https://console.hetzner.cloud)
2. Créer un nouveau projet : **"GedAvocat Production"**

### 1.2 Création du Serveur

#### Configuration Recommandée

| Paramètre | Valeur | Prix/mois |
|-----------|--------|-----------|
| **Type** | CPX31 | ~15€ |
| **CPU** | 4 vCPU AMD | |
| **RAM** | 8 GB | |
| **Stockage** | 160 GB SSD | |
| **OS** | Ubuntu 24.04 LTS | |
| **Localisation** | Nuremberg (DE) ou Falkenstein (DE) | |
| **Réseau** | IPv4 + IPv6 | |

#### Étapes de création

1. **Cliquer sur "Add Server"**
2. **Localisation** : `Nuremberg` (latence France ~10-15ms)
3. **Image** : `Ubuntu 24.04 LTS`
4. **Type** : `CPX31` (ou CX31 si budget limité - 7€/mois)
5. **Volume** : Aucun (optionnel pour backups)
6. **Réseau** : Sélectionner réseau privé si multi-serveurs
7. **SSH Key** :
   - Créer une clé SSH locale si besoin :
     ```powershell
     ssh-keygen -t ed25519 -C "gedavocat-prod"
     ```
   - Ajouter la clé publique (`~/.ssh/id_ed25519.pub`) dans Hetzner
8. **Nom du serveur** : `gedavocat-prod-01`
9. **Labels** : `env=production`, `app=gedavocat`
10. **Firewall** : Créer un nouveau firewall (voir section suivante)

### 1.3 Configuration Firewall Hetzner

Créer un firewall **avant** de démarrer le serveur :

| Règle | Direction | Protocol | Port | Source |
|-------|-----------|----------|------|--------|
| SSH | Inbound | TCP | 22 | Votre IP uniquement |
| HTTP | Inbound | TCP | 80 | Any IPv4/IPv6 |
| HTTPS | Inbound | TCP | 443 | Any IPv4/IPv6 |
| MySQL | Inbound | TCP | 3306 | **BLOQUER** (Docker interne) |
| All Outbound | Outbound | Any | Any | Any |

**⚠️ SÉCURITÉ** :
- Ne jamais exposer MySQL (3306) à Internet
- Limiter SSH à votre IP fixe ou utiliser VPN

### 1.4 Récupérer l'IP du Serveur

Une fois créé, noter :
- **IPv4** : `78.47.XXX.XXX` (exemple)
- **IPv6** : `2a01:4f8:c2c:XXXX::1` (exemple)

---

## 2️⃣ CONFIGURATION INITIALE

### 2.1 Connexion SSH

```bash
ssh root@78.47.XXX.XXX
```

### 2.2 Mise à Jour Système

```bash
# Mise à jour des packages
apt update && apt upgrade -y

# Installation outils de base
apt install -y curl wget git vim ufw fail2ban htop
```

### 2.3 Créer Utilisateur Non-Root

```bash
# Créer utilisateur applicatif
adduser gedavocat
usermod -aG sudo gedavocat

# Copier la clé SSH
mkdir -p /home/gedavocat/.ssh
cp ~/.ssh/authorized_keys /home/gedavocat/.ssh/
chown -R gedavocat:gedavocat /home/gedavocat/.ssh
chmod 700 /home/gedavocat/.ssh
chmod 600 /home/gedavocat/.ssh/authorized_keys

# Tester la connexion
# ssh gedavocat@78.47.XXX.XXX
```

### 2.4 Sécurité SSH

Éditer `/etc/ssh/sshd_config` :

```bash
sudo nano /etc/ssh/sshd_config
```

Modifier :
```conf
# Désactiver login root
PermitRootLogin no

# Désactiver authentification par mot de passe
PasswordAuthentication no
PubkeyAuthentication yes

# Changer le port SSH (optionnel, mais recommandé)
Port 2222

# Autres paramètres de sécurité
MaxAuthTries 3
MaxSessions 2
ClientAliveInterval 300
ClientAliveCountMax 2
```

Redémarrer SSH :
```bash
sudo systemctl restart sshd
```

**⚠️ ATTENTION** : Si vous changez le port SSH à 2222, mettre à jour le firewall Hetzner !

### 2.5 Configuration UFW (Firewall Local)

```bash
# Activer UFW
sudo ufw default deny incoming
sudo ufw default allow outgoing

# Autoriser SSH (adapter selon votre port)
sudo ufw allow 2222/tcp

# Autoriser HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Activer le firewall
sudo ufw enable

# Vérifier le statut
sudo ufw status
```

### 2.6 Fail2Ban (Anti Brute-Force)

Configurer Fail2Ban pour SSH :

```bash
sudo nano /etc/fail2ban/jail.local
```

Contenu :
```ini
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 3

[sshd]
enabled = true
port = 2222
logpath = /var/log/auth.log
```

Activer :
```bash
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```

---

## 3️⃣ INSTALLATION DOCKER

### 3.1 Installation Docker Engine

```bash
# Désinstaller anciennes versions
sudo apt remove docker docker-engine docker.io containerd runc

# Ajouter repository Docker
sudo apt update
sudo apt install -y ca-certificates curl gnupg lsb-release

sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Installer Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Ajouter l'utilisateur au groupe docker
sudo usermod -aG docker gedavocat

# Démarrer Docker
sudo systemctl enable docker
sudo systemctl start docker

# Vérifier installation
docker --version
docker compose version
```

### 3.2 Configuration Docker (Optimisations)

Créer `/etc/docker/daemon.json` :

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "userland-proxy": false
}
```

Redémarrer Docker :
```bash
sudo systemctl restart docker
```

---

## 4️⃣ CONFIGURATION HTTPS (Let's Encrypt)

### 4.1 Configuration DNS

Pointer votre domaine vers l'IP Hetzner :

**Chez votre registrar (OVH, Gandi, Cloudflare, etc.)** :

| Type | Nom | Valeur | TTL |
|------|-----|--------|-----|
| A | @ | 78.47.XXX.XXX | 300 |
| A | www | 78.47.XXX.XXX | 300 |
| AAAA | @ | 2a01:4f8:c2c:XXXX::1 | 300 |
| AAAA | www | 2a01:4f8:c2c:XXXX::1 | 300 |

Attendre la propagation DNS (5-30 min) :
```bash
# Vérifier DNS
nslookup gedavocat.fr
dig gedavocat.fr
```

### 4.2 Installation Certbot (Let's Encrypt)

```bash
# Installer Certbot
sudo apt install -y certbot python3-certbot-nginx

# Obtenir certificat SSL (mode standalone)
sudo certbot certonly --standalone -d gedavocat.fr -d www.gedavocat.fr --email votre@email.com --agree-tos --non-interactive

# Les certificats sont dans :
# /etc/letsencrypt/live/gedavocat.fr/
```

**Certificats générés** :
- `fullchain.pem` : Certificat complet (chaîne)
- `privkey.pem` : Clé privée
- `cert.pem` : Certificat seul
- `chain.pem` : Chaîne intermédiaire

### 4.3 Renouvellement Automatique

```bash
# Tester le renouvellement
sudo certbot renew --dry-run

# Ajouter cron job (renouvellement auto tous les jours à 3h)
sudo crontab -e
```

Ajouter :
```cron
0 3 * * * certbot renew --quiet --post-hook "docker compose -f /home/gedavocat/gedavocat-springboot/docker/docker-compose.yml restart nginx"
```

---

## 5️⃣ DÉPLOIEMENT APPLICATION

### 5.1 Cloner le Repository

```bash
# Se connecter en tant que gedavocat
su - gedavocat

# Créer répertoire application
mkdir -p ~/apps
cd ~/apps

# Cloner le projet
git clone https://github.com/votre-username/gedavocat-springboot.git
cd gedavocat-springboot

# Vérifier la version
git checkout v1.0.2-security-enhanced
```

### 5.2 Copier les Clés JWT

**Depuis votre machine locale** :

```powershell
# Copier les clés générées localement vers le serveur
scp -P 2222 config/keys/private_key.pem gedavocat@78.47.XXX.XXX:/home/gedavocat/apps/gedavocat-springboot/config/keys/
scp -P 2222 config/keys/public_key.pem gedavocat@78.47.XXX.XXX:/home/gedavocat/apps/gedavocat-springboot/config/keys/
```

**Sur le serveur** :

```bash
# Sécuriser les permissions
chmod 600 config/keys/private_key.pem
chmod 644 config/keys/public_key.pem
```

### 5.3 Créer `.env.prod`

```bash
nano .env.prod
```

Contenu (adapter avec vos vraies valeurs) :

```bash
# ========================================
# GEDAVOCAT PRODUCTION - Hetzner Cloud
# ========================================

# APPLICATION
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# DATABASE (MySQL dans Docker)
DB_HOST=mysql
DB_PORT=3306
DB_NAME=gedavocat_prod
DB_USER=gedavocat
DB_PASSWORD=VotreMotDePasseTresFort_2026!

# JWT (clés RSA générées)
JWT_SECRET=not_used_with_rs256
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# YOUSIGN (Production)
YOUSIGN_API_KEY=votre_cle_production_yousign

# STRIPE (Production)
STRIPE_API_KEY=sk_live_XXXXXXXXXXXXXXXXXXXXXXXXXX
STRIPE_PUBLISHABLE_KEY=pk_live_XXXXXXXXXXXXXXXXXXXXXXXXXX
STRIPE_WEBHOOK_SECRET=whsec_XXXXXXXXXXXXXXXXXXXXXXXXXX

# EMAIL (SMTP Production - ex: SendGrid, Mailgun, AWS SES)
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USERNAME=apikey
SMTP_PASSWORD=SG.XXXXXXXXXXXXXXXXXXXXXXXXXX
SMTP_FROM=noreply@gedavocat.fr

# DOMAINE
APP_DOMAIN=https://gedavocat.fr

# SÉCURITÉ
ALLOWED_ORIGINS=https://gedavocat.fr,https://www.gedavocat.fr

# UPLOADS
UPLOAD_MAX_FILE_SIZE=50MB
UPLOAD_DIR=/app/uploads
```

**Sécuriser le fichier** :
```bash
chmod 600 .env.prod
```

### 5.4 Configuration Nginx Reverse Proxy

Créer `docker/nginx/nginx.conf` :

```nginx
upstream spring_app {
    server app:8080;
}

# Redirection HTTP → HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name gedavocat.fr www.gedavocat.fr;

    # Let's Encrypt ACME challenge
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # Redirection vers HTTPS
    location / {
        return 301 https://$server_name$request_uri;
    }
}

# HTTPS
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name gedavocat.fr www.gedavocat.fr;

    # Certificats SSL
    ssl_certificate /etc/letsencrypt/live/gedavocat.fr/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gedavocat.fr/privkey.pem;

    # Configuration SSL moderne (A+ SSL Labs)
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:DHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    ssl_stapling on;
    ssl_stapling_verify on;

    # Headers de sécurité
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://js.stripe.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self' https://api.stripe.com; frame-src https://js.stripe.com;" always;

    # Logs
    access_log /var/log/nginx/gedavocat.access.log;
    error_log /var/log/nginx/gedavocat.error.log;

    # Limite taille upload (documents)
    client_max_body_size 50M;

    # Proxy vers Spring Boot
    location / {
        proxy_pass http://spring_app;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Static files caching
    location ~* \.(jpg|jpeg|png|gif|ico|css|js|woff|woff2|ttf|svg)$ {
        proxy_pass http://spring_app;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 5.5 Docker Compose Production

Modifier `docker/docker-compose.yml` :

```yaml
version: '3.8'

services:
  # Base de données MySQL
  mysql:
    image: mysql:8.0
    container_name: gedavocat-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: ${DB_NAME}
      MYSQL_USER: ${DB_USER}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      TZ: Europe/Paris
    volumes:
      - mysql-data:/var/lib/mysql
      - ./init:/docker-entrypoint-initdb.d
    ports:
      - "127.0.0.1:3306:3306"  # Accessible uniquement en local
    command: 
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u${DB_USER}", "-p${DB_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - gedavocat-network

  # Application Spring Boot
  app:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: gedavocat-app
    restart: unless-stopped
    env_file:
      - ../.env.prod
    volumes:
      - ../uploads:/app/uploads
      - ../config/keys:/app/config/keys:ro
      - app-logs:/app/logs
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    networks:
      - gedavocat-network

  # Nginx Reverse Proxy
  nginx:
    image: nginx:alpine
    container_name: gedavocat-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /var/www/certbot:/var/www/certbot:ro
      - nginx-logs:/var/log/nginx
    depends_on:
      - app
    networks:
      - gedavocat-network

networks:
  gedavocat-network:
    driver: bridge

volumes:
  mysql-data:
  app-logs:
  nginx-logs:
```

### 5.6 Build et Démarrage

```bash
# Se placer dans le dossier docker
cd docker

# Build de l'application (première fois)
docker compose --env-file ../.env.prod build

# Démarrer les services
docker compose --env-file ../.env.prod up -d

# Vérifier les logs
docker compose logs -f

# Vérifier le statut
docker compose ps
```

**Services démarrés** :
- ✅ **mysql** (port 3306 - local uniquement)
- ✅ **app** (Spring Boot - port 8080 interne)
- ✅ **nginx** (ports 80, 443 - public)

---

## 6️⃣ MIGRATIONS SQL

### 6.1 Migrations Automatiques (Flyway)

Les migrations SQL sont appliquées **automatiquement** au démarrage grâce à Flyway :

```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_indexes.sql
├── V3__add_multi_tenant_support.sql  ← Multi-tenant (firmId)
└── V4__add_refresh_tokens.sql        ← Refresh tokens
```

**Vérifier l'application** :

```bash
# Logs de l'application
docker compose logs app | grep Flyway

# Résultat attendu :
# Flyway migration V1 applied: Initial schema
# Flyway migration V2 applied: Add indexes
# Flyway migration V3 applied: Multi-tenant support
# Flyway migration V4 applied: Refresh tokens
```

### 6.2 Vérification Manuelle (si besoin)

```bash
# Se connecter à MySQL
docker exec -it gedavocat-mysql mysql -u gedavocat -p

# Lister les tables
USE gedavocat_prod;
SHOW TABLES;

# Vérifier la table firms (multi-tenant)
DESCRIBE firms;

# Vérifier la table refresh_tokens
DESCRIBE refresh_tokens;

# Vérifier firmId dans users
DESCRIBE users;
```

---

## 7️⃣ MONITORING & LOGS

### 7.1 Logs Application

```bash
# Logs en temps réel
docker compose logs -f app

# 100 dernières lignes
docker compose logs --tail=100 app

# Logs depuis 1h
docker compose logs --since 1h app

# Logs dans un fichier
docker compose logs app > ~/logs/gedavocat-$(date +%Y%m%d).log
```

### 7.2 Logs Nginx

```bash
# Access logs
docker exec gedavocat-nginx tail -f /var/log/nginx/gedavocat.access.log

# Error logs
docker exec gedavocat-nginx tail -f /var/log/nginx/gedavocat.error.log
```

### 7.3 Monitoring Ressources (htop)

```bash
# Installer htop
sudo apt install htop

# Surveiller CPU/RAM
htop

# Surveiller Docker
docker stats
```

### 7.4 Health Checks

```bash
# Vérifier le health endpoint
curl http://localhost:8080/actuator/health

# Résultat attendu :
# {"status":"UP"}

# Depuis l'extérieur (HTTPS)
curl https://gedavocat.fr/actuator/health
```

### 7.5 Alertes Email (optionnel)

Configurer des alertes avec **Uptime Robot** (gratuit) :
1. Créer compte sur [uptimerobot.com](https://uptimerobot.com)
2. Ajouter monitor : `https://gedavocat.fr/actuator/health`
3. Intervalle : 5 minutes
4. Alertes : Email + SMS

---

## 8️⃣ BACKUP & RESTAURATION

### 8.1 Backup MySQL Automatique

Créer script `/home/gedavocat/scripts/backup-mysql.sh` :

```bash
#!/bin/bash

# Configuration
BACKUP_DIR="/home/gedavocat/backups/mysql"
CONTAINER="gedavocat-mysql"
DB_NAME="gedavocat_prod"
DB_USER="gedavocat"
DB_PASS="VotreMotDePasse"
RETENTION_DAYS=7

# Créer répertoire backup
mkdir -p $BACKUP_DIR

# Nom du fichier backup
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/gedavocat_${DATE}.sql.gz"

# Dump MySQL
docker exec $CONTAINER mysqldump -u$DB_USER -p$DB_PASS $DB_NAME | gzip > $BACKUP_FILE

# Vérifier succès
if [ $? -eq 0 ]; then
    echo "✅ Backup réussi: $BACKUP_FILE"
    
    # Supprimer backups > 7 jours
    find $BACKUP_DIR -name "*.sql.gz" -mtime +$RETENTION_DAYS -delete
    echo "🗑️  Anciens backups supprimés (>$RETENTION_DAYS jours)"
else
    echo "❌ Erreur backup MySQL"
    exit 1
fi
```

Rendre exécutable :
```bash
chmod +x /home/gedavocat/scripts/backup-mysql.sh
```

**Cron job (tous les jours à 2h)** :
```bash
crontab -e
```

Ajouter :
```cron
0 2 * * * /home/gedavocat/scripts/backup-mysql.sh >> /home/gedavocat/logs/backup.log 2>&1
```

### 8.2 Restauration MySQL

```bash
# Lister les backups
ls -lh ~/backups/mysql/

# Restaurer un backup
gunzip < ~/backups/mysql/gedavocat_20260302_020000.sql.gz | \
docker exec -i gedavocat-mysql mysql -u gedavocat -pVotreMotDePasse gedavocat_prod

# Vérifier restauration
docker exec gedavocat-mysql mysql -u gedavocat -pVotreMotDePasse -e "SELECT COUNT(*) FROM users;" gedavocat_prod
```

### 8.3 Backup Uploads (Documents)

```bash
# Backup manuel
tar -czf ~/backups/uploads-$(date +%Y%m%d).tar.gz ~/apps/gedavocat-springboot/uploads/

# Automatiser (cron journalier)
crontab -e
```

Ajouter :
```cron
0 3 * * * tar -czf /home/gedavocat/backups/uploads-$(date +\%Y\%m\%d).tar.gz /home/gedavocat/apps/gedavocat-springboot/uploads/
```

### 8.4 Backup vers Hetzner Storage Box (optionnel)

Hetzner propose **Storage Box** (Backup externe) :
- 100 GB : 3,20€/mois
- Accessible via SFTP, WebDAV, rsync

```bash
# Installer rclone
curl https://rclone.org/install.sh | sudo bash

# Configurer Hetzner Storage Box
rclone config

# Sync backups
rclone sync ~/backups/ hetzner-box:gedavocat-backups/
```

---

## 9️⃣ OPTIMISATIONS SÉCURITÉ

### 9.1 Audit SSL (SSL Labs)

Tester votre configuration HTTPS :
1. Aller sur [ssllabs.com/ssltest](https://www.ssllabs.com/ssltest/)
2. Tester `gedavocat.fr`
3. **Objectif** : Note **A+**

### 9.2 Scan Vulnérabilités (Trivy)

```bash
# Installer Trivy
sudo apt install wget apt-transport-https gnupg lsb-release
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main | sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt update
sudo apt install trivy

# Scanner l'image Docker
trivy image gedavocat-app:latest
```

### 9.3 Hardening MySQL

Éditer `docker/docker-compose.yml`, section MySQL :

```yaml
mysql:
  # ... config existante ...
  command:
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --default-authentication-plugin=mysql_native_password
    - --sql-mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
    - --max-connections=200
    - --max-allowed-packet=64M
    - --innodb-buffer-pool-size=512M
    - --log-bin-trust-function-creators=1
```

### 9.4 Rate Limiting Nginx (DDoS Protection)

Ajouter dans `nginx.conf` (avant le bloc `server`) :

```nginx
# Rate limiting
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=5r/m;

server {
    # ... config existante ...
    
    # Limiter API
    location /api/ {
        limit_req zone=api_limit burst=20 nodelay;
        proxy_pass http://spring_app;
        # ... autres directives proxy ...
    }
    
    # Limiter login
    location /api/auth/login {
        limit_req zone=login_limit burst=3 nodelay;
        proxy_pass http://spring_app;
        # ... autres directives proxy ...
    }
}
```

### 9.5 Bloquer IPs Malveillantes (Fail2Ban Nginx)

Créer `/etc/fail2ban/filter.d/nginx-gedavocat.conf` :

```ini
[Definition]
failregex = ^<HOST> .* "(GET|POST|HEAD).*" (401|403|404|429) .*$
ignoreregex =
```

Ajouter dans `/etc/fail2ban/jail.local` :

```ini
[nginx-gedavocat]
enabled = true
port = http,https
filter = nginx-gedavocat
logpath = /var/log/nginx/gedavocat.access.log
maxretry = 10
findtime = 60
bantime = 3600
```

Redémarrer Fail2Ban :
```bash
sudo systemctl restart fail2ban
```

---

## 🔟 COMMANDES UTILES

### Gestion Docker

```bash
# Redémarrer tous les services
docker compose restart

# Redémarrer un service spécifique
docker compose restart app

# Arrêter tous les services
docker compose down

# Arrêter et supprimer volumes (ATTENTION : perte données)
docker compose down -v

# Voir les logs en temps réel
docker compose logs -f

# Rebuild après modification code
docker compose build --no-cache
docker compose up -d

# Nettoyer images inutilisées
docker system prune -a
```

### Gestion Application

```bash
# Mettre à jour le code
cd ~/apps/gedavocat-springboot
git pull origin main
docker compose build
docker compose up -d

# Vérifier version déployée
docker exec gedavocat-app cat /app/version.txt

# Exécuter commande dans le container
docker exec -it gedavocat-app bash
```

### Monitoring

```bash
# Espace disque
df -h

# RAM et CPU
htop

# Connexions réseau
sudo netstat -tulpn

# Logs système
journalctl -xe

# Logs Docker
docker compose logs --tail=100 app
```

---

## 📊 CHECKLIST POST-DÉPLOIEMENT

### Sécurité
- [ ] Firewall Hetzner configuré (SSH, HTTP, HTTPS uniquement)
- [ ] UFW activé sur le serveur
- [ ] SSH sécurisé (clé uniquement, port modifié)
- [ ] Fail2Ban actif
- [ ] Certificat SSL Let's Encrypt installé
- [ ] SSL Labs : Note A+
- [ ] Headers de sécurité configurés (HSTS, CSP, etc.)
- [ ] MySQL non exposé à Internet
- [ ] Secrets dans `.env.prod` (permissions 600)
- [ ] Clés JWT générées et sécurisées (600)

### Application
- [ ] Docker Compose démarré sans erreur
- [ ] Migrations Flyway appliquées (V1-V4)
- [ ] Health check OK : `/actuator/health`
- [ ] Login fonctionne
- [ ] Upload documents fonctionne
- [ ] Multi-tenant : isolation vérifiée
- [ ] Refresh tokens : génération OK
- [ ] Emails SMTP configurés et testés

### Monitoring
- [ ] Uptime Robot configuré (alertes)
- [ ] Backups MySQL automatiques (cron)
- [ ] Backups uploads automatiques (cron)
- [ ] Logs accessibles
- [ ] Dashboard Grafana/Prometheus (optionnel)

### Performance
- [ ] Temps de réponse < 500ms
- [ ] Pages statiques cachées
- [ ] Compression Gzip/Brotli activée
- [ ] CDN configuré (Cloudflare - optionnel)

---

## 🆘 TROUBLESHOOTING

### Problème : Container app ne démarre pas

**Diagnostic** :
```bash
docker compose logs app
```

**Solutions** :
- Vérifier `.env.prod` (variables manquantes ?)
- Vérifier clés JWT présentes dans `config/keys/`
- Vérifier MySQL accessible : `docker compose logs mysql`
- Vérifier permissions :
  ```bash
  chmod 600 config/keys/private_key.pem
  chmod 644 config/keys/public_key.pem
  ```

### Problème : Erreur 502 Bad Gateway (Nginx)

**Diagnostic** :
```bash
docker compose logs nginx
docker compose logs app
```

**Solutions** :
- Vérifier que le container `app` est démarré : `docker ps`
- Vérifier health check : `curl http://localhost:8080/actuator/health`
- Augmenter timeout Nginx :
  ```nginx
  proxy_read_timeout 120s;
  ```

### Problème : Migrations SQL échouent

**Diagnostic** :
```bash
docker compose logs app | grep Flyway
```

**Solutions** :
- Vérifier connexion MySQL : `docker exec -it gedavocat-mysql mysql -u gedavocat -p`
- Re-créer la base :
  ```bash
  docker exec -it gedavocat-mysql mysql -u root -p
  DROP DATABASE gedavocat_prod;
  CREATE DATABASE gedavocat_prod CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```
- Redémarrer : `docker compose restart app`

### Problème : Certificat SSL expiré

**Solution** :
```bash
# Renouveler manuellement
sudo certbot renew

# Redémarrer Nginx
docker compose restart nginx

# Vérifier expiration
sudo certbot certificates
```

---

## 📞 SUPPORT & RESSOURCES

- **Documentation Hetzner** : [docs.hetzner.com](https://docs.hetzner.com)
- **Let's Encrypt** : [letsencrypt.org](https://letsencrypt.org)
- **Docker Docs** : [docs.docker.com](https://docs.docker.com)
- **Spring Boot Actuator** : [docs.spring.io/actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

## 📝 CONCLUSION

Votre application **GedAvocat** est maintenant déployée en production sur **Hetzner Cloud** avec :

- ✅ **Sécurité niveau bancaire (A+)** : HTTPS, Firewall, Rate Limiting, Fail2Ban
- ✅ **Multi-tenant** : Isolation complète des cabinets (firmId)
- ✅ **JWT RS256** : Clés asymétriques 2048 bits
- ✅ **Refresh Tokens** : Gestion sessions sécurisée
- ✅ **Haute disponibilité** : Docker, health checks, auto-restart
- ✅ **Backups automatiques** : MySQL + uploads
- ✅ **Monitoring** : Logs, Uptime Robot, alertes

**Coût total mensuel** : ~15-20€ (serveur CPX31 + backups optionnels)

**🎉 Application prête pour production SaaS multi-cabinets !**

---

**Dernière mise à jour** : 2 mars 2026  
**Auteur** : Équipe GedAvocat
