# 🔍 GedAvocat - Configuration Monitoring Production

## 📊 Vue d'ensemble

Le monitoring de GedAvocat repose sur la stack moderne **Prometheus + Grafana** :

- **Prometheus** : Collecte des métriques time-series (CPU, RAM, requêtes HTTP, latence, errors)
- **Grafana** : Dashboards visuels interactifs avec alertes
- **Node Exporter** : Métriques système (CPU, RAM, disque, réseau)
- **MySQL Exporter** : Métriques base de données (connexions, slow queries, deadlocks)
- **cAdvisor** : Métriques containers Docker (CPU, RAM, network par container)
- **Spring Boot Actuator** : Métriques applicatives (endpoints HTTP, JVM, GC, threads)

---

## 🚀 Installation rapide

### 1️⃣ Activer les métriques Prometheus dans Spring Boot

✅ **Déjà fait** : Les dépendances `spring-boot-starter-actuator` + `micrometer-registry-prometheus` ont été ajoutées au `pom.xml`.

```bash
# Compiler l'application avec les nouvelles dépendances
mvn clean install -DskipTests
```

### 2️⃣ Démarrer la stack de monitoring Docker

```bash
cd docker/
docker-compose up -d prometheus grafana node-exporter mysql-exporter cadvisor
```

### 3️⃣ Accéder aux interfaces

#### **Prometheus** (métriques brutes)
```bash
# URL: http://localhost:9090
# Query examples:
# - rate(http_server_requests_seconds_count[5m])
# - mysql_global_status_threads_connected
# - node_cpu_seconds_total{mode="idle"}
```

#### **Grafana** (dashboards visuels)
```bash
# URL: http://localhost:3000
# Login: admin
# Password: (défini dans .env → GRAFANA_PASSWORD)
```

---

## 📈 Dashboards Grafana

### Dashboard principal : **GedAvocat - Production**

Accessible via : **Dashboards → GedAvocat - Production Dashboard**

#### Panels inclus :

1. **Application Status**
   - Statut UP/DOWN de l'application
   - Couleur verte = opérationnel, rouge = down

2. **HTTP Requests Rate**
   - Nombre de requêtes HTTP par seconde
   - Graphique par endpoint (`GET /api/cases`, `POST /api/auth/login`, etc.)

3. **Response Time (P50/P95/P99)**
   - Temps de réponse percentiles :
     - **P50** : 50% des requêtes plus rapides que ce temps
     - **P95** : 95% des requêtes plus rapides que ce temps (SLA)
     - **P99** : 99% des requêtes plus rapides que ce temps
   - ⚠️ **Alerte si P95 > 1 seconde**

4. **System Resources**
   - CPU usage (%)
   - Memory usage (%)
   - ⚠️ **Alerte si CPU > 80% ou RAM > 85%**

5. **Database Metrics** (à ajouter manuellement)
   - Connexions MySQL actives
   - Slow queries (> 500ms)
   - Deadlocks

6. **Docker Containers** (à ajouter manuellement)
   - CPU par container (`gedavocat-app`, `mysql`, etc.)
   - RAM par container
   - Network I/O

---

## 🔔 Alertes Prometheus

### Alertes critiques (CRITICAL)

| Alerte | Déclencheur | Actions |
|--------|-------------|---------|
| **ApplicationDown** | Application DOWN pendant > 1 min | Email + SMS admin |
| **MySQLDown** | MySQL DOWN pendant > 1 min | Email + SMS admin |
| **ContainerDown** | Container Docker DOWN | Email admin |

### Alertes warning (WARNING)

| Alerte | Déclencheur | Actions |
|--------|-------------|---------|
| **HighErrorRate** | Erreurs HTTP 5xx > 5% pendant 5 min | Email admin |
| **SlowResponseTime** | P95 > 1 seconde pendant 5 min | Email admin |
| **MySQLTooManyConnections** | Connexions > 150 (max 200) | Email admin |
| **MySQLSlowQueries** | Slow queries > 1/s pendant 5 min | Email admin |
| **HighCPUUsage** | CPU > 80% pendant 5 min | Email admin |
| **HighMemoryUsage** | RAM > 85% pendant 5 min | Email admin |
| **DiskSpaceLow** | Disque < 15% libre | Email admin |
| **SSLCertificateExpiringSoon** | Certificat SSL expire dans < 7 jours | Email admin |

