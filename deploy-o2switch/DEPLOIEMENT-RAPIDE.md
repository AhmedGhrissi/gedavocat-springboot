# 🚀 DÉPLOIEMENT RAPIDE - GED Avocat sur O2Switch

## ⚡ Démarrage en 10 minutes

### Étape 1 : Préparer la base de données (2 min)

1. Connectez-vous à **cPanel O2Switch**
2. Allez dans **Bases de données MySQL**
3. Créez une nouvelle base (ex: `gedavocat_db`)
4. Notez les identifiants créés

### Étape 2 : Configurer l'application (2 min)

Éditez le fichier `application.properties` :

```properties
# Remplacez ces valeurs par vos identifiants O2Switch
spring.datasource.url=jdbc:mysql://localhost:3306/VOTRE_BASE
spring.datasource.username=VOTRE_USER
spring.datasource.password=VOTRE_PASSWORD

# Générez une clé JWT sécurisée (64 caractères)
jwt.secret=VOTRE_CLE_SECRETE_TRES_LONGUE_ICI

# Adaptez les chemins de stockage
app.upload.dir=/home/VOTRE_USER/uploads/documents
app.signature.dir=/home/VOTRE_USER/uploads/signatures

# Configurez l'email si nécessaire
spring.mail.host=smtp.VOTRE_DOMAINE.com
spring.mail.username=noreply@VOTRE_DOMAINE.com
spring.mail.password=VOTRE_PASSWORD_EMAIL
```

### Étape 3 : Upload via FTP/SFTP (3 min)

Uploadez ces fichiers sur votre serveur O2Switch :

```
Fichiers locaux           →  Destination O2Switch
─────────────────────────────────────────────────
app.jar                   →  ~/gedavocat/app.jar
application.properties    →  ~/gedavocat/application.properties
scripts/start.sh          →  ~/gedavocat/start.sh
scripts/stop.sh           →  ~/gedavocat/stop.sh
scripts/restart.sh        →  ~/gedavocat/restart.sh
.htaccess                 →  ~/public_html/.htaccess (optionnel)
```

### Étape 4 : Connexion SSH et démarrage (3 min)

```bash
# Se connecter en SSH
ssh votre_user@ssh.o2switch.net

# Créer les répertoires nécessaires
mkdir -p ~/gedavocat/logs
mkdir -p ~/uploads/documents
mkdir -p ~/uploads/signatures

# Rendre les scripts exécutables
chmod +x ~/gedavocat/*.sh

# Démarrer l'application
cd ~/gedavocat
./start.sh

# Vérifier que ça tourne
tail -f logs/gedavocat.log
```

### Étape 5 : Tester l'accès

Accédez à votre application :
- Via port direct : `http://votre-domaine.com:8080`
- Via reverse proxy : `http://votre-domaine.com/` (si .htaccess configuré)

### 🔐 Premier login

Comptes par défaut (à changer immédiatement) :
- **Admin** : `admin@gedavocat.com` / `admin123`
- **Avocat** : `lawyer@gedavocat.com` / `lawyer123`

---

## 📊 Commandes utiles

```bash
# Démarrer l'application
./start.sh

# Arrêter l'application
./stop.sh

# Redémarrer l'application
./restart.sh

# Voir les logs en temps réel
tail -f logs/gedavocat.log

# Vérifier si l'application tourne
ps aux | grep java

# Tester l'accès local
curl http://localhost:8080
```

---

## 🆘 Problèmes fréquents

### L'application ne démarre pas

```bash
# Vérifier les logs
tail -n 50 logs/gedavocat.log

# Vérifier la configuration MySQL
cat application.properties | grep datasource
```

### Port 8080 déjà utilisé

```bash
# Trouver le processus
lsof -i :8080

# Tuer le processus
kill -9 [PID]
```

### Erreur de connexion MySQL

1. Vérifiez que la base existe
2. Vérifiez les identifiants dans `application.properties`
3. Testez la connexion MySQL :
```bash
mysql -u VOTRE_USER -p VOTRE_BASE
```

---

## 📚 Documentation complète

Pour plus de détails, consultez :
- **DEPLOIEMENT-O2SWITCH.md** - Guide complet de déploiement
- **README.md** - Documentation technique
- **QUICKSTART.md** - Guide de démarrage

---

## ✅ Checklist de déploiement

- [ ] Base de données MySQL créée
- [ ] Fichier JAR uploadé
- [ ] Configuration `application.properties` mise à jour
- [ ] Clé JWT générée et configurée
- [ ] Répertoires créés (`logs`, `uploads`)
- [ ] Scripts rendus exécutables
- [ ] Application démarrée avec `./start.sh`
- [ ] Application accessible via navigateur
- [ ] Mots de passe par défaut changés
- [ ] SSL/HTTPS activé (recommandé)

---

## 🎉 Félicitations !

Votre application GED Avocat est maintenant déployée sur O2Switch !

**Support** : Pour toute question, consultez la documentation complète ou contactez le support O2Switch.
