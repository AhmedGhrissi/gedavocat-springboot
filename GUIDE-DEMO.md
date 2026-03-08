# 🎯 Guide Rapide - Présentation DocAvocat

## ⚡ Démarrage Ultra-Rapide (Pour Demain !)

### 1️⃣ Avant de commencer
- ✅ Démarrez **Docker Desktop** (icône baleine)
- ✅ Attendez que Docker soit prêt (icône verte)

### 2️⃣ Lancer l'application

```powershell
.\start-demo.ps1
```

Ce script va :
- ✅ Vérifier Docker
- ✅ Démarrer MySQL automatiquement
- ✅ Compiler l'application
- ✅ Afficher les commandes pour démarrer

### 3️⃣ Démarrer l'application

Une fois le script terminé, lancez :

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4️⃣ Accéder à l'application

Ouvrez votre navigateur : **http://localhost:8080**

---

## 🔧 En cas de problème

### MySQL ne démarre pas ?
```powershell
.\check-mysql.ps1
```

### Vérifier que tout compile ?
```powershell
mvn clean compile -DskipTests
```

### Voir les logs MySQL
```powershell
cd docker
docker-compose logs -f mysql
```

---

## 📊 Informations Techniques

- **Base de données** : MySQL 8.0 (Docker)
- **Port BDD** : 3307
- **Port App** : 8080
- **Profil** : dev (emails et paiements désactivés)

---

## 🎬 Checklist Présentation

- [ ] Docker Desktop démarré ✅
- [ ] MySQL lancé (`.\start-demo.ps1`) ✅
- [ ] Application compilée (BUILD SUCCESS) ✅
- [ ] Application démarrée (`mvn spring-boot:run -Dspring-boot.run.profiles=dev`) ✅
- [ ] Navigateur ouvert sur http://localhost:8080 ✅

---

## ⚠️ Notes Importantes

- **Emails** : Ne seront pas envoyés (mode dev)
- **Paiements** : Mode test uniquement
- **Brevo/Stripe** : Pas configuré (valeurs par défaut)
- **Sécurité** : Configuration dev simplifiée

---

## 🚀 Bonne présentation !

Tout est prêt pour votre démo de demain ! 🎉
