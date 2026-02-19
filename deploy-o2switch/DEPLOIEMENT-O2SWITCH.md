# 🚀 GUIDE DE DÉPLOIEMENT SUR O2SWITCH

## 📋 Prérequis O2Switch

Votre hébergement O2Switch doit avoir :
- ✅ Accès SSH activé
- ✅ Java 17 ou supérieur
- ✅ MySQL accessible
- ✅ Accès FTP/SFTP

## 🔧 Étape 1 : Préparation locale

### 1.1 Configuration de la base de données

Modifiez le fichier `application-o2switch.properties` avec vos identifiants MySQL O2Switch :

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/VOTRE_BASE
spring.datasource.username=VOTRE_USER
spring.datasource.password=VOTRE_PASSWORD
```

### 1.2 Génération d'une clé JWT sécurisée

```bash
# Générer une clé secrète de 64 caractères
openssl rand -base64 64
```

Copiez le résultat dans `application-o2switch.properties` :

```properties
jwt.secret=VOTRE_CLE_GENEREE_ICI
```

### 1.3 Compilation du projet

```bash
# Nettoyer et compiler
mvn clean package -DskipTests

# Le fichier JAR sera dans target/gedavocat-app-1.0.0.jar
```

## 🌐 Étape 2 : Création de la base de données sur O2Switch

### 2.1 Connexion à phpMyAdmin

1. Connectez-vous à votre compte O2Switch
2. Allez dans **Bases de données MySQL**
3. Créez une nouvelle base de données (ex: `gedavocat_db`)
4. Notez les identifiants créés

### 2.2 Initialisation de la base

Via phpMyAdmin, importez le fichier `database-init.sql` ou exécutez :

```sql
CREATE DATABASE gedavocat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Les tables seront créées automatiquement par Hibernate au premier démarrage.

## 📤 Étape 3 : Upload des fichiers

### 3.1 Connexion SSH

```bash
ssh votre_user@ssh.o2switch.net
```

### 3.2 Création de la structure de répertoires

```bash
# Créer les répertoires nécessaires
mkdir -p ~/gedavocat
mkdir -p ~/gedavocat/logs
mkdir -p ~/uploads/documents
mkdir -p ~/uploads/signatures

# Donner les permissions
chmod 755 ~/gedavocat
chmod 755 ~/uploads
chmod 755 ~/uploads/documents
chmod 755 ~/uploads/signatures
```

### 3.3 Upload via FTP/SFTP

Utilisez FileZilla ou un client SFTP pour uploader :

```
Fichiers locaux → Destination sur O2Switch

target/gedavocat-app-1.0.0.jar → ~/gedavocat/app.jar
application-o2switch.properties → ~/gedavocat/application.properties
```

## 🎯 Étape 4 : Configuration du service

### 4.1 Créer un script de démarrage

```bash
cd ~/gedavocat
nano start.sh
```

Contenu du fichier `start.sh` :

```bash
#!/bin/bash

# Variables
APP_DIR="$HOME/gedavocat"
JAR_FILE="$APP_DIR/app.jar"
LOG_FILE="$APP_DIR/logs/gedavocat.log"
PID_FILE="$APP_DIR/app.pid"

# Démarrer l'application
nohup java -jar $JAR_FILE \
  --spring.config.location=file:$APP_DIR/application.properties \
  >> $LOG_FILE 2>&1 &

# Sauvegarder le PID
echo $! > $PID_FILE

echo "GED Avocat démarré avec le PID $(cat $PID_FILE)"
```

Rendre le script exécutable :

```bash
chmod +x start.sh
```

### 4.2 Créer un script d'arrêt

```bash
nano stop.sh
```

Contenu du fichier `stop.sh` :

```bash
#!/bin/bash

PID_FILE="$HOME/gedavocat/app.pid"

if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    kill $PID
    rm $PID_FILE
    echo "GED Avocat arrêté (PID: $PID)"
else
    echo "Fichier PID non trouvé"
fi
```

Rendre le script exécutable :

```bash
chmod +x stop.sh
```

### 4.3 Créer un script de redémarrage

```bash
nano restart.sh
```

Contenu :

```bash
#!/bin/bash
./stop.sh
sleep 2
./start.sh
```

Rendre exécutable :

```bash
chmod +x restart.sh
```

## ▶️ Étape 5 : Démarrage de l'application

```bash
cd ~/gedavocat
./start.sh
```

