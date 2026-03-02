# 🚀 Guide de Déploiement Production - DocAvocat

## Prérequis

- ✅ Docker & Docker Compose installés
- ✅ Maven 3.8+ installé
- ✅ Java 21 installé
- ✅ Port 8080 disponible

## Déploiement Rapide

### 1. Configuration Environnement

```powershell
# Copier le template .env
cp docker/.env.example docker/.env

# Éditer les variables critiques
notepad docker/.env
```

**Variables essentielles à configurer:**

| Variable | Description | Exemple |
|----------|-------------|---------|
| `MYSQL_ROOT_PASSWORD` | Mot de passe root MySQL | `SecureRootPass123!` |
| `MYSQL_PASSWORD` | Mot de passe utilisateur gedavocat | `SecureDbPass456!` |
| `JWT_SECRET` | Secret JWT (base64) | Générer avec `openssl rand -base64 64` |
| `MAIL_HOST` | Serveur SMTP | `smtp.sendgrid.net` |
| `MAIL_USERNAME` | Username SMTP | `apikey` |
| `MAIL_PASSWORD` | Mot de passe/API Key SMTP | `SG.xxx` |
| `YOUSIGN_API_KEY` | API Key YouSign | `ys_xxx` |
| `GRAFANA_PASSWORD` | Mot de passe Grafana | `Grafana123!` |

### 2. Générer JWT Secret

```powershell
# Sous Windows (PowerShell)
$bytes = New-Object byte[] 64
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)

# Sous Linux/Mac
openssl rand -base64 64
```

### 3. Lancer le Déploiement

```powershell
# Déploiement complet (build + tests + docker)
.\deploy-production.ps1

# Skip tests (plus rapide)
.\deploy-production.ps1 -SkipTests

# Skip build (si JAR déjà construit)
.\deploy-production.ps1 -SkipBuild

# Force (ignore warnings)
.\deploy-production.ps1 -Force
```

## Architecture Déployée

```
┌─────────────────────────────────────────────────────────┐
│                    Nginx (Host)                         │
│              SSL + Reverse Proxy                        │
│           https://docavocat.fr → :8080                  │
└────────────────────┬────────────────────────────────────┘
                     │
    ┌────────────────▼─────────────────────┐
    │      Docker Network: docavocat-net   │
    │                                      │
    │  ┌──────────────┐  ┌──────────────┐ │
    │  │ Spring Boot  │  │   MySQL 8    │ │
    │  │  App :8080   │  │   :3306      │ │
    │  └──────┬───────┘  └──────────────┘ │
    │         │                            │
    │  ┌──────▼───────┐  ┌──────────────┐ │
    │  │  Prometheus  │  │   Grafana    │ │
    │  │    :9090     │  │   :3000      │ │
    │  └──────────────┘  └──────────────┘ │
    │                                      │
    │  ┌──────────────┐  ┌──────────────┐ │
    │  │     Loki     │  │   Promtail   │ │
    │  │    :3100     │  │              │ │
    │  └──────────────┘  └──────────────┘ │
    └──────────────────────────────────────┘
```

## Services Exposés

| Service | Port Host | Port Container | Accès |
|---------|-----------|----------------|-------|
| Application | 8080 | 8080 | `http://localhost:8080` |
| MySQL | 3307 | 3306 | `localhost:3307` (local uniquement) |
| Prometheus | 9090 | 9090 | `http://localhost:9090` |
| Grafana | 3000 | 3000 | `http://localhost:3000` |
| Loki | 3100 | 3100 | `http://localhost:3100` |

## Vérification Post-Déploiement

### 1. Health Check

```powershell
# Application
curl http://localhost:8080/actuator/health

# Réponse attendue: {"status":"UP"}
```

### 2. Logs

```powershell
# Logs application
docker logs -f docavocat-app

# Logs MySQL
docker logs docavocat-mysql

# Tous les logs
docker-compose -f docker/docker-compose.yml logs -f
```

### 3. Status Conteneurs

```powershell
docker ps

# Tous doivent être "Up"
# - docavocat-app
# - docavocat-mysql
# - docavocat-prometheus
# - docavocat-grafana
# - docavocat-loki
# - docavocat-promtail
```

## Monitoring

### Grafana

