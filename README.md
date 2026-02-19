# GED Avocat - Application Spring Boot MVC

Application de Gestion Électronique de Documents pour Avocats développée en Java Spring Boot MVC avec MySQL.

## 📋 Prérequis

- **Java 17** ou supérieur
- **Maven 3.6+**
- **MySQL 8.0+**
- **IDE** (IntelliJ IDEA, Eclipse, ou VS Code avec extensions Java)

## 🚀 Installation

### 1. Cloner ou télécharger le projet

```bash
cd gedavocat-springboot
```

### 2. Configuration de la base de données MySQL

#### Créer la base de données

```sql
CREATE DATABASE gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'gedavocat_user'@'localhost' IDENTIFIED BY 'votre_mot_de_passe';
GRANT ALL PRIVILEGES ON gedavocat.* TO 'gedavocat_user'@'localhost';
FLUSH PRIVILEGES;
```

#### Configurer application.properties

Ouvrez le fichier `src/main/resources/application.properties` et modifiez les paramètres de connexion :

```properties
# Configuration MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/gedavocat?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=gedavocat_user
spring.datasource.password=votre_mot_de_passe

# JWT Secret (IMPORTANT: Changez cette clé en production)
jwt.secret=VotreClefSecreteTresLongueEtSecuriseePourJWT2024GEDAvocat123456789
```

### 3. Construire le projet

```bash
mvn clean install
```

### 4. Lancer l'application

```bash
mvn spring-boot:run
```

Ou avec Java :

```bash
java -jar target/gedavocat-app-1.0.0.jar
```

L'application sera accessible à l'adresse : **http://localhost:8080**

## 📁 Structure du projet

```
gedavocat-springboot/
├── src/
│   ├── main/
│   │   ├── java/com/gedavocat/
│   │   │   ├── config/              # Configuration (Security, etc.)
│   │   │   ├── controller/          # Contrôleurs MVC
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   ├── model/               # Entités JPA
│   │   │   ├── repository/          # Repositories (DAO)
│   │   │   ├── security/            # Sécurité et JWT
│   │   │   ├── service/             # Services métier
│   │   │   └── GedAvocatApplication.java
│   │   │
│   │   └── resources/
│   │       ├── static/              # CSS, JS, images
│   │       │   ├── css/
│   │       │   ├── js/
│   │       │   └── images/
│   │       ├── templates/           # Templates Thymeleaf
│   │       │   ├── auth/            # Pages d'authentification
│   │       │   ├── dashboard/       # Tableau de bord
│   │       │   ├── clients/         # Gestion clients
│   │       │   ├── cases/           # Gestion dossiers
│   │       │   └── documents/       # Gestion documents
│   │       └── application.properties
│   │
│   └── test/                        # Tests unitaires et d'intégration
│
└── pom.xml                          # Dépendances Maven
```

## 🔐 Fonctionnalités

### Authentification et Autorisation
- ✅ Inscription avec validation
- ✅ Connexion avec JWT
- ✅ Gestion des rôles (Admin, Avocat, Client, Collaborateur)
- ✅ Protection des routes par rôle

### Gestion des Clients
- ✅ CRUD complet des clients
- ✅ Recherche et filtrage
- ✅ Gestion des accès temporaires

### Gestion des Dossiers
- ✅ Création de dossiers par client
- ✅ Statuts (Ouvert, Fermé, Archivé)
- ✅ Partage de dossiers entre avocats

### Gestion des Documents
- ✅ Upload de fichiers multiples
- ✅ Gestion des versions
- ✅ Corbeille et restauration
- ✅ Signature électronique (intégration possible)

### Permissions
- ✅ Partage granulaire (lecture, écriture, upload)
- ✅ Permissions temporaires avec expiration
- ✅ Révocation de permissions

### Audit et Traçabilité
- ✅ Journal complet des actions
- ✅ Historique par utilisateur/entité
- ✅ Traçabilité IP et User-Agent

