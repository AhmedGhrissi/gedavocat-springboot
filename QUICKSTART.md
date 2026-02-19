# 🚀 GUIDE DE DÉMARRAGE RAPIDE - GED Avocat

## Installation en 5 minutes

### Étape 1 : Prérequis
Vérifiez que vous avez installé :
- ✅ Java 17 ou supérieur (`java -version`)
- ✅ Maven 3.6+ (`mvn -version`)
- ✅ MySQL 8.0+ (`mysql --version`)

### Étape 2 : Configuration MySQL

Ouvrez MySQL et exécutez :

```sql
-- Créer la base de données
CREATE DATABASE gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- (Optionnel) Créer un utilisateur dédié
CREATE USER 'gedavocat_user'@'localhost' IDENTIFIED BY 'VotreMotDePasse123!';
GRANT ALL PRIVILEGES ON gedavocat.* TO 'gedavocat_user'@'localhost';
FLUSH PRIVILEGES;
```

Ou utilisez le script fourni :
```bash
mysql -u root -p < database-init.sql
```

### Étape 3 : Configuration de l'application

Éditez `src/main/resources/application.properties` :

```properties
# Connexion MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/gedavocat
spring.datasource.username=root
spring.datasource.password=votre_mot_de_passe

# JWT Secret (IMPORTANT: changez cette valeur)
jwt.secret=VotreClefSecreteSuperLongueEtComplexe2024GEDAvocat
```

### Étape 4 : Lancer l'application

```bash
# Construire le projet
mvn clean install

# Démarrer l'application
mvn spring-boot:run
```

### Étape 5 : Accéder à l'application

Ouvrez votre navigateur à l'adresse :
**http://localhost:8080**

## 🔐 Comptes par défaut

### Administrateur
- **Email** : admin@gedavocat.com
- **Mot de passe** : admin123

### Avocat
- **Email** : lawyer@gedavocat.com
- **Mot de passe** : lawyer123

⚠️ **IMPORTANT** : Changez ces mots de passe en production !

## 📱 Responsive

L'application est automatiquement responsive pour :
- 💻 PC (> 768px)
- 📱 Tablette (480px - 768px)
- 📱 Mobile (< 480px)

## ⚡ Commandes utiles

### Développement
```bash
# Démarrer avec rechargement automatique
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Compiler sans tests
mvn clean install -DskipTests

# Nettoyer le projet
mvn clean
```

### Production
```bash
# Créer le JAR
mvn clean package

# Lancer le JAR
java -jar target/gedavocat-app-1.0.0.jar
```

### Tests
```bash
# Lancer tous les tests
mvn test

# Tests d'intégration
mvn verify
```

## 🔧 Résolution de problèmes

### Port déjà utilisé
```properties
# Changer le port dans application.properties
server.port=8081
```

### Erreur de connexion MySQL
1. Vérifiez que MySQL est démarré
2. Vérifiez les identifiants dans `application.properties`
3. Vérifiez que la base `gedavocat` existe

### Erreur de mémoire
```bash
# Augmenter la mémoire allouée
export MAVEN_OPTS="-Xmx1024m"
mvn spring-boot:run
```

## 📚 Documentation complète

Consultez le fichier `README.md` pour la documentation complète.

## 🆘 Support

En cas de problème :
1. Vérifiez les logs : `logs/gedavocat.log`
2. Consultez la documentation
3. Contactez le support : support@gedavocat.com

---

**Bon développement ! 🎉**
