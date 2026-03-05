# 🔒 AUDIT DE SÉCURITÉ — DocAvocat / GED Avocat
## Résultats NOUVEAUX (hors CRIT-01→SEC-11 déjà corrigés)
**Date :** Juin 2025  
**Périmètre :** Code source complet (contrôleurs, services, filtres, repositories, templates, configuration)  
**Méthodologie :** Revue statique OWASP Top-10 2021, ANSSI, RGPD

---

## Synthèse rapide

| Sévérité | Nombre |
|----------|--------|
| 🔴 CRITIQUE | 3 |
| 🟠 HAUTE | 4 |
| 🟡 MOYENNE | 6 |
| 🔵 BASSE | 3 |
| **TOTAL** | **16** |

---

## 🔴 CRITIQUES

### SEC-NEW-01 — Information Disclosure : endpoint compliance public (Contournement d'autorisation)

**Sévérité :** 🔴 CRITIQUE  
**Catégorie :** 5 — Contrôle d'accès  
**Fichier :** `src/main/java/com/gedavocat/controller/ComplianceController.java` ligne 92  

**Description :**  
Le contrôleur `ComplianceController` est protégé au niveau classe par `@PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")`.  
Cependant, la méthode `getInternalComplianceScore()` porte `@PreAuthorize("permitAll()")` qui **écrase** la restriction de classe. N'importe quel utilisateur anonyme peut accéder aux scores de conformité, niveaux de risque et horodatages internes.

```java
@GetMapping("/internal/compliance-score")
@PreAuthorize("permitAll()")                // ← BYPASS total de la sécurité classe
public ResponseEntity<Map<String, Object>> getInternalComplianceScore() {
```

**PoC :**
```bash
curl -s https://docavocat.fr/api/compliance/internal/compliance-score
# → {"score":87,"riskLevel":"LOW","status":"COMPLIANT","timestamp":"..."}
```

**Risque :** Exposition d'informations internes de conformité (score, niveau de risque, statut). Un attaquant sait exactement où le système est faible.

**Correctif :**
```java
@GetMapping("/internal/compliance-score")
@PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
// ou supprimer la ligne @PreAuthorize pour hériter de la classe
public ResponseEntity<Map<String, Object>> getInternalComplianceScore() {
```

---

### SEC-NEW-02 — Secret JWT vide par défaut (Signature prévisible)

**Sévérité :** 🔴 CRITIQUE  
**Catégorie :** 8 — Données sensibles  
**Fichier :** `src/main/resources/application.properties` ligne 76  

**Description :**  
Le secret JWT utilise une valeur par défaut **vide** si la variable d'environnement `JWT_SECRET` n'est pas définie :

```properties
jwt.secret=${JWT_SECRET:}
```

Si le déploiement oublie de définir `JWT_SECRET`, tous les JWT sont signés avec une chaîne vide, rendant la forge de tokens triviale.

**PoC :**
```python
import jwt
# Si JWT_SECRET n'est pas défini → secret = ""
token = jwt.encode({"sub": "admin@docavocat.fr", "role": "ADMIN"}, "", algorithm="HS256")
# Ce token sera accepté par le serveur
```

**Correctif :**  
Faire échouer le démarrage si le secret est vide :
```properties
jwt.secret=${JWT_SECRET}
```
Ou ajouter une validation `@PostConstruct` :
```java
@PostConstruct
void validateJwtSecret() {
    if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
        throw new IllegalStateException("JWT_SECRET doit être défini (≥ 32 caractères)");
    }
}
```

---

### SEC-NEW-03 — Secrets Stripe en dur avec valeurs factices (Contournement webhook)

**Sévérité :** 🔴 CRITIQUE  
**Catégorie :** 8 — Données sensibles  
**Fichier :** `src/main/resources/application.properties` lignes 120-122  

**Description :**  
Les clés Stripe utilisent des valeurs factices comme défaut :

```properties
stripe.api.key=${STRIPE_API_KEY:sk_test_dummy}
stripe.publishable.key=${STRIPE_PUBLISHABLE_KEY:pk_test_dummy}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET:whsec_dummy}
```

Si `STRIPE_WEBHOOK_SECRET` n'est pas défini, le secret webhook est `whsec_dummy`. Un attaquant qui le devine peut **forger des événements webhook** pour activer des abonnements gratuitement.