### Abonnements
- ✅ Plans tarifaires (Solo, Cabinet, Enterprise)
- ✅ Gestion des limites de clients
- ✅ Quotas de stockage

## 🎨 Design Responsive

L'application est entièrement responsive et s'adapte à tous les écrans :
- 📱 **Mobile** (< 480px)
- 📱 **Tablette** (480px - 768px)
- 💻 **Desktop** (> 768px)

## 🔧 Configuration

### Modification du port

Dans `application.properties` :

```properties
server.port=8080  # Changez selon vos besoins
```

### Configuration email (SMTP)

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=votre-email@gmail.com
spring.mail.password=votre-mot-de-passe-application
```

### Upload de fichiers

```properties
# Taille maximale par fichier
spring.servlet.multipart.max-file-size=50MB

# Taille maximale de la requête
spring.servlet.multipart.max-request-size=100MB

# Répertoire de stockage
app.upload.dir=uploads/documents
```

## 🛡️ Sécurité

### JWT Configuration

Dans `application.properties` :

```properties
# Clé secrète JWT (IMPORTANT: Changez en production)
jwt.secret=VotreClefSecreteTresLongueEtSecurisee

# Durée de validité (24 heures en millisecondes)
jwt.expiration=86400000
```

### Protection CSRF

CSRF est désactivé pour les API REST mais activé pour les formulaires.

### Hachage des mots de passe

Les mots de passe sont hachés avec BCrypt (coût: 10).

## 📊 Base de données

### Schéma principal

- **users** - Utilisateurs (avocats, clients, admins)
- **clients** - Clients du cabinet
- **cases** - Dossiers juridiques
- **documents** - Documents stockés
- **permissions** - Droits d'accès partagés
- **audit_logs** - Journal d'audit

### Migration automatique

Hibernate gère automatiquement la création et mise à jour du schéma :

```properties
spring.jpa.hibernate.ddl-auto=update
```

Options :
- `create` : Recrée le schéma à chaque démarrage (⚠️ perte de données)
- `update` : Met à jour le schéma sans perte
- `validate` : Valide uniquement
- `none` : Aucune action automatique

## 🧪 Tests

### Lancer les tests

```bash
mvn test
```

### Tests d'intégration

```bash
mvn verify
```

## 📦 Déploiement

### Package JAR

```bash
mvn clean package
```

Le fichier JAR sera dans `target/gedavocat-app-1.0.0.jar`

### Exécution en production

```bash
java -jar target/gedavocat-app-1.0.0.jar --spring.profiles.active=prod
```

### Docker (optionnel)

Créer un `Dockerfile` :

```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/gedavocat-app-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build et run :

```bash
docker build -t gedavocat .
docker run -p 8080:8080 gedavocat
```

## 🔨 Développement

### Mode développement

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Avec DevTools activé, l'application redémarre automatiquement lors des modifications.

### Hot reload

Spring Boot DevTools est inclus pour le rechargement automatique.

## 📝 Logs

Les logs sont stockés dans `logs/gedavocat.log`

Niveaux de log configurables dans `application.properties` :

```properties
logging.level.com.gedavocat=DEBUG
logging.level.org.springframework.security=DEBUG
```

## 🤝 Contribution

Pour contribuer au projet :

1. Fork le projet
2. Créer une branche (`git checkout -b feature/AmazingFeature`)
3. Commit les changements (`git commit -m 'Add AmazingFeature'`)
4. Push vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## 📄 Licence

Ce projet est sous licence MIT.

## 👥 Support

Pour toute question ou support :
- Email: support@gedavocat.com
- Documentation: [lien vers documentation]

## 🎯 Roadmap

- [ ] Intégration e-Barreau (RPVA)
- [ ] Signature électronique avancée
- [ ] Module de facturation
- [ ] Application mobile (iOS/Android)
- [ ] Synchronisation cloud
- [ ] OCR pour documents scannés
- [ ] Recherche full-text avancée

---

**Développé avec ❤️ pour les avocats français**
