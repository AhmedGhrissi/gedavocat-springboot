# 🚀 GED AVOCAT - APPLICATION COMPLÈTE

## ⚡ Démarrage Ultra-Rapide (5 minutes)

### 📋 Prérequis
- ☕ Java 17+
- 📦 Maven 3.6+
- 🗄️ MySQL 8.0+

---

## 🎯 ÉTAPE PAR ÉTAPE

### 1️⃣ Créer la base de données (1 min)

```bash
# Se connecter à MySQL
mysql -u root -p
```

```sql
-- Créer la base
CREATE DATABASE gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
```

```bash
# Importer les données
mysql -u root -p gedavocat < database-init-FINAL.sql
```

✅ **Résultat** : 7 utilisateurs, 6 clients, 8 dossiers créés

---

### 2️⃣ Configurer l'application (30 secondes)

Éditer : `src/main/resources/application.properties`

**Modifier UNIQUEMENT cette ligne :**
```properties
spring.datasource.password=VOTRE_MOT_DE_PASSE_MYSQL
```

💡 Tout le reste est déjà configuré !

---

### 3️⃣ Lancer l'application (2 min)

```bash
mvn spring-boot:run
```

⏳ Attendre le message : `Started GedAvocatApplication in X seconds`

---

### 4️⃣ Tester (1 min)

🌐 Ouvrir : **http://localhost:8080**

✅ La page d'accueil s'affiche avec 3 plans tarifaires

**Cliquer "Se connecter" :**
- Email : `admin@gedavocat.com`
- Mot de passe : `password123`

✅ Le dashboard s'affiche avec les statistiques

---

## 🔐 Comptes de test

**Mot de passe universel** : `password123`

| Email | Rôle | Plan |
|-------|------|------|
| admin@gedavocat.com | Admin | - |
| jean.dupont@gedavocat.com | Avocat | Cabinet |
| marie.martin@gedavocat.com | Avocat | Solo |
| pierre.bernard@gedavocat.com | Avocat | Enterprise |
| paul.durand@email.com | Client | - |

---

## 💰 Plans d'abonnement

| Plan | Prix | Limite clients |
|------|------|----------------|
| **Solo** | 29,99€/mois | 10 clients |
| **Cabinet** ⭐ | 99,99€/mois | 100 clients |
| **Enterprise** | 299,99€/mois | Illimité |

---

## 📱 Fonctionnalités

✅ Page d'accueil avec tarifs  
✅ Inscription avec pré-sélection du plan  
✅ Dashboard personnalisé par rôle  
✅ Gestion clients (CRUD)  
✅ Gestion dossiers avec statuts  
✅ Upload de documents  
✅ Signatures électroniques (Yousign)  
✅ Communications e-Barreau (RPVA)  
✅ Paiements Stripe  
✅ Spring Security + JWT  

---

## 🛠️ Structure du projet

```
gedavocat-final/
├── src/main/
│   ├── java/com/gedavocat/
│   │   ├── controller/      # 8 contrôleurs
│   │   ├── service/         # 8 services
│   │   ├── model/           # 6 entités
│   │   ├── repository/      # 6 repositories
│   │   ├── dto/             # DTOs
│   │   ├── config/          # Configuration
│   │   └── security/        # JWT, filtres
│   └── resources/
│       ├── templates/       # 15 pages HTML
│       └── static/          # CSS, JS
├── src/test/                # Tests unitaires
├── database-init-FINAL.sql  # Base de données
├── pom.xml
└── START-HERE.md            # Ce fichier
```

---

## 🧪 Lancer les tests

```bash
mvn test
```

**Résultat attendu** :
- ✅ ClientServiceTest
- ✅ AuthServiceTest  
- ✅ BCryptPasswordTest
- ✅ JwtServiceTest

---

## ⚠️ Problèmes courants

### Port 8080 déjà utilisé
```bash
# Ajouter dans application.properties
server.port=8081
```

### MySQL refuse la connexion
```bash
sudo systemctl start mysql
sudo systemctl status mysql
```

### Table n'existe pas
```bash
# Réimporter la base
mysql -u root -p gedavocat < database-init-FINAL.sql
```

---

## 📚 Documentation

- `README.md` - Documentation complète
- `QUICKSTART.md` - Guide rapide
- `deploy-o2switch/` - Scripts de déploiement

---

## 🎯 Parcours utilisateur

1. **http://localhost:8080/** → Landing page
2. Choisir **"Cabinet"** → Inscription avec plan pré-sélectionné
3. **Ou** cliquer **"Se connecter"**
4. Dashboard → Créer client → Créer dossier → Upload document

---

**Version** : 1.0.0  
**Date** : 17 février 2026  
**Status** : ✅ Production Ready

🎉 **C'est parti !**
