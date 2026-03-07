# Configuration OWASP Dependency Check

## Problème
Le scan OWASP Dependency Check échoue ou prend très longtemps car il doit télécharger la base de données NVD (~336 000 CVE).

## Solution : Obtenir une clé API NVD (GRATUIT)

### 1. Demander une clé API
Rendez-vous sur : https://nvd.nist.gov/developers/request-an-api-key

- Créez un compte
- Demandez une clé API (gratuite, limite : 5 requêtes/30s)
- Vous recevrez la clé par email en quelques minutes

### 2. Configurer la clé API

#### Option A : Variable d'environnement (Recommandé)
```bash
# Linux/Mac
export NVD_API_KEY="votre-clé-api-ici"

# Windows PowerShell
$env:NVD_API_KEY="votre-clé-api-ici"

# Windows CMD
set NVD_API_KEY=votre-clé-api-ici
```

#### Option B : Fichier .env (à ne PAS committer)
```bash
# Créer .env à la racine du projet
echo "NVD_API_KEY=votre-clé-api-ici" >> .env
```

Vérifier que `.env` est dans `.gitignore` :
```bash
grep "\.env" .gitignore
```

### 3. Exécuter le scan

#### Avec clé API (rapide - ~2-3 minutes)
```bash
mvn dependency-check:check
```

#### Sans clé API (première fois - peut prendre 30-60 min)
```bash
# Augmenter les timeouts Maven
export MAVEN_OPTS="-Xmx1024m"
mvn dependency-check:check -DskipTests
```

### 4. Consulter les résultats

Le rapport sera généré dans :
- `target/dependency-check-report.html` (rapport visuel)
- `target/dependency-check-report.json` (pour scripts)

## Configuration actuelle (pom.xml)

Le plugin est configuré pour :
- ✅ Utiliser la variable d'environnement `NVD_API_KEY` si disponible
- ✅ Timeout de 5 minutes pour le téléchargement initial
- ✅ Cache de 24h (pas de re-téléchargement)
- ✅ Échec du build si CVE critique (CVSS >= 7.0)
- ✅ Exclusion des dépendances de test

## Suppression de faux positifs

Si le scan détecte des CVE non applicables, ajoutez dans `pom.xml` :

```xml
<configuration>
  <!-- ... config existante ... -->
  <suppressionFiles>
    <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
  </suppressionFiles>
</configuration>
```

Créez `dependency-check-suppressions.xml` :
```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
  <suppress>
    <notes>CVE non applicable car version corrigée</notes>
    <cve>CVE-2024-XXXXX</cve>
  </suppress>
</suppressions>
```

## Alternatives pour CI/CD

### GitHub Actions
```yaml
- name: OWASP Dependency Check
  env:
    NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
  run: mvn dependency-check:check
```

### GitLab CI
```yaml
dependency-check:
  script:
    - export NVD_API_KEY=$NVD_API_KEY
    - mvn dependency-check:check
  variables:
    NVD_API_KEY: $NVD_API_KEY
```

## Dépannage

### Erreur : "Unable to download NVD database"
- Vérifier la connexion Internet
- Vérifier les proxies/firewalls
- Essayer avec la clé API NVD

### Erreur : "Build failed due to CVSS threshold"
- Consulter le rapport HTML pour voir les CVE détectées
- Mettre à jour les dépendances vulnérables
- Si faux positif : ajouter suppression

### Base de données corrompue
```bash
# Supprimer le cache local
rm -rf ~/.m2/repository/org/owasp/dependency-check-data/
```

## Ressources
- Documentation : https://jeremylong.github.io/DependencyCheck/
- NVD API : https://nvd.nist.gov/developers
- Suppressions : https://jeremylong.github.io/DependencyCheck/general/suppression.html
