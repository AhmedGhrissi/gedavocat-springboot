# 🚀 Script de Déploiement Complet GedAvoCat

## 📋 Description

Scripts pour déployer complètement la base de données GedAvoCat avec :
- ✅ Import du dump de production
- ✅ Schéma mis à jour avec les colonnes `barreau_id` et `billing_period`
- ✅ Ajout automatique d'un utilisateur ADMIN et d'un utilisateur CLIENT

## 📦 Fichiers Inclus

### Scripts SQL
- `dump_gedavocat_prod_20260305_backup.sql` - Dump principal (schéma mis à jour)
- `add_admin_client_users.sql` - Script d'ajout des utilisateurs
- `setup_complet_gedavocat.sql` - Script tout-en-un (utilise SOURCE)

### Scripts d'Automatisation
- `deploy_complet.ps1` - **PowerShell (Windows - RECOMMANDÉ)**
- `deploy_complet.bat` - Batch Windows
- `deploy_complet.sh` - Bash Linux/Mac

## 🎯 Utilisation Rapide

### Option 1: PowerShell (Recommandé pour Windows)

```powershell
.\deploy_complet.ps1
```

### Option 2: Batch

```cmd
deploy_complet.bat
```

### Option 3: Bash (Linux/Mac)

```bash
chmod +x deploy_complet.sh
./deploy_complet.sh
```

### Option 4: Manuelle (MySQL)

```bash
# 1. Importer le dump principal
mysql -u root -p gedavocat < dump_gedavocat_prod_20260305_backup.sql

# 2. Ajouter les utilisateurs
mysql -u root -p gedavocat < add_admin_client_users.sql
```

## 👥 Utilisateurs Créés

### Super Administrateur
- **Email:** `superadmin@gedavocat.fr`
- **Mot de passe:** `Test1234!`
- **Rôle:** ADMIN
- **Description:** Administrateur système avec tous les droits

### Client de Démonstration
- **Email:** `client.demo@gedavocat.fr`
- **Mot de passe:** `Test1234!`
- **Rôle:** CLIENT
- **Cabinet:** firm-demo-001
- **Description:** Client de test rattaché au cabinet de démonstration

## 🔧 Modifications du Schéma

Le dump a été mis à jour avec les colonnes suivantes dans la table `users`:

```sql
-- Nouvelle colonne pour le barreau français
barreau_id bigint DEFAULT NULL

-- Nouvelle colonne pour la période de facturation
billing_period varchar(20) DEFAULT NULL

-- Contrainte de clé étrangère
CONSTRAINT fk_user_barreau 
  FOREIGN KEY (barreau_id) 
  REFERENCES barreaux_france (id) 
  ON DELETE SET NULL
```

## ⚠️ Prérequis

- MySQL 8.0+
- Base de données `gedavocat` créée
- Accès root ou utilisateur avec privilèges suffisants

## 📝 Notes

- Le mot de passe des utilisateurs de test utilise BCrypt: `$2a$12$S0in8qlrAXxQeT1T9dwrfuiWMDQ1D7Q04vrkV5SMKR8lIUiB.aATi`
- Les scripts utilisent `ON DUPLICATE KEY UPDATE` pour éviter les erreurs si les utilisateurs existent déjà
- Le schéma est synchronisé avec les entités JPA (User.java, Client.java, etc.)

## 🔍 Vérification

Après l'exécution, vérifiez que les utilisateurs sont créés:

```sql
SELECT id, email, first_name, last_name, role 
FROM users 
WHERE id IN ('admin-super-001', 'client-demo-002');
```

## 📞 Support

Pour toute question ou problème, consultez les logs d'erreur MySQL ou contactez l'équipe technique.