Vérifier les logs :

```bash
tail -f logs/gedavocat.log
```

## 🌍 Étape 6 : Configuration du reverse proxy (optionnel)

### 6.1 Créer un fichier .htaccess

Dans votre dossier `public_html` :

```apache
# Activer le module proxy
<IfModule mod_proxy.c>
    ProxyPreserveHost On
    ProxyPass /gedavocat http://localhost:8080/
    ProxyPassReverse /gedavocat http://localhost:8080/
</IfModule>

# Redirection HTTPS
RewriteEngine On
RewriteCond %{HTTPS} off
RewriteRule ^(.*)$ https://%{HTTP_HOST}%{REQUEST_URI} [L,R=301]
```

### 6.2 Accès à l'application

Votre application sera accessible à :
- **http://votre-domaine.com/gedavocat**
- **https://votre-domaine.com/gedavocat** (recommandé)

## 🔄 Étape 7 : Démarrage automatique

### 7.1 Ajouter au crontab

```bash
crontab -e
```

Ajouter cette ligne pour démarrer l'app au reboot :

```
@reboot /home/VOTRE_USER/gedavocat/start.sh
```

## 📊 Étape 8 : Surveillance

### 8.1 Vérifier que l'application tourne

```bash
ps aux | grep java
```

### 8.2 Consulter les logs

```bash
tail -f ~/gedavocat/logs/gedavocat.log
```

### 8.3 Vérifier l'accès web

```bash
curl http://localhost:8080/
```

## 🔐 Étape 9 : Sécurité

### 9.1 Changez les mots de passe par défaut

Connectez-vous à l'application et changez :
- Le mot de passe admin
- Le mot de passe avocat de test

### 9.2 Configurez SSL/HTTPS

O2Switch fournit gratuitement Let's Encrypt. Activez-le depuis votre cPanel.

### 9.3 Sauvegarde régulière

Configurez des sauvegardes automatiques :

```bash
# Script de backup
nano ~/backup-gedavocat.sh
```

```bash
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$HOME/backups/gedavocat"

mkdir -p $BACKUP_DIR

# Backup MySQL
mysqldump -u VOTRE_USER -p'VOTRE_PASSWORD' VOTRE_BASE > $BACKUP_DIR/db_$DATE.sql

# Backup fichiers
tar -czf $BACKUP_DIR/files_$DATE.tar.gz ~/uploads

# Nettoyer les backups de plus de 30 jours
find $BACKUP_DIR -type f -mtime +30 -delete

echo "Backup terminé: $DATE"
```

Automatiser avec cron :

```bash
crontab -e
# Backup quotidien à 2h du matin
0 2 * * * /home/VOTRE_USER/backup-gedavocat.sh
```

## 🆘 Dépannage

### L'application ne démarre pas

```bash
# Vérifier les logs
tail -n 100 ~/gedavocat/logs/gedavocat.log

# Vérifier la configuration
cat ~/gedavocat/application.properties
```

### Port déjà utilisé

```bash
# Trouver le processus utilisant le port 8080
lsof -i :8080

# Tuer le processus
kill -9 PID
```

### Erreur de connexion MySQL

1. Vérifiez que MySQL est accessible
2. Vérifiez les identifiants dans `application.properties`
3. Vérifiez que la base de données existe

### Manque de mémoire

Modifier `start.sh` pour augmenter la mémoire :

```bash
java -Xmx512m -Xms256m -jar $JAR_FILE ...
```

## 📞 Support O2Switch

Si vous rencontrez des problèmes spécifiques à O2Switch :
- Support : https://www.o2switch.fr/support/
- Documentation : https://faq.o2switch.fr/

## ✅ Checklist finale

- [ ] Base de données créée et configurée
- [ ] Fichier JAR uploadé
- [ ] Configuration `application.properties` mise à jour
- [ ] Répertoires de logs et uploads créés
- [ ] Scripts de démarrage/arrêt créés et testés
- [ ] Application accessible via navigateur
- [ ] Mots de passe par défaut changés
- [ ] HTTPS/SSL activé
- [ ] Sauvegardes configurées
- [ ] Monitoring en place

## 🎉 Félicitations !

Votre application GED Avocat est maintenant déployée sur O2Switch !

Accès : **https://votre-domaine.com/gedavocat**

---

**Pour toute question, consultez le README.md ou contactez le support.**