**PoC :**
```python
import hmac, hashlib, time, json

payload = json.dumps({"type": "checkout.session.completed", "data": {"object": {"client_reference_id": "victim-user-id", "metadata": {"plan": "PREMIUM"}}}})
timestamp = str(int(time.time()))
signature = hmac.new(b"whsec_dummy", f"{timestamp}.{payload}".encode(), hashlib.sha256).hexdigest()

# POST /subscription/webhook avec ce payload active un abonnement Premium gratuit
```

**Correctif :**  
Supprimer les défauts et exiger les variables d'environnement :
```properties
stripe.api.key=${STRIPE_API_KEY}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET}
```

---

## 🟠 HAUTES

### SEC-NEW-04 — Upload de facture sans validation de type de fichier

**Sévérité :** 🟠 HAUTE  
**Catégorie :** 7 — Upload de fichiers  
**Fichier :** `src/main/java/com/gedavocat/controller/InvoiceWebController.java` lignes 156-164  

**Description :**  
Contrairement à `DocumentService.uploadDocument()` qui valide extensions, MIME types et bytes magiques, la méthode `importInvoice()` n'effectue **aucune validation de type** sur le fichier uploadé. Seule la sanitization des caractères du nom est appliquée.

```java
String filename = UUID.randomUUID() + "_" + file.getOriginalFilename()
    .replaceAll("[^a-zA-Z0-9._-]", "_");
Path dest = invoicesDir.resolve(filename);
file.transferTo(dest.toFile());   // ← aucune validation d'extension/MIME/magic bytes
```

**PoC :**
```bash
# Upload d'un fichier HTML contenant du JavaScript malveillant
curl -X POST https://docavocat.fr/invoices/import \
  -F "clientId=xxx" -F "invoiceNumber=FAC-001" -F "invoiceDate=2025-01-01" \
  -F "totalHT=100" -F "file=@malicious.html" \
  -H "Cookie: GEDAVOCAT_SESSION=..."
# Le fichier est accessible à /uploads/invoices/<uuid>_malicious.html → XSS stocké
```

**Correctif :**  
Réutiliser la validation de `DocumentService` ou ajouter une whitelist :
```java
Set<String> ALLOWED = Set.of(".pdf", ".jpg", ".png");
String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
if (!ALLOWED.contains(ext)) {
    throw new SecurityException("Type de fichier non autorisé");
}
```

---

### SEC-NEW-05 — Régression SVC-01 : InvoiceWebController passe null comme lawyerId

**Sévérité :** 🟠 HAUTE  
**Catégorie :** 5 — Contrôle d'accès / 9 — Logique métier  
**Fichier :** `src/main/java/com/gedavocat/controller/InvoiceWebController.java` lignes 216, 252, 311  

**Description :**  
Le fix SVC-01 dans `InvoiceService.getInvoiceById()` rejette désormais les appels avec `lawyerId == null` :

```java
// InvoiceService.java
if (lawyerId == null) {
    throw new SecurityException("Identifiant avocat requis pour accéder à une facture");
}
```

Or, `InvoiceWebController` passe systématiquement `null` dans `show()`, `edit()` et `downloadPdf()` :

```java
var invoice = invoiceService.getInvoiceById(id, null); // null=no filter, ownership checked below
```

**Conséquence :** Les 3 méthodes lèvent **toujours** `SecurityException`, attrapée par le `catch (Exception e)` qui redirige avec "Facture non trouvée". **Aucun utilisateur ne peut consulter, éditer ou télécharger une facture via l'interface web.**

**Correctif :**  
Passer le lawyerId réel ou utiliser une variante adaptée :
```java
// Pour show() — déterminer le contexte d'accès
boolean isAdmin = /* ... */;
boolean isClient = /* ... */;
String accessKey = isAdmin ? "ADMIN_BYPASS" 
    : isClient ? "CLIENT_" + user.getId() 
    : user.getId();
var invoice = invoiceService.getInvoiceById(id, accessKey);
```

---

### SEC-NEW-06 — ClientPortalController fallback par email : risque cross-tenant

**Sévérité :** 🟠 HAUTE  
**Catégorie :** 5 — Contrôle d'accès (Multi-tenant)  
**Fichier :** `src/main/java/com/gedavocat/controller/ClientPortalController.java` lignes 89-95  