---

## 📊 Métriques Spring Boot Actuator

### Endpoints exposés

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Statut santé (UP/DOWN) |
| `/actuator/prometheus` | Métriques Prometheus (format texte) |
| `/actuator/metrics` | Liste des métriques disponibles |
| `/actuator/info` | Informations application (version, git commit) |

### Métriques collectées automatiquement

#### **HTTP Server Requests**
- `http_server_requests_seconds_count` : Nombre de requêtes
- `http_server_requests_seconds_sum` : Temps total de traitement
- `http_server_requests_seconds_bucket` : Histogramme (distribution des temps de réponse)

**Labels** :
- `uri` : Endpoint (ex: `/api/cases`)
- `method` : HTTP method (GET, POST, PUT, DELETE)
- `status` : HTTP status code (200, 404, 500)
- `exception` : Exception Java (si erreur)

#### **JVM Metrics**
- `jvm_memory_used_bytes` : Mémoire JVM utilisée
- `jvm_memory_max_bytes` : Mémoire JVM maximale
- `jvm_gc_pause_seconds` : Temps de pause Garbage Collector
- `jvm_threads_live` : Nombre de threads Java actifs
- `jvm_threads_daemon` : Nombre de threads daemon

#### **Database Metrics** (via HikariCP)
- `hikaricp_connections_active` : Connexions actives à MySQL
- `hikaricp_connections_idle` : Connexions inactives (pool)
- `hikaricp_connections_pending` : Connexions en attente
- `hikaricp_connections_timeout_total` : Timeout de connexions

---

## 🐳 Exporters Docker Compose

### Configuration des services

```yaml
# docker/docker-compose.yml (extrait)

prometheus:
  image: prom/prometheus:v2.54.1
  ports:
    - "127.0.0.1:9090:9090"  # Prometheus UI
  volumes:
    - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    - prometheus_data:/prometheus

node-exporter:
  image: prom/node-exporter:v1.8.2
  ports:
    - "127.0.0.1:9100:9100"  # Metrics HTTP
  volumes:
    - /:/host:ro,rslave  # Accès lecture système hôte

mysql-exporter:
  image: prom/mysqld-exporter:v0.15.1
  ports:
    - "127.0.0.1:9104:9104"  # Metrics HTTP
  environment:
    DATA_SOURCE_NAME: "gedavocat:${MYSQL_PASSWORD}@(mysql:3306)/"

cadvisor:
  image: gcr.io/cadvisor/cadvisor:v0.49.1
  ports:
    - "127.0.0.1:8081:8080"  # cAdvisor UI + metrics
  privileged: true
  volumes:
    - /var/lib/docker:/var/lib/docker:ro

grafana:
  image: grafana/grafana:11.3.0
  ports:
    - "127.0.0.1:3000:3000"  # Grafana UI
  volumes:
    - ./grafana/provisioning:/etc/grafana/provisioning:ro
    - ./grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
```

---

## 🔐 Sécurité Monitoring

### Accès sécurisé (Production Hetzner)

**TOUJOURS** accéder via tunnel SSH (ports non exposés publiquement) :

```bash
# Tunnel SSH pour Grafana
ssh -L 3000:localhost:3000 root@docavocat.fr

# Tunnel SSH pour Prometheus
ssh -L 9090:localhost:9090 root@docavocat.fr

# Ouvrir dans navigateur
http://localhost:3000  # Grafana
http://localhost:9090  # Prometheus
```

### Authentification Grafana

```bash
# Créer un utilisateur admin sécurisé
# Ajouter dans .env :
GRAFANA_PASSWORD=VotreMotDePasseSecurise123!

# Connexion Grafana :
# Username: admin
# Password: GRAFANA_PASSWORD (depuis .env)
```

---

## 📂 Structure fichiers monitoring

```
docker/
├── prometheus/
│   ├── prometheus.yml         # Config Prometheus (scrape configs)
│   └── alerts.yml             # Règles d'alertes
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml # Datasource Prometheus auto-provisionnée
│   │   └── dashboards/
│   │       └── dashboards.yml # Auto-provisioning dashboards
│   └── dashboards/
│       └── gedavocat-dashboard.json  # Dashboard principal
└── docker-compose.yml         # Services Prometheus + Grafana + Exporters
```

