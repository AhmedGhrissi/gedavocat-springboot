# 🔒 AUDIT DE SÉCURITÉ MILITAIRE — GedAvocat / DocAvocat
## Rapport complet : Services (39 fichiers) + Templates (81 fichiers)
### Date : 2026-01-XX | Auditeur : Analyse automatisée Copilot

---

## RÉSUMÉ EXÉCUTIF

| Catégorie | CRITIQUE | HIGH | MEDIUM | LOW | INFO |
|-----------|----------|------|--------|-----|------|
| **Services (39 fichiers)** | 1 | 2 | 4 | 3 | 2 |
| **Templates (81 fichiers)** | 1 | 3 | 3 | 2 | 3 |
| **TOTAL** | **2** | **5** | **7** | **5** | **5** |

**Score global : 72/100** — Le code a subi un durcissement sécuritaire important (nombreux correctifs SEC-* visibles), mais des vulnérabilités résiduelles demeurent.

---

# PARTIE 1 — SERVICES JAVA (39 fichiers)

---

## [S-01] CRITIQUE — Backdoor ADMIN_BYPASS dans InvoiceService

- **Fichier** : [InvoiceService.java](src/main/java/com/gedavocat/service/InvoiceService.java#L175)
- **Sévérité** : 🔴 CRITIQUE
- **Catégorie** : Contrôle d'accès manquant / Backdoor

**Code vulnérable :**
```java
// Bypass pour les admins
if ("ADMIN_BYPASS".equals(lawyerId)) {
    return convertToResponse(invoice);
}
```

**Problème** : N'importe quel appelant qui passe la chaîne littérale `"ADMIN_BYPASS"` comme `lawyerId` contourne entièrement la vérification de propriété. Si un contrôleur construit mal le `lawyerId` ou si un attaquant influence ce paramètre, toutes les factures deviennent accessibles.

**Recommandation** :
```java
// Vérifier le rôle réel de l'utilisateur via le SecurityContext
if (SecurityContextHolder.getContext().getAuthentication()
        .getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
    return convertToResponse(invoice);
}
```

---

## [S-02] HIGH — DoS par lecture intégrale de fichier log (LogService)

- **Fichier** : [LogService.java](src/main/java/com/gedavocat/service/LogService.java#L48-L58)
- **Sévérité** : 🟠 HIGH
- **Catégorie** : I/O fichier non sécurisé / DoS

**Code vulnérable :**
```java
List<String> allLines = new ArrayList<>();
String line;
while ((line = reader.readLine()) != null) {
    allLines.add(line);
}
int start = Math.max(0, allLines.size() - maxLines);
List<String> lastLines = allLines.subList(start, allLines.size());
```

**Problème** : Le fichier de log entier est chargé en mémoire avant d'extraire les N dernières lignes. Un fichier log de plusieurs Go provoquera un OutOfMemoryError.

**Recommandation** : Utiliser `RandomAccessFile` pour lire depuis la fin du fichier, ou une file circulaire (Guava `EvictingQueue`) :
```java
try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile, StandardCharsets.UTF_8)) {
    List<String> lastLines = new ArrayList<>(maxLines);
    String line;
    while (lastLines.size() < maxLines && (line = reader.readLine()) != null) {
        lastLines.add(0, line);
    }
    // ... parser lastLines
}
```

---

## [S-03] HIGH — Requêtes non bornées chargeant tous les utilisateurs (AdminMetricsService / UserService)

- **Fichier** : [AdminMetricsService.java](src/main/java/com/gedavocat/service/AdminMetricsService.java#L395-L410)
- **Sévérité** : 🟠 HIGH
- **Catégorie** : Requêtes non bornées / DoS

**Code vulnérable :**
```java
long lawyersCount = userRepository.findAll().stream()
    .filter(u -> "LAWYER".equals(u.getRole() != null ? u.getRole().name() : ""))
    .count();
long clientsCount = userRepository.findAll().stream()
    .filter(u -> "CLIENT".equals(u.getRole() != null ? u.getRole().name() : ""))
    .count();
long adminsCount = userRepository.findAll().stream()
    .filter(u -> "ADMIN".equals(u.getRole() != null ? u.getRole().name() : ""))
    .count();
```

**Problème** : `findAll()` est appelé **3 fois**, chargeant TOUS les utilisateurs à chaque appel. Avec des milliers d'utilisateurs, cela provoque une surcharge mémoire et DB. Le même problème est observé avec `caseRepository.findAll()` (4 appels supplémentaires).

**Recommandation** : Créer des méthodes de repository dédiées :
```java
@Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
long countByRole(@Param("role") Role role);
```

---

## [S-04] MEDIUM — Exposition de l'URL de base de données (AdminMetricsService)

- **Fichier** : [AdminMetricsService.java](src/main/java/com/gedavocat/service/AdminMetricsService.java)
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Fuite d'informations sensibles

**Problème** : La méthode `getDatabaseUrl()` expose l'URL JDBC complète (incluant potentiellement host, port, nom de base) via l'interface admin. Même si protégée par le rôle ADMIN, c'est un vecteur de reconnaissance en cas de compromission du compte admin.

**Recommandation** : Masquer partiellement l'URL :
```java
public String getDatabaseUrl() {
    String url = dataSource.getConnection().getMetaData().getURL();
    return url.replaceAll("//(.+?)@", "//***@")
              .replaceAll("password=[^&]+", "password=***");
}
```

---

## [S-05] MEDIUM — Confiance aveugle dans X-Forwarded-For (AuditService)

- **Fichier** : [AuditService.java](src/main/java/com/gedavocat/service/AuditService.java#L108-L120)
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Usurpation d'identité / IP Spoofing

**Code vulnérable :**
```java
private String getClientIpAddress(HttpServletRequest request) {
    String[] headers = {
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        ...
    };
```

**Problème** : Les en-têtes `X-Forwarded-For` et similaires sont librement manipulables par le client. Sans reverse proxy de confiance, un attaquant peut usurper l'IP dans les logs d'audit — rendant la traçabilité inutile.

**Recommandation** : Configurer Spring comme `ForwardedHeaderFilter` avec une liste de proxys de confiance, ou n'accepter `X-Forwarded-For` que si l'IP source correspond à votre load-balancer :
```java
// Dans SecurityConfig
@Bean
ForwardedHeaderFilter forwardedHeaderFilter() {
    return new ForwardedHeaderFilter();
}
// Et dans application.properties:
server.forward-headers-strategy=NATIVE
server.tomcat.remoteip.internal-proxies=10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}
```

---

## [S-06] MEDIUM — Clés API en mémoire non chiffrées (SettingsService)

- **Fichier** : [SettingsService.java](src/main/java/com/gedavocat/service/SettingsService.java)
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Secrets hardcodés / non protégés

**Problème** : Les clés API Yousign sont stockées dans un `ConcurrentHashMap` en mémoire. Elles sont perdues au redémarrage (pas de persistance) et visibles en clair dans un heap dump.

**Recommandation** :
- Chiffrer les clés avant stockage (via `SecureCryptographyService` déjà présent)
- Persister en base avec chiffrement AES-256-GCM
- Purger les clés de la mémoire après utilisation avec `Arrays.fill()`

---

## [S-07] MEDIUM — Construction d'URL par String.format non validée (RPVAService)

- **Fichier** : [RPVAService.java](src/main/java/com/gedavocat/service/RPVAService.java#L165)
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Injection potentielle dans URL

**Problème** : Les paramètres `postalCode` et `jurisdictionType` sont insérés dans une URL via `String.format()` sans validation/encodage. Un `postalCode` contenant `/../` ou des caractères spéciaux pourrait manipuler l'URL cible.

**Recommandation** : Encoder les paramètres avec `UriComponentsBuilder` :
```java
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .path("/jurisdictions")
    .queryParam("postalCode", postalCode)
    .queryParam("type", jurisdictionType)
    .build()
    .toUriString();
```

---

## [S-08] LOW — Utilisation de Math.random() au lieu de SecureRandom (PRAService)

- **Fichier** : [PRAService.java](src/main/java/com/gedavocat/service/PRAService.java#L360)
- **Sévérité** : 🟢 LOW
- **Catégorie** : Aléatoire non sécurisé

**Code vulnérable :**
```java
// Simulation de sauvegarde
double successRate = Math.random();
```

**Problème** : `Math.random()` utilise un PRNG prévisible. Même si c'est pour une simulation, dans un context de sécurité (PRA/Plan de Reprise d'Activité), le résultat pourrait être deviné.

**Recommandation** : Remplacer par `SecureRandom.nextDouble()`.

---

## [S-09] LOW — Utilisation de Math.random() pour scoring risque (LABFTService)

- **Fichier** : [LABFTService.java](src/main/java/com/gedavocat/service/LABFTService.java#L280-L290)
- **Sévérité** : 🟢 LOW
- **Catégorie** : Aléatoire non sécurisé

**Problème** : Des fonctions de simulation de scoring anti-blanchiment utilisent `Math.random()`. Si ces simulations sont exécutées en production, un attaquant pourrait influencer les résultats.

**Recommandation** : Remplacer par `SecureRandom`, ou clairement marquer et désactiver le code de simulation en production.

---

## [S-10] LOW — Race condition sur generateInvoiceNumber (InvoiceService)

- **Fichier** : [InvoiceService.java](src/main/java/com/gedavocat/service/InvoiceService.java)
- **Sévérité** : 🟢 LOW
- **Catégorie** : TOCTOU / Concurrence

**Problème** : Bien que la méthode `generateInvoiceNumber()` soit `synchronized`, elle utilise `count() + 1` pour générer le numéro. En cas de suppression concurrente de factures, on peut obtenir des numéros en double.

**Recommandation** : Utiliser une séquence SQL dédiée (`AUTO_INCREMENT` ou `SEQUENCE`) au lieu de la logique applicative.

---

## [S-11] INFO — Logique de seuil mémoire inversée (AdminMetricsService)

- **Fichier** : [AdminMetricsService.java](src/main/java/com/gedavocat/service/AdminMetricsService.java#L375-L380)
- **Sévérité** : ℹ️ INFO

**Code :**
```java
if (memoryUsagePercent > 90) {
    health.put("memory", "WARNING");
} else if (memoryUsagePercent > 95) {
    health.put("memory", "CRITICAL");
}
```

**Problème** : Le seuil CRITICAL (>95%) ne sera jamais atteint car il est dans le `else if` du seuil WARNING (>90%). Les deux conditions sont dans le mauvais ordre.

**Recommandation** : Inverser l'ordre des conditions :
```java
if (memoryUsagePercent > 95) {
    health.put("memory", "CRITICAL");
} else if (memoryUsagePercent > 90) {
    health.put("memory", "WARNING");
}
```

---

## [S-12] INFO — Tokens d'invitation en mémoire (ClientInvitationService)

- **Fichier** : [ClientInvitationService.java](src/main/java/com/gedavocat/service/ClientInvitationService.java)
- **Sévérité** : ℹ️ INFO

**Problème** : Les tokens d'invitation sont d'abord stockés dans un `ConcurrentHashMap` en mémoire. Bien qu'un fallback DB existe, un redémarrage du serveur entre la création et l'acceptation d'une invitation pourrait causer des pertes.

---

# PARTIE 2 — TEMPLATES THYMELEAF (81 fichiers)

---

## [T-01] CRITIQUE — Formulaire POST sans th:action (CSRF bypassable) (calendar.html)

- **Fichier** : [appointments/calendar.html](src/main/resources/templates/appointments/calendar.html#L540)
- **Sévérité** : 🔴 CRITIQUE
- **Catégorie** : CSRF / Formulaire non sécurisé

**Code vulnérable :**
```html
<form action="/appointments/create" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
```

**Problème** : Le formulaire utilise `action="/appointments/create"` (URL hardcodée) au lieu de `th:action="@{/appointments/create}"`. Bien que le token CSRF soit bien inclus, l'URL hardcodée :
1. **N'inclut pas le context path** si l'application est déployée sous un sous-chemin (ex: `/app/`)
2. **Contourne le mécanisme de résolution d'URL** de Thymeleaf
3. **N'est pas protégée** contre les changements de context

Note : Le token CSRF est présent (L541), ce qui atténue l'impact. Sévérité réduite à HIGH si le déploiement est toujours à la racine.

**Recommandation** :
```html
<form th:action="@{/appointments/create}" method="post">
```

---

## [T-02] HIGH — innerHTML avec données utilisateur non sûres (admin/users.html)

- **Fichier** : [admin/users.html](src/main/resources/templates/admin/users.html#L642)
- **Sévérité** : 🟠 HIGH
- **Catégorie** : XSS via innerHTML

**Code vulnérable :**
```javascript
document.getElementById('vu-accountEnabled').innerHTML = u.accountEnabled
    ? '<span class="badge bg-success">Actif</span>'
    : '<span class="badge bg-danger">Bloqué</span>';
```

**Problème** : Bien que la valeur `u.accountEnabled` soit un booléen ici (safe), le pattern général d'utilisation de `innerHTML` est dangereux. Plus important, d'autres champs comme `u.firstName` et `u.lastName` sont affichés via `textContent` (safe), mais la construction de l'URL éditeur se fait via :
```javascript
document.getElementById('editUserForm').action = '/admin/users/' + userId + '/edit';
```
L'`userId` provient d'un attribut `data-user-id` du DOM, ce qui est relativement sûr, mais la concaténation d'URL côté client reste un vecteur d'injection.

**Recommandation** : Utiliser `textContent` au lieu de `innerHTML` partout où possible, et encoder les paramètres d'URL :
```javascript
document.getElementById('editUserForm').action = '/admin/users/' + encodeURIComponent(userId) + '/edit';
```

---

## [T-03] HIGH — URL non validée dans th:href (invoices/show.html)

- **Fichier** : [invoices/show.html](src/main/resources/templates/invoices/show.html#L323)
- **Sévérité** : 🟠 HIGH
- **Catégorie** : Open Redirect / XSS

**Code vulnérable :**
```html
<!-- SEC FIX H-07 : validation URL pour prévenir XSS via javascript: -->
<a th:if="${invoice.documentUrl != null and (invoice.documentUrl.startsWith('http://') 
    or invoice.documentUrl.startsWith('https://') or invoice.documentUrl.startsWith('/'))}" 
   th:href="${invoice.documentUrl}" target="_blank" rel="noopener noreferrer" class="pdf-badge">
```

**Problème** : Malgré le fix H-07 qui bloque `javascript:`, la validation est insuffisante :
1. `http://` est accepté → permet des liens vers des sites malveillants (Open Redirect / phishing)
2. Un `documentUrl` comme `//evil.com/fake.pdf` passerait la validation (commence par `/`)
3. `th:href="${...}"` insère l'URL telle quelle sans encodage supplémentaire

**Recommandation** :
```html
<a th:if="${invoice.documentUrl != null and invoice.documentUrl.startsWith('/')}" 
   th:href="@{__${invoice.documentUrl}__}" target="_blank" rel="noopener noreferrer">
```
Ou mieux, valider côté serveur dans `InvoiceService` que l'URL est bien relative au domaine.

---

## [T-04] HIGH — Formulaires sans th:action utilisant action hardcodé

- **Fichiers** :
  - [layout.html](src/main/resources/templates/layout.html#L256) : `<form action="/documents" method="get">`
  - [invoices/index.html](src/main/resources/templates/invoices/index.html#L86) : `<form method="get" action="/invoices">`
  - [admin/users.html](src/main/resources/templates/admin/users.html#L104) : `<form method="get" action="/admin/users">`
  - [admin/logs.html](src/main/resources/templates/admin/logs.html#L117) : `<form method="get" action="/admin/logs">`
- **Sévérité** : 🟠 HIGH (si déploiement sous context path) / 🟡 MEDIUM (si racine uniquement)
- **Catégorie** : URL hardcodées

**Problème** : Ces formulaires GET utilisent des URLs hardcodées qui ne respectent pas le context path de l'application. Les fonctionnalités de recherche/filtrage seraient cassées si l'app est déployée sous `/gedavocat/`.

**Recommandation** : Remplacer systématiquement par `th:action="@{...}"` :
```html
<form th:action="@{/documents}" method="get">
```

---

## [T-05] MEDIUM — Exposition de l'URL complète de base de données (admin/dashboard.html + admin/database.html)

- **Fichiers** :
  - [admin/dashboard.html](src/main/resources/templates/admin/dashboard.html#L249) :
    ```html
    <td th:text="${metrics.databaseUrl}" style="max-width: 300px;">jdbc:mysql://localhost:3306/gedavocat</td>
    ```
  - [admin/database.html](src/main/resources/templates/admin/database.html#L70-L72) :
    ```html
    <td th:text="${metrics != null ? metrics.databaseUrl : 'N/A'}">
        jdbc:mysql://localhost:3306/gedavocat
    </td>
    ```
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Information Disclosure

**Problème** : L'URL JDBC complète est affichée dans les pages d'administration. Même si ces pages sont protégées par le rôle ADMIN, en cas de compromission du compte admin ou de screenshot, cette information facilite les attaques ciblées sur la base de données.

**Recommandation** : Masquer partiellement : afficher uniquement le type de base et le statut de connexion, pas l'URL complète. Si nécessaire, afficher `jdbc:mysql://***:****/gedavocat`.

---

## [T-06] MEDIUM — Formulaires JavaScript-only sans th:action (invoices/new.html, invoices/edit.html, layout.html)

- **Fichiers** :
  - [invoices/new.html](src/main/resources/templates/invoices/new.html#L19) : `<form id="invoiceForm">`
  - [invoices/edit.html](src/main/resources/templates/invoices/edit.html#L135) : `<form id="editForm" onsubmit="submitForm(event)">`
  - [layout.html](src/main/resources/templates/layout.html#L433) : `<form id="changePasswordForm">`
  - [client-portal/profile.html](src/main/resources/templates/client-portal/profile.html#L114) : `<form id="clientPasswordForm">`
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Client-only auth check / Dégradation gracieuse impossible

**Problème** : Ces formulaires n'ont aucun attribut `action` et dépendent entièrement du JavaScript pour la soumission via `fetch()`. Si JS est désactivé ou échoue, les formulaires sont non fonctionnels. De plus, la validation de sécurité (CSRF, authentification) est gérée uniquement côté JS.

**Atténuation** : Les endpoints API appelés par ces fetch() incluent bien le token CSRF dans les headers. Le risque principal est l'absence de dégradation gracieuse.

**Recommandation** : Ajouter `th:action` comme fallback et `method="post"` pour la dégradation gracieuse, tout en conservant le traitement JS.

---

## [T-07] MEDIUM — CDN externes sans Subresource Integrity (SRI)

- **Fichiers** :
  - [huissier-portal/calendar.html](src/main/resources/templates/huissier-portal/calendar.html#L105) :
    ```html
    <script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.10/index.global.min.js"></script>
    ```
  - [huissier-portal/accept-invitation.html](src/main/resources/templates/huissier-portal/accept-invitation.html#L8-L9) :
    ```html
    <link href="https://fonts.googleapis.com/css2?..." rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    ```
  - [home.html](src/main/resources/templates/home.html#L19) :
    ```html
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    ```
  - [landing.html](src/main/resources/templates/landing.html#L118) :
    ```html
    <link href="https://fonts.googleapis.com/css2?..." rel="stylesheet">
    ```
- **Sévérité** : 🟡 MEDIUM
- **Catégorie** : Supply chain / Intégrité des ressources

**Problème** : Les ressources JS/CSS sont chargées depuis des CDN externes sans attribut `integrity` (SRI). Si un CDN est compromis, du code malveillant peut être injecté.

**Recommandation** : Ajouter les attributs `integrity` et `crossorigin` :
```html
<script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.10/index.global.min.js"
        integrity="sha384-HASH_ICI" crossorigin="anonymous"></script>
```
Ou mieux, héberger les fichiers localement.

---

## [T-08] LOW — Absence de Content-Security-Policy (layout.html)

- **Fichier** : [layout.html](src/main/resources/templates/layout.html)
- **Sévérité** : 🟢 LOW
- **Catégorie** : Headers sécuritaires manquants

**Problème** : Aucun en-tête `Content-Security-Policy` n'est défini, ni en meta tag ni côté serveur. L'application contient de nombreux blocs `<script>` inline et des chargements CDN externes. Sans CSP, tout XSS exploitable n'est pas mitigé par le navigateur.

**Recommandation** : Implémenter une CSP progressive. Commencer en mode report-only :
```
Content-Security-Policy-Report-Only: 
    default-src 'self'; 
    script-src 'self' 'nonce-{RANDOM}' https://cdn.jsdelivr.net; 
    style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; 
    font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; 
    img-src 'self' data:; 
    connect-src 'self';
```

---

## [T-09] LOW — Inline JavaScript avec insertAdjacentHTML non sanitisé (invoices/edit.html)

- **Fichier** : [invoices/edit.html](src/main/resources/templates/invoices/edit.html#L253-L279)
- **Sévérité** : 🟢 LOW
- **Catégorie** : XSS potentiel via template literals

**Code :**
```javascript
const initItems = /*[[${invoice.items}]]*/ [];
// ...
function addItem(desc='', qty=1, price=0, tva=20) {
    const html = `<div class="item-card" id="ic-${id}">
        ...
        value="${esc(desc)}"
        ...`;
    document.getElementById('itemsContainer').insertAdjacentHTML('beforeend', html);
}
```

**Atténuation** : La fonction `esc()` est définie à la [ligne 282](src/main/resources/templates/invoices/edit.html#L282) :
```javascript
function esc(s){ const d=document.createElement('div'); d.appendChild(document.createTextNode(s||'')); return d.innerHTML; }
```
Ce pattern d'échappement via DOM est **correct** et prévient l'XSS. Cependant, les variables `qty`, `price` et `tva` ne sont pas validées/échappées dans le template literal.

**Recommandation** : Valider les types numériques avant insertion :
```javascript
value="${Number(qty) || 1}"
```

---

## [T-10] INFO — Notifications avec escaping correct (layout.html)

- **Fichier** : [layout.html](src/main/resources/templates/layout.html#L1091-L1101)
- **Sévérité** : ℹ️ INFO (SÉCURISÉ)

**Code :**
```javascript
// SEC-XSS FIX : escape HTML entities in notification data
function escN(s) { if (!s) return ''; var d = document.createElement('div'); 
    d.appendChild(document.createTextNode(s)); return d.innerHTML; }
// ...
// SEC-XSS FIX : sanitize link to prevent javascript: protocol
const safeLink = (n.link && !n.link.toLowerCase().trim().startsWith('javascript:')) 
    ? encodeURI(n.link) : '#';
```

**Observation** : Les données de notification sont correctement échappées via DOM textContent, et les liens sont validés contre le protocol `javascript:`. Bon pattern.

---

## [T-11] INFO — Formulaires d'authentification avec autocomplete correct

- **Fichiers** : [auth/login.html](src/main/resources/templates/auth/login.html#L65-L76), [auth/register.html](src/main/resources/templates/auth/register.html#L114-L198), [auth/reset-password.html](src/main/resources/templates/auth/reset-password.html#L40-L56), [settings/index.html](src/main/resources/templates/settings/index.html#L175-L187)
- **Sévérité** : ℹ️ INFO (SÉCURISÉ)

**Observation** : Tous les champs de mot de passe utilisent les valeurs `autocomplete` appropriées :
- `autocomplete="current-password"` pour les champs de mot de passe existant
- `autocomplete="new-password"` pour les champs de nouveau mot de passe
- `autocomplete="email"` pour les champs email
- `autocomplete="given-name"`, `autocomplete="family-name"` pour les noms

Ceci est conforme aux bonnes pratiques OWASP et WCAG.

---

## [T-12] INFO — Absence de th:utext (tous les templates)

- **Sévérité** : ℹ️ INFO (SÉCURISÉ)

**Observation** : **Aucun fichier** template n'utilise `th:utext`. Tous les affichages de données dynamiques utilisent `th:text` (auto-escaping HTML par Thymeleaf). Ce point est **exemplaire** pour la prévention XSS côté serveur.

---

# ANNEXE A — Inventaire des correctifs SEC-* déjà appliqués

L'application contient de nombreux correctifs de sécurité déjà en place (visibles dans les commentaires du code) :

| Code | Description | Fichier |
|------|-------------|---------|
| SEC-01 | JWT blacklist pour révocation de tokens | JwtBlacklistService |
| SEC-03 | Vérification signature webhook PayPlug | PayPlugService |
| SEC-05 | Vérification accès client sur factures | InvoiceService |
| SEC-06 | Configuration base URL (pas de localhost hardcodé) | Multiple |
| SEC-07 | Suppression comptes zombie | AuthService |
| SEC-08 | Messages d'erreur génériques (prévention user enumeration) | AuthService |
| SEC-14 | Suppression application/octet-stream des MIME autorisés | DocumentService |
| SEC-IDOR | Corrections IDOR multiples | AppointmentService, NotificationService, DocumentShareService |
| SEC-RATELIMIT | Rate limiting sur vérification email | EmailVerificationService |
| SEC-TIMING | Comparaison en temps constant pour webhook | PayPlugService |
| SEC-WEBHOOK | Validation secret webhook | StripePaymentService |
| SEC-XSS | Escaping notifications et sanitisation liens | layout.html |
| H-07 | Validation URL dans th:href (anti javascript:) | invoices/show.html |
| H-09 | Protection path traversal | YousignService |
| H-11 | Validation magic bytes uploads | DocumentService |
| L-04 | Verrouillage après 5 tentatives (15 min) | AccountLockoutService |
| M-03 | Synchronized invoice number | InvoiceService |
| M-05 | Code pas dans le sujet email | EmailVerificationService |
| M-12 | Vérification propriété dossiers | CaseService |

---

# ANNEXE B — Éléments sécurisés notables

| Élément | Statut | Détails |
|---------|--------|---------|
| **Aucun th:utext** | ✅ SÉCURISÉ | Aucune injection HTML côté serveur possible |
| **CSRF tokens** | ✅ SÉCURISÉ | Présents dans tous les POST forms (sauf forms JS-only qui incluent les tokens dans les headers fetch) |
| **Autocomplete** | ✅ SÉCURISÉ | Correctement configuré sur tous les champs auth |
| **AES-256-GCM** | ✅ SÉCURISÉ | SecureCryptographyService utilise des algorithmes conformes |
| **RSA-4096** | ✅ SÉCURISÉ | Paires de clés asymétriques robustes |
| **PBKDF2 100K iterations** | ✅ SÉCURISÉ | Pour les codes de backup MFA |
| **SecureRandom** | ✅ SÉCURISÉ | Utilisé dans JWT, MFA, tokens (sauf PRA/LABFT simulations) |
| **Path traversal protection** | ✅ SÉCURISÉ | DocumentService et YousignService |
| **Magic bytes validation** | ✅ SÉCURISÉ | Vérification des premiers octets des fichiers uploadés |
| **Account lockout** | ✅ SÉCURISÉ | 5 tentatives / 15 minutes |
| **JWT blacklist** | ✅ SÉCURISÉ | Révocation de tokens lors du logout |
| **Webhook signature verification** | ✅ SÉCURISÉ | Stripe et PayPlug |
| **Ownership verification** | ✅ SÉCURISÉ | Cases, Documents, Clients, Appointments, Invoices |

---

# ANNEXE C — Matrice des risques par priorité de correction

| Priorité | ID | Sévérité | Effort | Impact |
|----------|----|----------|--------|--------|
| **P0** | S-01 | CRITIQUE | Faible | Backdoor permettant l'accès à toutes les factures |
| **P0** | T-01 | CRITIQUE* | Faible | Formulaire POST avec URL hardcodée |
| **P1** | S-02 | HIGH | Moyen | DoS par épuisement mémoire via logs |
| **P1** | S-03 | HIGH | Moyen | DoS par requêtes non bornées |
| **P1** | T-02 | HIGH | Faible | innerHTML avec données potentiellement non sûres |
| **P1** | T-03 | HIGH | Faible | Open redirect via URL de document |
| **P1** | T-04 | HIGH | Faible | URLs hardcodées dans les formulaires |
| **P2** | S-04 | MEDIUM | Faible | Fuite URL base de données |
| **P2** | S-05 | MEDIUM | Moyen | IP spoofing dans logs audit |
| **P2** | S-06 | MEDIUM | Moyen | Clés API non chiffrées en mémoire |
| **P2** | S-07 | MEDIUM | Faible | Injection URL dans RPVAService |
| **P2** | T-05 | MEDIUM | Faible | DB URL dans pages admin |
| **P2** | T-06 | MEDIUM | Moyen | Formulaires JS-only sans fallback |
| **P2** | T-07 | MEDIUM | Faible | CDN sans SRI |
| **P3** | S-08 | LOW | Trivial | Math.random() dans PRA |
| **P3** | S-09 | LOW | Trivial | Math.random() dans LAB-FT |
| **P3** | S-10 | LOW | Moyen | Race condition numéros facture |
| **P3** | T-08 | LOW | Moyen | Absence de CSP |
| **P3** | T-09 | LOW | Faible | Template literals non typés |

---

*\* T-01 est atténué à HIGH car le token CSRF est bien présent malgré l'URL hardcodée.*

**Fin du rapport d'audit.**
