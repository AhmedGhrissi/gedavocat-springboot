# Configuration NVD API Key pour OWASP Dependency Check

## Problème

Le scan OWASP Dependency Check sans clé API NVD prend **20-30 minutes** car il doit télécharger ~500MB de données CVE avec rate-limiting (max 5 requêtes/30s).

## Solution : Clé API NVD (GRATUITE)

Avec une clé API NVD, le scan prend seulement **2-3 minutes** ! 🚀

### Étapes

1. **Créer un compte NVD** (gratuit)
   - Aller sur : https://nvd.nist.gov/developers/request-an-api-key
   - Remplir le formulaire (nom, email, organisation)
   - Valider l'email

2. **Récupérer la clé API**
   - Vous recevrez la clé par email dans les quelques minutes
   - Format : `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

## Configuration GitLab CI/CD (Production)

**Pour activer le scan rapide dans GitLab CI/CD :**

1. Aller dans **Settings > CI/CD > Variables**
2. Cliquer sur **Add variable**
3. Configurer :
   - **Key** : `NVD_API_KEY`
   - **Value** : `90d8d09b-ee85-41fa-8672-37d9b6811d7e`
   - **Type** : Variable
   - **Environment scope** : All (default)
   - **Protected** : ✅ **Yes** (recommandé - seulement branches protégées)
   - **Masked** : ✅ **Yes** (masqué dans les logs)
   - **Expanded** : ❌ No
4. Cliquer sur **Add variable**

**Résultat** : Le job `owasp-dependency-check` passera de 30 min à 2-3 min ! 🚀

---

## Vérification de la configuration

## Configuration locale rapide

**Votre clé API NVD est déjà configurée dans `.env.local` !** 🎉

Pour l'utiliser :

```bash
# Linux/macOS
export NVD_API_KEY="90d8d09b-ee85-41fa-8672-37d9b6811d7e"

# Windows PowerShell
$env:NVD_API_KEY="90d8d09b-ee85-41fa-8672-37d9b6811d7e"

# Puis lancer le scan OWASP
mvn dependency-check:check
```

**Résultat attendu** : Scan complet en **2-3 minutes** au lieu de 30+ minutes ! ⚡

---

## Configuration GitLab CI/CD (Production)

---

## Vérification de la configuration

### Test local

```bash
# Vérifier que la variable est définie
echo $env:NVD_API_KEY  # Windows PowerShell
echo $NVD_API_KEY      # Linux/macOS

# Lancer un scan de test
mvn dependency-check:check -DskipTests

# Vérifier dans les logs :
# [INFO] NVD_API_KEY configurée — scan rapide (~2-3 min)
```

### Test GitLab CI/CD

1. Pousser un commit sur `main`
2. Aller dans **CI/CD > Pipelines**
3. Ouvrir le job `owasp-dependency-check`
4. Vérifier dans les logs :
   ```
   NVD_API_KEY configurée — scan rapide (~2-3 min)
   [INFO] Checking for updates using NVD API
   ```
5. Le job devrait se terminer en **2-3 minutes** au lieu de timeout

---

## Résultat attendu

### Sans clé API
```
[INFO] NVD API has 336,587 records in this update
[INFO] Downloaded 10,000/336,587 (3%)
[INFO] Downloaded 20,000/336,587 (6%)
...
⏱️ Temps : ~20-30 minutes
```

### Avec clé API
```
[INFO] NVD_API_KEY configurée — scan rapide
[INFO] Checking for updates using NVD API
[INFO] Processing 50 records...
⏱️ Temps : ~2-3 minutes
```

## Cache GitLab CI

Le cache NVD est activé dans `.gitlab-ci.yml` :
```yaml
cache:
  key: "owasp-nvd-data"
  paths:
    - .m2/repository/org/owasp/dependency-check-data/
  policy: pull-push
```

**Première exécution** : 20-30 min (téléchargement base NVD)  
**Exécutions suivantes** : 2-3 min (cache réutilisé, mise à jour uniquement)

## Limites API

| Type de clé | Requêtes/30s | Requêtes/jour |
|-------------|-------------|---------------|
| Sans clé    | 5           | Illimité      |
| Clé gratuite| 50          | Illimité      |

Source : https://nvd.nist.gov/developers/start-here

## Dépannage

### Erreur : "NVD API rate limit exceeded"
- Attendre 30 secondes entre les scans
- Vérifier que la clé API est correctement configurée

### Erreur : "Invalid API key"
- Vérifier que la clé n'a pas d'espaces avant/après
- Régénérer une nouvelle clé si nécessaire

### Le scan reste lent même avec la clé
- Vérifier dans les logs GitLab CI si le message "NVD_API_KEY configurée" apparaît
- Vérifier que la variable est bien définie dans Settings > CI/CD > Variables

## Documentation officielle

- OWASP Dependency Check : https://jeremylong.github.io/DependencyCheck/
- NVD API : https://nvd.nist.gov/developers
- Configuration Maven : https://jeremylong.github.io/DependencyCheck/dependency-check-maven/

---

**Note** : La clé API est optionnelle mais **fortement recommandée** pour ne pas bloquer le pipeline CI/CD.
