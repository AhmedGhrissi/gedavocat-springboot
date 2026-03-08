# 🚀 Configuration Base de Données - Production/Distance

## ⚠️ **PROBLÈME IDENTIFIÉ**

La base de données distante **ne sera PAS créée automatiquement** car :
- `spring.jpa.hibernate.ddl-auto=validate` (ne crée pas les tables)
- Pas de Flyway configuré
- Pas de script d'initialisation

---

## ✅ **SOLUTIONS DISPONIBLES**

### **Solution 1 : Automatique (Recommandée pour dev/test)**

Le profil `dev` est configuré avec `ddl-auto=update` :
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
→ Les tables seront créées automatiquement

### **Solution 2 : Script d'initialisation Docker (Pour production)**

#### **Étape 1 : Générer le schéma**
```powershell
# 1. Démarrez l'app en mode dev (crée les tables)
.\start-demo.ps1
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 2. Exportez le schéma
.\scripts\export-schema.ps1
```

#### **Étape 2 : Configurer Docker**
Décommentez dans `docker/docker-compose.yml` (ligne ~35) :
```yaml
volumes:
  - mysql_data:/var/lib/mysql
  - ./init/01-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro  # ← Décommenter
```

#### **Étape 3 : Réinitialiser MySQL**
```powershell
cd docker
docker-compose down -v
docker-compose up -d mysql
```

→ Le schéma sera automatiquement chargé

---

## 📋 **POUR VOTRE PRÉSENTATION**

### **En local (DEV) :**
```powershell
.\start-demo.ps1
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
→ Tout fonctionne, les tables se créent automatiquement

### **En production/distance :**
Il faut SOIT :
1. **Exporter le schéma** avec `.\scripts\export-schema.ps1`
2. **Configurer Docker** pour charger le schéma au démarrage
3. **OU** utiliser Flyway (migration plus professionnelle)

---

## 🔄 **Option Flyway (Recommandée pour production)**

### Configuration :

1. **Ajouter la dépendance** dans `pom.xml` :
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

2. **Créer le dossier** :
```
src/main/resources/db/migration/
```

3. **Déplacer les scripts SQL** :
```
V1__initial_schema.sql
V2__add_avocat_admin_role.sql
V3__rename_database.sql
```

4. **Configuration** dans `application.properties` :
```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

---

## 📊 **État actuel des fichiers**

### **Nouveaux scripts créés :**
- `scripts/export-schema.ps1` - Exporte le schéma depuis MySQL
- `scripts/generate-schema.ps1` - Génère le schéma depuis JPA
- `scripts/generate-schema.sh` - Version Linux
- `start-demo.ps1` - Démarrage automatique pour démo
- `check-mysql.ps1` - Vérification MySQL
- `GUIDE-DEMO.md` - Guide de démarrage rapide
- `DOCKER-MYSQL-SETUP.md` - Configuration Docker

### **Modifications :**
- `application-dev.properties` - Mode `update` pour auto-création
- `FirmManagementController.java` - Warnings corrigés
- `.env` - Nouveaux identifiants dev
- `docker/.env` - Identifiants Docker

---

## ⚡ **ACTION IMMÉDIATE pour votre présentation**

```powershell
# Commiter et pusher
git add .
git commit -m "fix: Configuration BDD et scripts de démarrage pour démo"
git push

# Démarrer pour test
.\start-demo.ps1
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 🎯 **Résumé**

| Mode | Initialisation BDD | Command |
|------|-------------------|---------|
| **DEV Local** | ✅ Automatique | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` |
| **Prod Docker (première fois)** | ⚠️ Manuel | Exporter schéma + configurer init script |
| **Prod avec Flyway** | ✅ Automatique | Ajout dépendance + migrations |

**Pour demain : Utilisez le mode DEV** ✅
