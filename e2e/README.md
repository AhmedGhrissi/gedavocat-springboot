# 🎭 Tests E2E Playwright - GedAvocat

Tests End-to-End pour valider **l'isolation multi-tenant** (VULN-01).

## 📋 Prérequis

- Node.js 18+ et npm
- Application GedAvocat démarrée sur `http://localhost:8080`

## 🚀 Installation

```bash
# Installer les dépendances
npm install

# Installer les navigateurs Playwright (première fois uniquement)
npx playwright install
```

## ▶️ Exécuter les Tests

### Mode Headless (CI/CD)

```bash
# Tous les tests
npm test

# Test spécifique
npx playwright test tests/multi-tenant-isolation.spec.ts
```

### Mode UI (Interactif)

```bash
# Interface graphique Playwright
npm run test:ui
```

### Mode Debug

```bash
# Avec navigateur visible
npm run test:headed

# Mode debug step-by-step
npm run test:debug
```

## 📊 Rapports

```bash
# Voir le rapport HTML
npm run report
```

Les rapports sont générés dans `playwright-report/`.

## 🧪 Scénarios Testés

### Tests d'Isolation Multi-Tenant (`multi-tenant-isolation.spec.ts`)

| Test | Description | Objectif Sécurité |
|------|-------------|-------------------|
| 01.1 | Créer Cabinet A + Avocat A | Setup isolation |
| 01.2 | Créer Cabinet B + Avocat B | Setup isolation |
| 02.1 | Avocat A crée un dossier | Données Cabinet A |
| 02.2 | Avocat B crée un dossier | Données Cabinet B |
| 02.3 | Avocat A liste dossiers | Voit UNIQUEMENT Cabinet A |
| 02.4 | Avocat B liste dossiers | Voit UNIQUEMENT Cabinet B |
| **02.5** | **Accès URL dossier autre cabinet** | **404 ou 403 (CRITIQUE)** |
| 03.1 | Isolation clients Cabinet A | Voit UNIQUEMENT ses clients |
| 03.2 | Isolation clients Cabinet B | Voit UNIQUEMENT ses clients |
| 03.3 | Accès URL client autre cabinet | 404 ou 403 |
| 04.1 | Upload document Cabinet A | Documents isolés |
| 04.2 | Cabinet B ne voit pas docs A | Isolation documents |
| 05.1 | Quota max avocats Cabinet | Limite 5 avocats respectée |
| 06.1 | Recherche globale filtrée | Résultats filtrés par cabinet |

## 🔧 Configuration

### playwright.config.ts

Modifier `baseURL` si l'application n'est pas sur `localhost:8080`:

```typescript
use: {
  baseURL: 'https://gedavocat-staging.fr',
}
```

### Variables d'Environnement

```bash
# URL de base
export BASE_URL=http://localhost:8080

# Mode CI
export CI=true
```

## 📝 Structure

```
e2e/
├── tests/
│   └── multi-tenant-isolation.spec.ts  # Tests isolation
├── pages/                               # Page Object Model (optionnel)
├── playwright.config.ts                 # Configuration Playwright
├── tsconfig.json                        # Configuration TypeScript
└── package.json                         # Dépendances
```

## 🐛 Debugging

### Trace Viewer

```bash
# Activer traces
PWDEBUG=1 npx playwright test

# Voir les traces d'un test échoué
npx playwright show-trace test-results/.../trace.zip
```

### Screenshots

Les screenshots des tests échoués sont dans `test-results/`.

## 📚 Ressources

- [Playwright Docs](https://playwright.dev)
- [Best Practices E2E](https://playwright.dev/docs/best-practices)
- [VULN-01 Multi-Tenant](../docs/RAPPORT_AUDIT_SECURITE_Phase1.md)

## ✅ Critères de Succès

Pour que l'isolation multi-tenant soit validée, **TOUS les tests doivent passer** :

- ✅ Aucun utilisateur ne voit les données d'un autre cabinet
- ✅ Accès direct par URL à une ressource tierce → **404**
- ✅ Quotas respectés (max avocats, max clients)
- ✅ Recherche filtrée automatiquement par cabinet

**0 tolérance** pour les violations d'isolation !

---

**Dernière mise à jour** : Mars 2026  
**Auteur** : Équipe GedAvocat Security
