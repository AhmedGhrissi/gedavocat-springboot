# Guide de Scan de Sécurité - GitLab & Alternatives

## 🎯 Vue d'ensemble

Ce guide présente les solutions de scan de sécurité pour le projet GED Avocat, alternatives à OWASP Dependency Check qui rencontre des problèmes techniques.

---

## 📊 Solution 1: GitLab Security Scanning (Recommandé)

### Pour GitLab Premium/Ultimate

GitLab offre des scans de sécurité intégrés :

#### A. Activer les scans automatiques

1. **Créer/Modifier `.gitlab-ci.yml`** à la racine du projet :

```yaml
include:
  - template: Security/SAST.gitlab-ci.yml
  - template: Security/Dependency-Scanning.gitlab-ci.yml
  - template: Security/Secret-Detection.gitlab-ci.yml
  - template: Security/Container-Scanning.gitlab-ci.yml
```

2. **Pousser les modifications** :
```bash
git add .gitlab-ci.yml
git commit -m "feat: Enable GitLab Security Scanning"
git push origin main
```

3. **Consulter les résultats** :
   - GitLab UI → **Security & Compliance** → **Vulnerability Report**
   - Dans les Merge Requests : onglet **Security**
   - Pipelines → Jobs → Artifacts

#### B. Configuration avancée

```yaml
variables:
  DS_EXCLUDED_PATHS: "spec, test, tests, tmp"
  SAST_EXCLUDED_PATHS: "test/**, **/test/**"
  
dependency_scanning:
  variables:
    DS_JAVA_VERSION: 17
    DS_REMEDIATE: "true"  # Suggestions de corrections
```

### Pour GitLab Free

Utiliser **Trivy Scanner** (gratuit, open-source) :

```yaml
trivy-scan:
  stage: security
  image: aquasec/trivy:latest
  script:
    - trivy fs --exit-code 0 --format json --output trivy-report.json .
    - trivy fs --severity HIGH,CRITICAL .
  artifacts:
    reports:
      container_scanning: trivy-report.json
```

---

## 🛡️ Solution 2: Snyk (Gratuit pour Open Source)

### Installation et Configuration

#### Étape 1: Inscription
```bash
# Ouvrir https://snyk.io/signup
# Créer un compte gratuit (GitHub/GitLab/Email)
```

#### Étape 2: Authentication
```bash
snyk auth
# Une page web s'ouvre pour authentification
```

#### Étape 3: Scanner le projet
```bash
cd c:\Users\el_ch\git\gedavocat-springboot

# Scan Maven
snyk test --all-projects

# Générer rapport JSON
snyk test --all-projects --json | Out-File -FilePath target\snyk-report.json

# Générer rapport HTML
snyk test --all-projects --json | snyk-to-html -o target\snyk-report.html
```

#### Étape 4: Scan continu (CI/CD)

**Pour GitLab CI** :
```yaml
snyk-security:
  stage: security
  image: snyk/snyk:maven-3-jdk-17
  script:
    - snyk auth $SNYK_TOKEN
    - snyk test --all-projects --severity-threshold=high
  only:
    - merge_requests
    - main
```

