# 🏛️ GED Avocat - Gestion Électronique de Documents

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

Application SaaS de gestion documentaire sécurisée pour cabinets d'avocats français. Conforme RGPD, CNIL et respectant le secret professionnel (art. 66-5).

---

## 🚀 Démarrage Rapide (5 minutes)

### 1️⃣ Prérequis
- ☕ Java 17+
- 🗄️ MySQL 8.0+

### 2️⃣ Configuration
```bash
# 1. Créer la base de données
mysql -u root -p
CREATE DATABASE gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;

# 2. Importer le schéma
mysql -u root -p gedavocat < database-init.sql

# 3. Configurer les variables d'environnement
cp .env.example .env
notepad .env  # Remplir avec vos valeurs
```

### 3️⃣ Lancement
🌐 Application : **http://localhost:8081**

---

## ✨ Fonctionnalités

### 📁 Gestion Documentaire
- ✅ Chiffrement AES-256-GCM
- ✅ Organisation par dossiers/clients

### ✍️ Signature Électronique (Yousign)
- ✅ Signature conforme eIDAS
- ✅ 3 niveaux certifiés

### ⚖️ Communication RPVA
- ✅ Envoi aux juridictions
- ✅ Accusés de réception

### 💳 Abonnements Stripe
- ✅ Solo : 29.99€/mois
- ✅ Cabinet : 99.99€/mois
- ✅ Enterprise : 299.99€/mois

---

## 🔐 Sécurité

**⚠️ IMPORTANT** : Ne commitez JAMAIS vos clés API !

Voir [SECURITY-GUIDE.md](SECURITY-GUIDE.md)

---

## 📊 Statut
- ✅ **Tous les TODOs complétés**
- ✅ **Sécurité renforcée**
- ✅ Production-ready

**Version** : 1.0.0 | **Mise à jour** : 2026-02-19
