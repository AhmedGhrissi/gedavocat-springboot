# Guide de démarrage MySQL Docker

## 1. Démarrer Docker Desktop
- Ouvrir Docker Desktop depuis le menu Démarrer
- Attendre que l'icône Docker dans la barre des tâches soit verte/stable

## 2. Démarrer MySQL
```powershell
cd docker
docker-compose up -d mysql
```

## 3. Vérifier que MySQL est démarré
```powershell
# Voir les conteneurs actifs
docker ps

# Voir les logs MySQL
docker-compose logs mysql

# Vérifier le statut de santé (healthcheck)
docker inspect docavocat-mysql --format='{{.State.Health.Status}}'
```

## 4. Tester la connexion
```powershell
# Depuis le host (port 3307)
docker exec -it docavocat-mysql mysql -u doc_avocat -p'DocAvocat2026!DevDB' -e "SHOW DATABASES;"

# Ou se connecter interactivement
docker exec -it docavocat-mysql mysql -u doc_avocat -p'DocAvocat2026!DevDB'
```

## 5. Identifiants de connexion

### Pour VS Code Database Extension :
- **Host** : `localhost`
- **Port** : `3307`
- **Database** : `doc_avocat`
- **Username** : `doc_avocat`
- **Password** : `DocAvocat2026!DevDB`

### Pour Spring Boot (application.properties) :
```properties
spring.datasource.url=jdbc:mysql://localhost:3307/doc_avocat?useSSL=false&serverTimezone=Europe/Paris
spring.datasource.username=doc_avocat
spring.datasource.password=DocAvocat2026!DevDB
```

## 6. Commandes utiles

```powershell
# Arrêter MySQL
docker-compose stop mysql

# Redémarrer MySQL
docker-compose restart mysql

# Voir les logs en temps réel
docker-compose logs -f mysql

# Supprimer complètement MySQL et ses données (⚠️ ATTENTION)
docker-compose down -v
```

## 7. En cas de problème

### Réinitialiser complètement la base :
```powershell
# Arrêter et supprimer
docker-compose down -v

# Redémarrer
docker-compose up -d mysql

# Attendre 30 secondes que MySQL s'initialise
Start-Sleep -Seconds 30

# Vérifier
docker-compose logs mysql
```

### Port 3307 déjà utilisé :
```powershell
# Voir ce qui utilise le port
netstat -ano | findstr :3307

# Modifier le port dans docker-compose.yml si nécessaire
```