**Variables à configurer** :
- GitLab → Settings → CI/CD → Variables
- Ajouter : `SNYK_TOKEN` (obtenir depuis https://app.snyk.io/account)

### Commandes Snyk utiles

```bash
# Tester les dépendances
snyk test

# Fixer automatiquement les vulnérabilités
snyk wizard

# Monitorer le projet (surveillance continue)
snyk monitor

# Tester uniquement les vulnérabilités HIGH/CRITICAL
snyk test --severity-threshold=high

# Ignorer les dev dependencies
snyk test --dev
```

---

## 🔧 Solution 3: Alternatives Locales (Sans Compte)

### A. Maven Versions Plugin

Scanner les dépendances obsolètes :

```bash
# Afficher les mises à jour disponibles
mvn versions:display-dependency-updates

# Vérifier les vulnérabilités connues
mvn versions:display-plugin-updates

# Générer rapport
mvn versions:dependency-updates-report
```

### B. SpotBugs + Find Security Bugs

Ajouter au `pom.xml` :

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.6.2</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <plugins>
            <plugin>
                <groupId>com.h3xstream.findsecbugs</groupId>
                <artifactId>findsecbugs-plugin</artifactId>
                <version>1.13.0</version>
            </plugin>
        </plugins>
    </configuration>
</plugin>
```

Exécuter :
```bash
mvn spotbugs:check
mvn spotbugs:gui  # Interface graphique
```

### C. PMD avec règles de sécurité

```bash
mvn pmd:check
mvn pmd:cpd-check  # Détection de code dupliqué
```

### D. SonarQube Communauté (Local)

```bash
# Installer SonarQube localement
docker run -d -p 9000:9000 sonarqube:community

# Scanner le projet
mvn sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=admin \
  -Dsonar.password=admin
```

---

## 📋 Comparaison des Solutions

| Solution | Gratuit | Scan CVE | SAST | Secrets | CI/CD |
|----------|---------|----------|------|---------|-------|
| **GitLab Security (Premium)** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **GitLab + Trivy (Free)** | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| **Snyk** | ✅* | ✅ | ✅ | ✅ | ✅ |
| **OWASP Dependency Check** | ✅ | ✅ | ❌ | ❌ | ⚠️ |
| **SpotBugs** | ✅ | ❌ | ✅ | ⚠️ | ✅ |
| **SonarQube Community** | ✅ | ⚠️ | ✅ | ⚠️ | ✅ |

*Gratuit pour projets open-source, limité pour privés

---

## 🚀 Recommandations

### Pour ce projet (GitLab)

#### Option 1: GitLab Premium (Entreprise)
✅ **Configuration complète** avec tous les scans automatiques
```yaml
include:
  - template: Security/SAST.gitlab-ci.yml
  - template: Security/Dependency-Scanning.gitlab-ci.yml
  - template: Security/Secret-Detection.gitlab-ci.yml
```

#### Option 2: GitLab Free + Snyk
✅ **Scan CVE avec Snyk** + **SAST avec GitLab**
```yaml
include:
  - template: Security/SAST.gitlab-ci.yml

snyk-security:
  stage: security
  image: snyk/snyk:maven-3-jdk-17
  script:
    - snyk auth $SNYK_TOKEN
    - snyk test --all-projects
```

#### Option 3: Tout local (développement)
✅ **SpotBugs + Versions Plugin**
```bash
# Vérifier vulnérabilités
mvn versions:display-dependency-updates
mvn spotbugs:check
```

---

## 📖 Ressources

### Documentation
- **GitLab Security**: https://docs.gitlab.com/ee/user/application_security/
- **Snyk**: https://docs.snyk.io/
- **Trivy**: https://aquasecurity.github.io/trivy/
- **OWASP**: https://owasp.org/www-project-dependency-check/

### Obtenir les outils
- **Snyk Account**: https://snyk.io/signup
- **GitLab Trial**: https://about.gitlab.com/free-trial/
- **Trivy Releases**: https://github.com/aquasecurity/trivy/releases

---

## ⚡ Quick Start

### Scan immédiat (local)

```bash
# 1. Versions obsolètes
mvn versions:display-dependency-updates

# 2. Snyk (après auth)
snyk test --all-projects

# 3. SpotBugs sécurité
mvn com.github.spotbugs:spotbugs-maven-plugin:4.8.6.2:check
```

### Scan CI/CD (GitLab)

```bash
# Push .gitlab-ci.yml avec security scanning
git add .gitlab-ci.yml
git commit -m "feat: Enable security scanning"
git push

# Voir résultats dans GitLab UI
# Security & Compliance → Vulnerability Report
```

---

## 🔒 Configuration Snyk pour ce projet

### Fichier `.snyk` (configuration projet)

```yaml
# Snyk (https://snyk.io) policy file, patches or ignores known vulnerabilities.
version: v1.25.0

ignore:
  # Exemple: ignorer temporairement une vulnérabilité
  'SNYK-JAVA-ORGSPRINGFRAMEWORK-12345':
    - '*':
        reason: 'Non applicable, version corrigée en cours de test'
        expires: '2026-04-07T00:00:00.000Z'

patch: {}
```

### Intégration GitLab ↔ Snyk

```bash
# Connecter Snyk à GitLab
# 1. GitLab → Settings → Integrations → Snyk
# 2. Snyk → Settings → Integrations → GitLab
# 3. Autoriser l'accès

# Importer le projet dans Snyk
snyk monitor --all-projects
```

---

**Terminé ! Choisissez la solution qui convient le mieux à vos besoins.**
