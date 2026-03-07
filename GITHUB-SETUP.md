# Configuration GitHub - GED Avocat

## ✅ Repository GitHub Configuré

**URL**: https://github.com/AhmedGhrissi/gedavocat-springboot

---

## 🔧 Configuration des Secrets GitHub

Pour activer les workflows GitHub Actions (CI/CD et Security Scanning), vous devez configurer les secrets suivants :

### Étape 1: Accéder aux Secrets

1. Aller sur : https://github.com/AhmedGhrissi/gedavocat-springboot
2. Cliquer sur **Settings** (⚙️)
3. Dans le menu de gauche : **Secrets and variables** → **Actions**
4. Cliquer sur **New repository secret**

---

## 🔐 Secrets Requis

### 1. SNYK_TOKEN (Obligatoire pour Snyk scan)

**Description**: Token d'authentification Snyk pour scan de vulnérabilités

**Comment l'obtenir**:
1. S'inscrire sur https://snyk.io (gratuit)
2. Aller sur https://app.snyk.io/account
3. Section **Auth Token** → Cliquer sur **Show** puis **Copy**

**Configuration GitHub**:
```
Name: SNYK_TOKEN
Value: <votre-token-snyk>
```

---

### 2. NVD_API_KEY (Recommandé pour OWASP Dependency Check)

**Description**: Clé API NVD pour téléchargement rapide de la base CVE

**Comment l'obtenir**:
1. Aller sur https://nvd.nist.gov/developers/request-an-api-key
2. Remplir le formulaire (email requis)
3. Recevoir la clé par email (quelques minutes)

**Configuration GitHub**:
```
Name: NVD_API_KEY
Value: <votre-clé-nvd>
```

---

### 3. GITGUARDIAN_API_KEY (Optionnel - détection de secrets)

**Description**: Token GitGuardian pour détection de secrets dans le code

**Comment l'obtenir**:
1. S'inscrire sur https://www.gitguardian.com/ (gratuit pour projets publics)
2. Tableau de bord → API → Generate new API key

**Configuration GitHub**:
```
Name: GITGUARDIAN_API_KEY
Value: <votre-token-gitguardian>
```

---

## 🚀 Workflows GitHub Actions Configurés

### 1. **CI/CD Pipeline** (`.github/workflows/ci-cd.yml`)

**Déclenchement**:
- Push sur `main` ou `develop`
- Pull Requests vers `main`

**Jobs**:
- ✅ **Build & Test** - Compilation et tests unitaires
- 📦 **Package** - Création du JAR (branch main uniquement)
- 🐳 **Docker Build** - Construction de l'image Docker (branch main uniquement)

**Status**: [![CI/CD](https://github.com/AhmedGhrissi/gedavocat-springboot/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/AhmedGhrissi/gedavocat-springboot/actions/workflows/ci-cd.yml)

---

### 2. **Security Scanning** (`.github/workflows/security-scan.yml`)

**Déclenchement**:
- Push sur `main` ou `develop`
- Pull Requests vers `main`
- Quotidien à 2h du matin (UTC)

**Jobs**:
- 🛡️ **Snyk** - Scan de vulnérabilités (CVE)
- 🔍 **Trivy** - Scan filesystem et dépendances
- 🔒 **CodeQL** - Analyse statique de code (SAST)
- 📋 **OWASP Dependency Check** - Vérification CVE des dépendances
- 🔑 **GitGuardian** - Détection de secrets exposés

**Status**: [![Security](https://github.com/AhmedGhrissi/gedavocat-springboot/actions/workflows/security-scan.yml/badge.svg)](https://github.com/AhmedGhrissi/gedavocat-springboot/actions/workflows/security-scan.yml)

---

## 📊 Consultation des Résultats

### GitHub Security Tab

1. Aller sur : https://github.com/AhmedGhrissi/gedavocat-springboot
2. Cliquer sur **Security** (🛡️)
3. **Vulnerability alerts** → Voir les CVE détectées
4. **Code scanning** → Résultats CodeQL, Snyk, Trivy

### GitHub Actions

1. Aller sur : https://github.com/AhmedGhrissi/gedavocat-springboot/actions
2. Voir l'historique des workflows
3. Cliquer sur un run pour voir les détails
4. Télécharger les artifacts (rapports HTML, JSON)

---

## 🔄 Synchronisation GitHub ↔ GitLab

Le projet est maintenant disponible sur **2 plateformes** :

### GitHub (Principal pour CI/CD)
- **URL**: https://github.com/AhmedGhrissi/gedavocat-springboot
- **Remote**: `github`
- **CI/CD**: GitHub Actions

### GitLab (Principal pour deployment)
- **URL**: https://gitlab.com/ahmed.ghrissi/gedavocat-springboot
- **Remote**: `origin`
- **CI/CD**: GitLab CI/CD

### Commandes Git

```bash
# Voir les remotes
git remote -v

# Push vers GitHub
git push github main

# Push vers GitLab
git push origin main

# Push vers les deux simultanément
git push github main && git push origin main

# Ou configurer push multiple
git remote set-url --add --push origin https://github.com/AhmedGhrissi/gedavocat-springboot.git
git remote set-url --add --push origin https://gitlab.com/ahmed.ghrissi/gedavocat-springboot.git
git push origin main  # Pousse vers les 2
```

---

## 📝 Next Steps

### 1. Configurer les Secrets (15 min)
- [ ] Ajouter `SNYK_TOKEN`
- [ ] Ajouter `NVD_API_KEY`
- [ ] Ajouter `GITGUARDIAN_API_KEY` (optionnel)

### 2. Activer GitHub Security Features
- [ ] Settings → Code security and analysis
- [ ] Activer **Dependabot alerts**
- [ ] Activer **Dependabot security updates**
- [ ] Activer **Secret scanning**

### 3. Vérifier les Workflows
- [ ] Aller sur Actions tab
- [ ] Vérifier que les workflows se lancent
- [ ] Consulter les résultats dans Security tab

### 4. Configurer Branch Protection (Recommandé)
```
Settings → Branches → Add rule

Branch name pattern: main

Protection rules:
☑ Require a pull request before merging
☑ Require status checks to pass before merging
  → Select: CI/CD Pipeline
  → Select: Security Scanning
☑ Require conversation resolution before merging
☑ Include administrators
```

---

## 🆘 Troubleshooting

### Workflow Failed: "SNYK_TOKEN not found"

**Solution**: Ajouter le secret `SNYK_TOKEN` dans Settings → Secrets

### CodeQL Analysis Failed

**Cause**: Problème de compilation Java

**Solution**: Vérifier que le build Maven réussit localement
```bash
mvn clean compile -DskipTests
```

### OWASP Dependency Check Timeout

**Solution**: Ajouter `NVD_API_KEY` pour accélérer le téléchargement NVD

---

## 📚 Documentation

- [GitHub Actions Docs](https://docs.github.com/en/actions)
- [GitHub Security Features](https://docs.github.com/en/code-security)
- [Snyk Documentation](https://docs.snyk.io/)
- [OWASP Dependency Check](https://jeremylong.github.io/DependencyCheck/)

---

## ✅ Summary

- ✅ Code poussé sur GitHub
- ✅ GitHub Actions workflows configurés
- ✅ README mis à jour avec badges
- ⏳ Secrets à configurer manuellement
- ⏳ Security features à activer

**Prochaine action**: Configurer les secrets GitHub pour activer les scans de sécurité automatiques.