**Description :**  
Si `clientRepository.findByClientUserId(user.getId())` ne trouve pas de client, le contrôleur tente un fallback par email :

```java
java.util.Optional<Client> byEmail = clientRepository.findByEmail(user.getEmail());
if (byEmail.isPresent()) {
    client = byEmail.get();   // ← peut retourner le client d'un AUTRE cabinet
}
```

Le `MultiTenantFilter` filtre par `firm_id`, mais un CLIENT n'a pas nécessairement de firm assignée dans sa table `users`. Si deux cabinets ont un client avec le même email, et que le `clientUserId` n'est pas lié, le fallback retourne le premier trouvé — potentiellement d'un **autre cabinet**.

**PoC :**  
1. Cabinet A crée un client `john@example.com` sans lier le clientUser  
2. Cabinet B crée un client `john@example.com` sans lier le clientUser  
3. John se connecte → `findByClientUserId` échoue → `findByEmail` retourne l'un des deux clients (non déterministe)  
4. John voit les dossiers d'un cabinet qui n'est pas le sien

**Correctif :**  
Supprimer le fallback par email ou le restreindre au firm du clientUser :
```java
// Supprimer le fallback OU ajouter un filtre firm
if (user.getFirm() != null) {
    byEmail = clientRepository.findByEmailAndFirmId(user.getEmail(), user.getFirm().getId());
}
```

---

### SEC-NEW-07 — MySQL verifyServerCertificate=false (MITM sur connexion BDD)

**Sévérité :** 🟠 HAUTE  
**Catégorie :** 8 — Données sensibles / Transit  
**Fichier :** `src/main/resources/application.properties` ligne 24  

**Description :**  
La connexion MySQL exige SSL (`useSSL=true&requireSSL=true`) mais **désactive la vérification du certificat serveur** :

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/gedavocat?useSSL=true&requireSSL=true&verifyServerCertificate=false
```

Un attaquant en position de MITM entre l'application et la base de données peut intercepter tout le trafic SQL (identifiants, données avocat-client, secrets) malgré le chiffrement TLS.

**Correctif :**
```properties
spring.datasource.url=jdbc:mysql://...?useSSL=true&requireSSL=true&verifyServerCertificate=true&trustCertificateKeyStoreUrl=file:/path/to/truststore.jks
```

---

## 🟡 MOYENNES

### SEC-NEW-08 — Divulgation d'informations via messages d'erreur (11+ occurrences)

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 8 — Données sensibles  
**Fichiers :** Multiples contrôleurs  

**Description :**  
De nombreux contrôleurs exposent `e.getMessage()` directement dans les réponses HTTP ou les en-têtes `X-Error`. Cela peut révéler des noms de classes internes, des requêtes SQL, des chemins de fichiers ou des détails d'infrastructure.

**Occurrences principales :**

| Fichier | Ligne | Vecteur |
|---------|-------|---------|
| SecurityAuditController.java | 49, 73 | Header `X-Error` + body |
| SecurityAdminController.java | 75, 116, 151, 176 | Header `X-Error` |
| ComplianceController.java | 81, 111, 276, 348 | Body JSON `error` |
| ClientPortalController.java | 316, 352, 391, 429, 525, 628 | Body / redirect flash |
| DatabaseMigrationController.java | 37-137 | Body JSON |
| DocumentShareController.java | 82, 124 | Body JSON |
| HuissierInvitationController.java | 199 | Template model |
| InvoiceController.java | 54 | Body JSON |

**PoC :**
```bash
# Provoquer une erreur et observer la réponse
curl -s https://docavocat.fr/api/security/audit/technical/run \
  -H "Authorization: Bearer <admin-token>"
# → Header X-Error: "Audit technique échoué: NullPointerException at com.gedavocat.security.audit..."
```

**Correctif :**  
Remplacer systématiquement par des messages génériques :
```java
// ❌ Avant
.header("X-Error", "Erreur: " + e.getMessage())
// ✅ Après
log.error("Erreur technique [ref={}]", UUID.randomUUID(), e);
.header("X-Error", "Une erreur interne est survenue")
```

---

### SEC-NEW-09 — AppointmentController update() sans @Valid (Contournement de validation)

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 9 — Logique métier  
**Fichier :** `src/main/java/com/gedavocat/controller/AppointmentController.java`  

**Description :**  
La méthode `create()` applique `@Valid` sur le `@ModelAttribute Appointment`, mais `update()` ne le fait pas. Les contraintes de validation du modèle (taille, format, obligatoire) sont ignorées lors de la mise à jour.

```java
// create() — CORRECT
public String create(@Valid @ModelAttribute Appointment appointment, ...)