1. Accéder à http://localhost:3000
2. Login: `admin` / `GRAFANA_PASSWORD` (depuis .env)
3. Dashboards disponibles:
   - **GedAvocat Dashboard**: Métriques application (CPU, mémoire, requêtes/s)
   - **Spring Boot Actuator**: Statistiques JVM
   - **MySQL Performance**: Métriques base de données

### Prometheus

1. Accéder à http://localhost:9090
2. Queries exemples:
   ```promql
   # Requêtes HTTP par seconde
   rate(http_server_requests_seconds_count[1m])
   
   # Mémoire JVM utilisée
   jvm_memory_used_bytes{area="heap"}
   
   # Connexions MySQL actives
   mysql_global_status_threads_connected
   ```

## Maintenance

### Backup Base de Données

```powershell
# Backup complet
docker exec docavocat-mysql mysqldump -u gedavocat -p gedavocat > backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql

# Restauration
docker exec -i docavocat-mysql mysql -u gedavocat -p gedavocat < backup.sql
```

### Mise à Jour Application

```powershell
# 1. Rebuild image
.\deploy-production.ps1 -SkipTests

# 2. Redémarrer uniquement l'app (0 downtime si load balancer)
docker-compose -f docker/docker-compose.yml up -d --no-deps app
```

### Redémarrage Services

```powershell
# Redémarrer tous les services
docker-compose -f docker/docker-compose.yml restart

# Redémarrer app uniquement
docker-compose -f docker/docker-compose.yml restart app

# Redémarrer MySQL
docker-compose -f docker/docker-compose.yml restart mysql
```

### Arrêt Propre

```powershell
# Arrêt tous services
docker-compose -f docker/docker-compose.yml down

# Arrêt + suppression volumes (⚠️ PERTE DE DONNÉES)
docker-compose -f docker/docker-compose.yml down -v
```

## Troubleshooting

### Application ne démarre pas

```powershell
# Vérifier logs
docker logs docavocat-app

# Erreurs communes:
# - MySQL connection refused → Attendre healthcheck MySQL
# - Port 8080 already in use → Tuer processus existant
# - JWT keys not found → Vérifier volume /opt/gedavocat/config/keys
```

### MySQL connection refused

```powershell
# Vérifier healthcheck
docker inspect docavocat-mysql | grep -A 10 Health

# Redémarrer MySQL
docker-compose -f docker/docker-compose.yml restart mysql
```

### Grafana login impossible

```powershell
# Reset mot de passe admin
docker exec -it docavocat-grafana grafana-cli admin reset-admin-password NewPassword123!
```

## Sécurité Production

### ✅ Checklist

- [ ] Changer tous les mots de passe par défaut
- [ ] Générer un nouveau JWT_SECRET
- [ ] Configurer SSL/TLS (Let's Encrypt + Nginx)
- [ ] Activer le firewall (UFW sur Linux)
- [ ] Configurer backups automatiques
- [ ] Monitoring/alerting opérationnel
- [ ] Logs centralisés (Loki/Grafana)
- [ ] Rate limiting (Nginx)
- [ ] WAF activé (ModSecurity)

### Firewall (Linux/Hetzner)

```bash
# Ouvrir uniquement ports nécessaires
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # HTTP
ufw allow 443/tcp   # HTTPS
ufw enable
```

### SSL/TLS (Let's Encrypt)

```bash
# Installer Certbot
apt install certbot python3-certbot-nginx

# Obtenir certificat
certbot --nginx -d docavocat.fr -d www.docavocat.fr

# Auto-renouvellement (cron)
0 0 * * * certbot renew --quiet
```

## Performance

### Tuning MySQL

Éditer `docker/docker-compose.yml`:

```yaml
command: >
  --max_connections=500
  --innodb_buffer_pool_size=1G
  --innodb_log_file_size=256M
  --query_cache_size=0
  --query_cache_type=0
```

### Tuning JVM

Ajouter dans `docker/docker-compose.yml` → `app.environment`:

```yaml
JAVA_OPTS: >-
  -Xms512m
  -Xmx2g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
```

## Support

- **Documentation**: `/docs`
- **Logs**: `docker logs -f docavocat-app`
- **Monitoring**: http://localhost:3000
- **Health**: http://localhost:8080/actuator/health

---

**Version**: 1.0.0  
**Date**: Mars 2026  
**Contact**: support@docavocat.fr
