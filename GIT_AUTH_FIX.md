# 🔐 Guide de Résolution - Erreur Git "not authorized"

## ❌ Problème

```
Can't connect to repository: 
https://gitlab.com/ahmed.ghrissi/gedavocat-springboot.git
Error: not authorized
```

---

## ✅ Solutions par Ordre de Priorité

### **Solution 1 : Utiliser un Personal Access Token (Recommandé)**

#### Étape 1 : Créer un Personal Access Token sur GitLab

1. Connectez-vous à **GitLab** : https://gitlab.com
2. Cliquez sur votre **avatar** (en haut à droite) → **Preferences**
3. Dans le menu de gauche : **Access Tokens**
4. Créez un nouveau token :
   - **Token name** : `gedavocat-dev` (ou autre nom)
   - **Expiration date** : Choisir une date (ex: 1 an)
   - **Scopes** : Cocher au minimum :
     - ✅ `read_repository`
     - ✅ `write_repository`
     - ✅ `api` (optionnel mais recommandé)
5. Cliquez sur **Create personal access token**
6. **IMPORTANT** : Copiez le token immédiatement (il ne sera plus visible après)

#### Étape 2 : Configurer Git avec le Token

**Option A - Mettre à jour l'URL du remote avec le token :**

```bash
cd C:\Users\el_ch\git\gedavocat-springboot

# Supprimer l'ancien remote
git remote remove origin

# Ajouter le nouveau remote avec le token
git remote add origin https://ahmed.ghrissi:VOTRE_TOKEN_ICI@gitlab.com/ahmed.ghrissi/gedavocat-springboot.git

# Vérifier
git remote -v
```

**Option B - Utiliser Git Credential Manager (Windows) :**

```bash
cd C:\Users\el_ch\git\gedavocat-springboot

# Configurer le credential helper
git config --global credential.helper wincred

# Tenter de pull (vous serez invité à entrer vos credentials)
git pull origin main
```

Quand demandé :
- **Username** : `ahmed.ghrissi`
- **Password** : Collez votre **Personal Access Token** (PAS votre mot de passe GitLab)

---

### **Solution 2 : Vérifier et Mettre à Jour les Credentials Existants**

#### Sur Windows - Gestionnaire d'Identifications

1. Appuyez sur **Windows + R**
2. Tapez : `control /name Microsoft.CredentialManager`
3. Cliquez sur **Informations d'identification Windows**
4. Cherchez les entrées **GitLab** ou **git:https://gitlab.com**
5. **Supprimez** toutes les entrées GitLab obsolètes
6. Réessayez `git pull` → vous serez invité à entrer les nouveaux credentials

#### Commandes Git pour Réinitialiser

```bash
# Effacer les credentials en cache
git credential-cache exit

# Ou supprimer la configuration locale
git config --local --unset credential.helper
git config --global --unset credential.helper

# Réessayer
git pull
```

---

### **Solution 3 : Utiliser SSH au lieu de HTTPS (Plus Sécurisé)**

#### Étape 1 : Générer une Clé SSH (si pas déjà fait)

```bash
# Générer une clé SSH
ssh-keygen -t ed25519 -C "votre.email@example.com"

# Appuyer sur Entrée pour accepter l'emplacement par défaut
# Choisir un mot de passe (ou laisser vide pour pas de mot de passe)

# Afficher la clé publique
type %USERPROFILE%\.ssh\id_ed25519.pub
```

#### Étape 2 : Ajouter la Clé SSH à GitLab

1. Copiez tout le contenu de la clé publique
2. Sur GitLab : **Avatar** → **Preferences** → **SSH Keys**
3. Collez la clé dans le champ **Key**
4. Donnez un titre (ex: "PC Bureau")
5. Cliquez sur **Add key**

#### Étape 3 : Changer l'URL du Remote vers SSH

```bash
cd C:\Users\el_ch\git\gedavocat-springboot

# Vérifier l'URL actuelle
git remote -v

# Changer vers SSH
git remote set-url origin git@gitlab.com:ahmed.ghrissi/gedavocat-springboot.git

# Vérifier
git remote -v

# Tester
git pull
```

---

### **Solution 4 : Vérifier les Permissions du Projet**

#### Sur GitLab