// update() — MANQUE @Valid
public String update(@ModelAttribute Appointment appointment, ...)   // ← pas de @Valid
```

**PoC :**  
Soumettre un rendez-vous avec un titre vide ou une date passée via le formulaire d'édition — la validation côté serveur sera ignorée.

**Correctif :**
```java
public String update(@Valid @ModelAttribute Appointment appointment, BindingResult result, ...)
```

---

### SEC-NEW-10 — Idempotence webhook en mémoire (perte au redémarrage)

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 9 — Logique métier  
**Fichier :** `src/main/java/com/gedavocat/controller/SubscriptionController.java`  

**Description :**  
Les sets d'idempotence pour les webhooks Stripe utilisent des collections en mémoire :

```java
private final Set<String> processedSessionIds = ConcurrentHashMap.newKeySet();
private final Set<String> processedEventIds = new ConcurrentSkipListSet<>();
```

Au redémarrage de l'application, ces sets sont vidés. Si Stripe re-envoie un webhook déjà traité (retry standard), le traitement sera dupliqué — potentiellement avec double activation d'abonnement.

De plus, `processedEventIds` est purgé arbitrairement quand il dépasse 10 000 entrées.

**Correctif :**  
Persister les IDs en base de données :
```java
@Entity
class ProcessedWebhookEvent {
    @Id String eventId;
    LocalDateTime processedAt;
}
```

---

### SEC-NEW-11 — PaymentController redirect avec paramètres utilisateur non encodés

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 11 — Redirections ouvertes  
**Fichier :** `src/main/java/com/gedavocat/controller/PaymentController.java` ligne 50  

**Description :**  
```java
return "redirect:/subscription/checkout?plan=" + (plan != null ? plan : "") + "&period=" + period;
```

Les paramètres `plan` et `period` proviennent du `@RequestParam` et sont concaténés dans l'URL de redirection sans encodage. Un attaquant peut injecter des paramètres supplémentaires via `plan=foo%26admin%3Dtrue`.

**Correctif :**  
Utiliser `UriComponentsBuilder` :
```java
return "redirect:" + UriComponentsBuilder.fromPath("/subscription/checkout")
    .queryParam("plan", plan != null ? plan : "")
    .queryParam("period", period)
    .toUriString();
```

---

### SEC-NEW-12 — Content-Disposition header injection via numéro de facture

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 1 — Injection  
**Fichier :** `src/main/java/com/gedavocat/controller/InvoiceWebController.java` ligne 327  

**Description :**  
```java
String filename = "facture-" + invoice.getInvoiceNumber() + ".pdf";
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
```

Le `invoiceNumber` provient de l'utilisateur sans validation de caractères. Si le numéro contient `"` ou `\r\n`, cela permet une injection d'en-tête HTTP (response splitting).

**Correctif :**
```java
String safeNumber = invoice.getInvoiceNumber().replaceAll("[^a-zA-Z0-9_-]", "_");
String filename = "facture-" + safeNumber + ".pdf";
```

---

### SEC-NEW-13 — MFA encryption key vide par défaut

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 8 — Données sensibles  
**Fichier :** `src/main/resources/application.properties` ligne 82  

**Description :**  
```properties
security.mfa.encryption-key=${MFA_ENCRYPTION_KEY:}
```

Si `MFA_ENCRYPTION_KEY` n'est pas défini, les secrets TOTP sont stockés **en clair** en base (le commentaire le confirme). Toute compromission de la BDD révèle les secrets MFA de tous les utilisateurs.

**Correctif :**  
Faire échouer le démarrage si la clé n'est pas définie :
```properties
security.mfa.encryption-key=${MFA_ENCRYPTION_KEY}
```

---

## 🔵 BASSES

### SEC-NEW-14 — CaseShareController : vue partagée expose tous les documents du dossier