---

## 🛠️ Troubleshooting

### ❌ Prometheus ne collecte pas les métriques Spring Boot

**Symptôme** : Target `gedavocat-app` DOWN dans Prometheus Status → Targets

**Solution** :
```bash
# Vérifier endpoint Actuator exposé :
curl http://localhost:8080/actuator/prometheus

# Si erreur 404, vérifier application.properties :
management.endpoint.prometheus.enabled=true
management.endpoints.web.exposure.include=prometheus

# Redémarrer app :
docker-compose restart app
```

### ❌ Grafana : "No data" dans les panels

**Symptôme** : Dashboard vide, message "No data"

**Solution** :
1. Vérifier datasource Prometheus :
   - Grafana → Configuration → Data sources → Prometheus
   - URL doit être `http://prometheus:9090`
   - Cliquer "Save & Test" → ✅ Data source is working

2. Vérifier requêtes PromQL dans panels :
   - Aller dans panel → Edit → Query
   - Exécuter manuellement la requête dans Prometheus UI

3. Vérifier labels métriques :
   - Dans Prometheus : `http_server_requests_seconds_count{job="gedavocat-app"}`
   - Si aucune donnée, app ne publie pas de métriques → redémarrer app

### ❌ MySQL Exporter : connexion refusée

**Symptôme** : Target `mysql` DOWN dans Prometheus

**Solution** :
```bash
# Vérifier password MySQL dans docker-compose.yml :
DATA_SOURCE_NAME: "gedavocat:${MYSQL_PASSWORD}@(mysql:3306)/"

# Vérifier .env contient MYSQL_PASSWORD :
echo $MYSQL_PASSWORD

# Tester connexion manuellement :
docker exec -it docavocat-mysql mysql -u gedavocat -p${MYSQL_PASSWORD} -e "SHOW STATUS;"

# Redémarrer exporter :
docker-compose restart mysql-exporter
```

---

## 📝 Ajout de métriques custom

### Exemple : Tracker nombre de dossiers par cabinet

```java
// src/main/java/com/gedavocat/service/CaseService.java

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@Service
public class CaseService {

    private final MeterRegistry meterRegistry;
    private final Counter casesCreatedCounter;

    public CaseService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.casesCreatedCounter = Counter.builder("gedavocat_cases_created_total")
                .description("Total number of cases created")
                .tag("application", "gedavocat")
                .register(meterRegistry);
    }

    public Case createCase(CaseDTO dto) {
        Case newCase = caseRepository.save(caseEntity);
        casesCreatedCounter.increment(); // Incrémenter compteur
        return newCase;
    }
}
```

**Query Prometheus** :
```promql
# Taux de création de dossiers (par minute)
rate(gedavocat_cases_created_total[5m]) * 60
```

---

## 🎯 Checklist déploiement monitoring

- [ ] **Dépendances Maven** : `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- [ ] **application.properties** : Endpoints Actuator activés (`management.endpoints.web.exposure.include=prometheus`)
- [ ] **Docker Compose** : Services `prometheus`, `grafana`, `node-exporter`, `mysql-exporter`, `cadvisor` démarrés
- [ ] **Grafana Datasource** : Prometheus connecté (`http://prometheus:9090`)
- [ ] **Dashboards Grafana** : Dashboard "GedAvocat - Production" importé
- [ ] **Alertes Prometheus** : Fichier `alerts.yml` chargé
- [ ] **Tunnel SSH** : Accès sécurisé à Grafana via `ssh -L 3000:localhost:3000`
- [ ] **Credentials** : `.env` contient `GRAFANA_PASSWORD`, `MYSQL_PASSWORD`
- [ ] **Test métriques** : `curl http://localhost:8080/actuator/prometheus` retourne des données
- [ ] **Test alertes** : Simuler erreur (arrêter container) → vérifier alerte déclenchée

---

## 📞 Support

**En cas de problème** :
1. Vérifier logs Prometheus : `docker logs docavocat-prometheus`
2. Vérifier logs Grafana : `docker logs docavocat-grafana`
3. Vérifier application publie métriques : `curl localhost:8080/actuator/prometheus`
4. Consulter Prometheus Targets : `http://localhost:9090/targets`

---

✅ **Configuration complète — Monitoring production opérationnel**
