# 🚀 Guide de Déploiement Complet sur O2Switch - GED Avocat

## 📋 Pré-requis

### Sur votre machine locale
- ✅ Java JDK 17+
- ✅ Maven 3.6+
- ✅ Git
- ✅ Client SSH (PuTTY sous Windows ou Terminal sous Mac/Linux)

### Sur O2Switch
- ✅ Accès cPanel
- ✅ Accès SSH activé
- ✅ Base de données MySQL créée
- ✅ Nom de domaine configuré avec SSL (Let's Encrypt)

---

## 🔧 ÉTAPE 1 : Préparation de l'application

### 1.1 Compiler l'application
```bash
cd C:\Users\el_ch\git\gedavocat-springboot
mvn clean package -DskipTests
```

Cela génère le fichier `target/gedavocat-app-1.0.0.jar`

### 1.2 Préparer le fichier de configuration
1. Copiez `application-o2switch.properties` 
2. Renommez-le en `application.properties`
3. Éditez et remplacez **TOUTES** les valeurs suivantes :

```properties
# Base de données (obtenir depuis cPanel > MySQL)
spring.datasource.url=jdbc:mysql://localhost:3306/votre_base_mysql
spring.datasource.username=votre_user_mysql
spring.datasource.password=votre_password_mysql

# Chemins (remplacer VOTRE_UTILISATEUR par votre login SSH)
app.upload.dir=/home/votre_login/gedavocat/uploads/documents
app.signature.dir=/home/votre_login/gedavocat/uploads/signatures

# JWT Secret (GÉNÉRER UNE NOUVELLE CLÉ SÉCURISÉE)
# Utilisez : openssl rand -base64 64
jwt.secret=VOTRE_CLE_SECRETE_UNIQUE_64_CARACTERES_MINIMUM

# Email (obtenir depuis cPanel > Email Accounts)
spring.mail.host=smtp.votre-domaine.com
spring.mail.username=noreply@votre-domaine.com
spring.mail.password=votre_password_email
app.base-url=https://votre-domaine.com
```

---

## 🗄️ ÉTAPE 2 : Configuration de la base de données MySQL

### 2.1 Via cPanel
1. Connectez-vous à **cPanel**
2. Allez dans **MySQL® Databases**
3. Créez une nouvelle base :
   - Nom : `gedavocat_prod` (ou autre)
4. Créez un utilisateur MySQL :
   - Nom : `gedavocat_user`
   - Mot de passe : (générez un mot de passe fort)
5. Associez l'utilisateur à la base avec **TOUS LES PRIVILÈGES**

### 2.2 Importer le schéma
Depuis votre machine locale, via SSH :
```bash
# Se connecter en SSH
ssh votre_login@votre-domaine.com

# Importer le schéma
mysql -u gedavocat_user -p gedavocat_prod < database-init.sql
```

---

## 📁 ÉTAPE 3 : Création de la structure de répertoires

Connectez-vous en SSH et créez les répertoires :

```bash
# Se connecter
ssh votre_login@votre-domaine.com

# Créer la structure
mkdir -p ~/gedavocat/{uploads/documents,uploads/signatures,logs,temp,backups}
mkdir -p ~/gedavocat/app

# Définir les permissions
chmod 755 ~/gedavocat
chmod 700 ~/gedavocat/uploads
chmod 700 ~/gedavocat/logs
chmod 700 ~/gedavocat/backups
chmod 755 ~/gedavocat/app
```

---

## 📤 ÉTAPE 4 : Upload des fichiers

### 4.1 Via SCP (recommandé)
Depuis votre machine Windows :
```powershell
# Uploader le JAR
scp target/gedavocat-app-1.0.0.jar votre_login@votre-domaine.com:~/gedavocat/app/

# Uploader la configuration
scp application.properties votre_login@votre-domaine.com:~/gedavocat/app/

# Uploader les scripts
scp scripts/*.sh votre_login@votre-domaine.com:~/gedavocat/scripts/
```

### 4.2 Via FileZilla (alternative)
1. Connectez-vous avec FileZilla (protocole SFTP)
2. Naviguez vers `/home/votre_login/gedavocat/app/`
3. Uploadez :
   - `gedavocat-app-1.0.0.jar`
   - `application.properties`

---

## 🚀 ÉTAPE 5 : Créer le script de démarrage

Connectez-vous en SSH et créez le fichier de service :

```bash
cd ~/gedavocat/scripts
nano start.sh
```

Collez le contenu suivant :

```bash
#!/bin/bash
# Script de démarrage GED Avocat

APP_NAME="gedavocat"
APP_DIR="/home/VOTRE_LOGIN/gedavocat/app"
JAR_FILE="gedavocat-app-1.0.0.jar"
PID_FILE="/home/VOTRE_LOGIN/gedavocat/$APP_NAME.pid"
LOG_FILE="/home/VOTRE_LOGIN/gedavocat/logs/startup.log"

cd $APP_DIR

# Vérifier si l'application est déjà en cours d'exécution
if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null 2>&1; then
        echo "L'application est déjà en cours d'exécution (PID: $PID)"
        exit 1
    else
        rm -f $PID_FILE
    fi
fi

# Démarrer l'application
echo "Démarrage de $APP_NAME..."
nohup java -jar \
    -Xms512m \
    -Xmx1024m \
    -Dspring.config.location=file:$APP_DIR/application.properties \
    -Dspring.profiles.active=production \
    $JAR_FILE > $LOG_FILE 2>&1 &

# Sauvegarder le PID
echo $! > $PID_FILE
echo "$APP_NAME démarré avec le PID $(cat $PID_FILE)"
echo "Logs: tail -f $LOG_FILE"
```

Rendre le script exécutable :
```bash
chmod +x start.sh
chmod +x stop.sh
chmod +x restart.sh
```

---

## 🔄 ÉTAPE 6 : Créer le script d'arrêt

```bash
nano stop.sh
```

Collez :

```bash
#!/bin/bash
# Script d'arrêt GED Avocat

APP_NAME="gedavocat"
PID_FILE="/home/VOTRE_LOGIN/gedavocat/$APP_NAME.pid"

if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    echo "Arrêt de $APP_NAME (PID: $PID)..."
    kill -15 $PID
    sleep 5
    
    # Vérifier si le processus est toujours actif
    if ps -p $PID > /dev/null 2>&1; then
        echo "Arrêt forcé..."
        kill -9 $PID
    fi
    
    rm -f $PID_FILE
    echo "$APP_NAME arrêté"
else
    echo "Aucun fichier PID trouvé. L'application n'est pas en cours d'exécution."
fi
```

---

## ⚙️ ÉTAPE 7 : Configuration du reverse proxy Apache

Sur O2Switch, Apache est déjà installé. Configurez le via cPanel :

### 7.1 Via .htaccess
Créez un fichier `.htaccess` dans le répertoire public_html :

```apache
# Redirection HTTPS
RewriteEngine On
RewriteCond %{HTTPS} off
RewriteRule ^(.*)$ https://%{HTTP_HOST}%{REQUEST_URI} [L,R=301]

# Proxy vers l'application Spring Boot
RewriteCond %{REQUEST_URI} !^/\.well-known/
RewriteRule ^(.*)$ http://localhost:8080/$1 [P,L]

# Headers de sécurité
Header set X-Content-Type-Options "nosniff"
Header set X-Frame-Options "SAMEORIGIN"
Header set X-XSS-Protection "1; mode=block"
Header set Referrer-Policy "strict-origin-when-cross-origin"
```

### 7.2 Activer les modules Apache (via support O2Switch)
Contactez le support O2Switch pour activer :
- `mod_proxy`
- `mod_proxy_http`
- `mod_headers`
- `mod_rewrite`

---

## 🎯 ÉTAPE 8 : Démarrage de l'application

```bash
cd ~/gedavocat/scripts
./start.sh

# Vérifier les logs
tail -f ~/gedavocat/logs/startup.log

# Vérifier que l'app tourne
ps aux | grep gedavocat
```

---

## 🔍 ÉTAPE 9 : Vérification et tests

### 9.1 Tester l'accès local
```bash
curl http://localhost:8080/login
```

### 9.2 Tester depuis le navigateur
```
https://votre-domaine.com/login
```

### 9.3 Vérifier les logs
```bash
tail -f ~/gedavocat/logs/application.log
tail -f ~/gedavocat/logs/startup.log
```

### 9.4 Créer un compte admin
Accédez à : `https://votre-domaine.com/register`

---

## 🔄 ÉTAPE 10 : Automatisation du démarrage

### 10.1 Créer une tâche CRON
Via cPanel > Cron Jobs, ajoutez :

```bash
# Démarrer l'application au redémarrage du serveur
@reboot /home/VOTRE_LOGIN/gedavocat/scripts/start.sh

# Sauvegarde quotidienne à 2h du matin
0 2 * * * /home/VOTRE_LOGIN/gedavocat/scripts/backup.sh
```

---

## 🛠️ Commandes utiles

### Redémarrer l'application
```bash
cd ~/gedavocat/scripts
./restart.sh
```

### Voir les logs en temps réel
```bash
tail -f ~/gedavocat/logs/application.log
```

### Vérifier le statut
```bash
ps aux | grep gedavocat
```

### Tester la connexion MySQL
```bash
mysql -u gedavocat_user -p gedavocat_prod
```

### Vérifier l'espace disque
```bash
df -h
du -sh ~/gedavocat/*
```

---

## 🔒 Sécurité - Points critiques

### ✅ Checklist de sécurité

- [ ] **JWT Secret** : Clé unique et sécurisée (64+ caractères)
- [ ] **Mots de passe MySQL** : Fort et unique
- [ ] **SSL/HTTPS** : Activé via Let's Encrypt (gratuit sur O2Switch)
- [ ] **Permissions fichiers** : 
  - JAR : `chmod 644`
  - Scripts : `chmod 755`
  - Répertoires uploads : `chmod 700`
  - Fichiers de config : `chmod 600`
- [ ] **Firewall** : Port 8080 non exposé publiquement
- [ ] **Sauvegardes** : Automatiques et testées
- [ ] **Logs** : Rotation configurée (30 jours)

### Sécuriser application.properties
```bash
chmod 600 ~/gedavocat/app/application.properties
```

---

## 🐛 Dépannage

### L'application ne démarre pas
1. Vérifier les logs : `tail -n 100 ~/gedavocat/logs/startup.log`
2. Vérifier que le port 8080 est libre : `netstat -tulpn | grep 8080`
3. Vérifier la connexion MySQL : `mysql -u USER -p`

### Erreur de connexion à la base
- Vérifier les identifiants dans `application.properties`
- Tester : `mysql -h localhost -u USER -p BASE`

### L'application crashe
```bash
# Augmenter la mémoire JVM
# Dans start.sh, modifier :
-Xms512m -Xmx2048m
```

### Impossible d'uploader des fichiers
```bash
# Vérifier les permissions
ls -la ~/gedavocat/uploads/
chmod 700 ~/gedavocat/uploads -R
```

---

## 📞 Support O2Switch

- **Panel client** : https://www.o2switch.fr/espace-client/
- **Support technique** : Via ticket dans l'espace client
- **Documentation** : https://faq.o2switch.fr/

---

## 🎉 Félicitations !

Votre application GED Avocat est maintenant déployée sur O2Switch et accessible en production !

**URL de connexion** : https://votre-domaine.com/login

N'oubliez pas de :
1. Créer votre premier compte admin
2. Tester toutes les fonctionnalités
3. Configurer les sauvegardes automatiques
4. Surveiller les logs régulièrement

---

## 📚 Ressources supplémentaires

- [Documentation O2Switch](https://faq.o2switch.fr/)
- [Guide Spring Boot Production](https://spring.io/guides/gs/spring-boot/)
- [Sécurité Spring Boot](https://spring.io/guides/topicals/spring-security-architecture/)