**Sévérité :** 🔵 BASSE  
**Catégorie :** 5 — Contrôle d'accès  
**Fichier :** `src/main/java/com/gedavocat/controller/CaseShareController.java`  

**Description :**  
Lorsqu'un lien de partage de dossier est utilisé, **tous** les documents non supprimés du dossier sont affichés. Il n'y a pas de filtrage granulaire (par document partagé). L'avocat ne peut pas choisir quels documents spécifiques partager via ce mécanisme.

**Correctif :**  
Filtrer les documents affichés par ceux explicitement partagés via `DocumentShareRepository`.

---

### SEC-NEW-15 — CSP script-src avec 'unsafe-inline'

**Sévérité :** 🔵 BASSE  
**Catégorie :** 12 — Sécurité des en-têtes  
**Fichier :** `src/main/java/com/gedavocat/security/SecurityConfig.java`  

**Description :**  
La Content Security Policy autorise `'unsafe-inline'` pour les scripts, ce qui réduit significativement la protection contre les XSS. Noté comme dette technique dans le code. Tout XSS trouvé à l'avenir (par exemple via un nouveau `th:utext`) ne sera pas bloqué par la CSP.

**Correctif :**  
Migrer vers des nonces CSP ou externaliser tous les scripts inline :
```java
.contentSecurityPolicy(csp -> csp.policyDirectives(
    "script-src 'self' 'nonce-{random}'; ..."))
```

---

### SEC-NEW-16 — Absence de @PreAuthorize au niveau classe sur InvoiceWebController

**Sévérité :** 🔵 BASSE  
**Catégorie :** 5 — Contrôle d'accès (Défense en profondeur)  
**Fichier :** `src/main/java/com/gedavocat/controller/InvoiceWebController.java`  

**Description :**  
`InvoiceWebController` n'a pas de `@PreAuthorize` au niveau classe. Chaque méthode est annotée individuellement, mais si un développeur ajoute une nouvelle méthode sans annotation, elle sera accessible à **tout utilisateur authentifié** (y compris CLIENT, HUISSIER).

Même problème sur `NotificationController` qui n'a aucune annotation `@PreAuthorize`.

**Correctif :**  
Ajouter une protection par défaut au niveau classe :
```java
@PreAuthorize("isAuthenticated()")   // minimum
// ou mieux :
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")
```

---

## Points positifs observés

| Aspect | Évaluation |
|--------|-----------|
| ✅ Pas de `th:utext` dans les templates | XSS output encoding OK |
| ✅ Toutes les `@Query` utilisent des paramètres nommés JPQL | Pas de SQL injection |
| ✅ Redirections internes hardcodées (sauf 2 exceptions) | Pas d'open redirect majeur |
| ✅ CSRF activé sur tous les formulaires web | Protection CSRF correcte |
| ✅ Session cookie : HttpOnly, Secure, SameSite=Strict | Cookie sécurisé |
| ✅ BCrypt(12) pour les mots de passe | Hashage fort |
| ✅ Rate limiting sur les endpoints d'auth | Anti brute-force |
| ✅ DocumentService upload avec triple validation | Upload sécurisé (hors invoices) |
| ✅ Multi-tenant via Hibernate filter | Isolation firm correcte |
| ✅ `server.error.include-stacktrace=never` | Pas de stacktrace en prod |
| ✅ `spring.jpa.hibernate.ddl-auto=validate` | Pas d'altération automatique du schéma |
| ✅ H2 console désactivée | Surface d'attaque réduite |
| ✅ Actuator complètement désactivé (profil principal) | Pas de fuite de métriques |

---

## Priorité de remédiation

| Priorité | IDs | Effort estimé |
|----------|-----|---------------|
| 🔴 Immédiat (< 1 jour) | SEC-NEW-01, SEC-NEW-02, SEC-NEW-03 | 1h chacun |
| 🟠 Court terme (< 1 semaine) | SEC-NEW-04, SEC-NEW-05, SEC-NEW-06, SEC-NEW-07 | 2-4h chacun |
| 🟡 Moyen terme (< 1 mois) | SEC-NEW-08 à SEC-NEW-13 | 1-2h chacun |
| 🔵 Planifié (backlog) | SEC-NEW-14 à SEC-NEW-16 | Variable |