1. Allez sur le projet : https://gitlab.com/ahmed.ghrissi/gedavocat-springboot
2. Vérifiez que vous êtes bien **Owner** ou **Maintainer**
3. Menu **Project information** → **Members**
4. Vérifiez votre rôle

Si vous n'êtes pas membre, vous devez :
- Demander l'accès au propriétaire
- Ou forker le projet

---

## 🔧 Commandes de Diagnostic

### Vérifier la Configuration Git

```bash
# Voir la configuration globale
git config --list --global

# Voir la configuration locale du projet
cd C:\Users\el_ch\git\gedavocat-springboot
git config --list --local

# Voir l'URL du remote
git remote -v

# Tester la connexion HTTPS
git ls-remote https://gitlab.com/ahmed.ghrissi/gedavocat-springboot.git

# Tester la connexion SSH (si configuré)
ssh -T git@gitlab.com
```

---

## 📋 Procédure Recommandée (Pas à Pas)

### **Méthode Rapide avec Personal Access Token**

```bash
# 1. Créer le token sur GitLab (voir Solution 1)

# 2. Mettre à jour le remote avec le token
cd C:\Users\el_ch\git\gedavocat-springboot
git remote set-url origin https://ahmed.ghrissi:VOTRE_TOKEN@gitlab.com/ahmed.ghrissi/gedavocat-springboot.git

# 3. Tester
git pull

# 4. Si ça fonctionne, pousser vos changements
git push
```

---

## ⚠️ Erreurs Courantes et Solutions

### "Authentication failed"
→ Token expiré ou incorrect
→ Recréer un nouveau token sur GitLab

### "Repository not found"
→ L'URL est incorrecte
→ Vérifier sur GitLab que le projet existe

### "Permission denied (publickey)"
→ Clé SSH non configurée ou incorrecte
→ Régénérer et ré-ajouter la clé SSH

### "fatal: Could not read from remote repository"
→ Problème de permissions
→ Vérifier que vous êtes membre du projet

---

## 🚀 Après la Résolution

Une fois connecté avec succès :

```bash
cd C:\Users\el_ch\git\gedavocat-springboot

# 1. Récupérer les dernières modifications
git pull origin main

# 2. Vérifier le statut
git status

# 3. Ajouter vos changements
git add .

# 4. Commiter
git commit -m "🎨 feat: Modernize calendar with blue design system

- Replace violet gradients with professional blue (#1E3A5F → #0F172A)
- Add responsive design for mobile/tablet/desktop
- Implement glassmorphism effects and modern shadows
- Fix transparent cookie banner visibility
- Update all documentation with new color scheme"

# 5. Pousser
git push origin main
```

---

## 📞 Si Rien ne Fonctionne

### Option 1 : Cloner à Nouveau
```bash
# Avec HTTPS et token
git clone https://ahmed.ghrissi:VOTRE_TOKEN@gitlab.com/ahmed.ghrissi/gedavocat-springboot.git gedavocat-new

# Copier vos modifications
# Supprimer l'ancien dossier
# Renommer le nouveau
```

### Option 2 : Créer un Nouveau Remote
```bash
cd C:\Users\el_ch\git\gedavocat-springboot

# Ajouter un nouveau remote avec un nom différent
git remote add gitlab https://ahmed.ghrissi:TOKEN@gitlab.com/ahmed.ghrissi/gedavocat-springboot.git

# Pousser vers ce remote
git push gitlab main
```

---

## 📝 Checklist de Vérification

- [ ] Token créé sur GitLab avec les bons scopes
- [ ] Token copié et sauvegardé en lieu sûr
- [ ] URL du remote mise à jour avec le token
- [ ] Test `git pull` réussi
- [ ] Credentials sauvegardés dans Git Credential Manager
- [ ] `git push` fonctionne

---

## 🎯 Recommandation Finale

**Pour une utilisation professionnelle à long terme :**

1. ✅ Utiliser **SSH** (plus sécurisé, pas besoin de token dans l'URL)
2. ✅ Configurer **Git Credential Manager** pour Windows
3. ✅ Créer des tokens avec **dates d'expiration courtes** et les renouveler régulièrement
4. ✅ Ne **jamais commiter** de tokens dans le code

---

**Besoin d'aide ?** Exécutez les commandes de diagnostic ci-dessus et communiquez les résultats.
