# 🚀 Guide Rapide - Fix Git "not authorized"

## ⚡ Solution Express (2 minutes)

### Méthode 1️⃣ : Personal Access Token (RECOMMANDÉ)

#### Étape 1 : Créer le Token
1. Allez sur : https://gitlab.com/-/profile/personal_access_tokens
2. Cliquez sur **Add new token**
3. Remplissez :
   - Name : `gedavocat-dev`
   - Expires at : 1 an
   - Scopes : ✅ `read_repository` + ✅ `write_repository`
4. Cliquez **Create personal access token**
5. **COPIEZ LE TOKEN** immédiatement

#### Étape 2 : Utiliser le Script Automatique
```bash
# Double-cliquez sur :
configure-git.bat

# Ou lancez dans cmd :
cd C:\Users\el_ch\git\gedavocat-springboot
configure-git.bat
```

#### Étape 3 : Collez votre Token
- Choisissez l'option **1**
- Collez votre token
- C'est fait ! ✅

---

### Méthode 2️⃣ : Manuelle (si le script ne fonctionne pas)

```bash
cd C:\Users\el_ch\git\gedavocat-springboot

# Remplacez VOTRE_TOKEN par le token copié
git remote set-url origin https://ahmed.ghrissi:VOTRE_TOKEN@gitlab.com/ahmed.ghrissi/gedavocat-springboot.git

# Testez
git pull
```

---

## 🎯 Après la Configuration

```bash
# 1. Vérifier que ça fonctionne
git pull

# 2. Ajouter vos modifications
git add .

# 3. Commiter
git commit -m "🎨 Update calendar design to blue theme"

# 4. Pousser
git push
```

---

## ❓ FAQ Rapide

**Q : Le token ne fonctionne pas ?**  
→ Vérifiez qu'il a les scopes `read_repository` et `write_repository`

**Q : "Repository not found" ?**  
→ Vérifiez que vous êtes membre du projet sur GitLab

**Q : Je veux utiliser SSH ?**  
→ Lancez `configure-git.bat` et choisissez l'option 2

**Q : Où trouver mes tokens existants ?**  
→ https://gitlab.com/-/profile/personal_access_tokens

---

## 📞 Besoin d'Aide ?

Consultez le guide complet : **GIT_AUTH_FIX.md**

Ou exécutez le diagnostic :
```bash
configure-git.bat
# Puis choisissez l'option 4
```

---

**Temps estimé : 2-3 minutes** ⏱️  
**Difficulté : Facile** 🟢
