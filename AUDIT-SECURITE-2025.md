# 🔒 AUDIT DE SÉCURITÉ — DocAvocat / GED Avocat
## Résultats NOUVEAUX (hors CRIT-01→SEC-11 déjà corrigés)
**Date :** Juin 2025 — **Re-audit : Juillet 2025**  
**Périmètre :** Code source complet (37 contrôleurs, 39 services, 5 filtres, 18 modèles, 18 repositories, templates, configuration Docker/Nginx, pom.xml)  
**Méthodologie :** Revue statique OWASP Top-10 2021, ANSSI, RGPD, analyse de dépendances, audit infrastructure Docker

---

## Re-audit Juillet 2025 — Synthèse

| Sévérité | Juin 2025 | Statut Juillet 2025 |
|----------|-----------|---------------------|
| 🔴 CRITIQUE | 3 | 2 ouverts / **1 corrigé** (SEC-NEW-01 ✅) |
| 🟠 HAUTE | 4 | 4 ouverts |
| 🟡 MOYENNE | 6 | 6 ouverts + **4 nouveaux** |
| 🔵 BASSE | 3 | 3 ouverts + **3 nouveaux** |
| **TOTAL** | **16** | **22 (15 existants + 7 nouveaux)** |

### Correctifs vérifiés depuis Juin 2025

| ID | Titre | Statut |
|----|-------|--------|
| SEC-NEW-01 | ComplianceController `permitAll()` bypass | ✅ **CORRIGÉ** — Annotation remplacée par `@PreAuthorize("hasRole('ADMIN')")` |
| SEC-NEW-02 | JWT secret vide par défaut | ⚠️ **PARTIELLEMENT ATTÉNUÉ** — `@PostConstruct` dans `JwtService` empêche le démarrage avec un secret vide/court/dummy. La propriété garde `${JWT_SECRET:}` (défaut vide) mais l'app ne démarre plus. |

---

## Synthèse rapide (consolidée Juillet 2025)

| Sévérité | Nombre |
|----------|--------|
| 🔴 CRITIQUE | 2 |
| 🟠 HAUTE | 4 |
| 🟡 MOYENNE | 10 |
| 🔵 BASSE | 6 |
| **TOTAL** | **22** |

---

## 🔴 CRITIQUES

### SEC-NEW-01 — ~~Information Disclosure : endpoint compliance public~~ ✅ CORRIGÉ

**Sévérité :** ~~🔴 CRITIQUE~~ → ✅ CORRIGÉ (Juillet 2025)  
**Catégorie :** 5 — Contrôle d'accès  
**Fichier :** `src/main/java/com/gedavocat/controller/ComplianceController.java` ligne 93  

**Vérification Juillet 2025 :**  
L'annotation `@PreAuthorize("permitAll()")` a été remplacée par `@PreAuthorize("hasRole('ADMIN')")`.

```java
@GetMapping("/internal/compliance-score")
@PreAuthorize("hasRole('ADMIN')")           // ← CORRIGÉ — était permitAll()
public ResponseEntity<Map<String, Object>> getInternalComplianceScore() {
```

**Statut : CORRIGÉ** — Plus de contournement d'autorisation possible.

---

### SEC-NEW-02 — Secret JWT vide par défaut (Signature prévisible)

**Sévérité :** 🔴 CRITIQUE (⚠️ partiellement atténué — voir note)  
**Catégorie :** 8 — Données sensibles  
**Fichier :** `src/main/resources/application.properties` ligne 76  

**Description :**  
Le secret JWT utilise une valeur par défaut **vide** si la variable d'environnement `JWT_SECRET` n'est pas définie :

```properties
jwt.secret=${JWT_SECRET:}
```

**Atténuation existante (Juillet 2025) :**  
`JwtService.java` contient un `@PostConstruct` qui **empêche le démarrage** si le secret est vide, contient "CHANGE_ME"/"dummy", ou fait moins de 32 caractères. L'application ne peut donc pas démarrer avec un secret vide. Cependant, la propriété garde un défaut vide, ce qui est un anti-pattern (l'erreur survient au démarrage, pas à la compilation/configuration).

**Risque résiduel :** Faible en pratique grâce au `@PostConstruct`, mais la propriété devrait utiliser `${JWT_SECRET}` (sans défaut) pour échouer dès le chargement des propriétés Spring.

**Correctif recommandé :**  
Supprimer le défaut vide pour échouer au plus tôt :
```properties
jwt.secret=${JWT_SECRET}
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

**Atténuation partielle (Juillet 2025) :**  
`StripeService.isConfigured()` vérifie que les clés ne commencent pas par "sk_test_dummy" ou "pk_test_dummy", et bloque `createCheckoutSession()` si les clés sont factices. **Cependant**, `constructWebhookEvent()` utilise `webhookSecret` directement sans vérifier `isConfigured()`. Si `STRIPE_WEBHOOK_SECRET` n'est pas défini, le secret webhook est `whsec_dummy` et un attaquant qui le devine peut **forger des événements webhook** pour activer des abonnements.

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
Et ajouter un guard dans `constructWebhookEvent()` :
```java
public Event constructWebhookEvent(String payload, String sigHeader) throws StripeException {
    if (webhookSecret == null || webhookSecret.isBlank() || webhookSecret.startsWith("whsec_dummy")) {
        throw new SecurityException("Webhook secret non configuré");
    }
    return Webhook.constructEvent(payload, sigHeader, webhookSecret);
}
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

## 🆕 NOUVELLES VULNÉRABILITÉS (Re-audit Juillet 2025)

### SEC-NEW-17 — Yousign sandbox URL en profil PRODUCTION

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 3 — Configuration  
**Fichiers :**  
- `src/main/resources/application-prod.properties` ligne 31  
- `src/main/resources/application.properties` ligne 112  
- `src/main/java/com/gedavocat/service/YousignService.java` ligne 39  

**Description :**  
Tous les profils (y compris **production**) pointent vers l'API sandbox Yousign :

```properties
# application-prod.properties ligne 31
yousign.api.url=https://api-sandbox.yousign.app
```

```java
// YousignService.java ligne 39
@Value("${yousign.api.url:https://api-sandbox.yousign.app}")
```

L'URL de production Yousign est `https://api.yousign.app`. En sandbox, les signatures électroniques ne sont **pas juridiquement valides** (eIDAS). Tout document "signé" en production via ce endpoint est juridiquement nul.

**Risque :** Signatures invalides juridiquement ; données de signature envoyées vers un environnement non-production chez Yousign.

**Correctif :**
```properties
# application-prod.properties
yousign.api.url=https://api.yousign.app
```

---

### SEC-NEW-18 — `allowPublicKeyRetrieval=true` sur connexion MySQL (amplification MITM)

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 8 — Données sensibles / Transit  
**Fichier :** `src/main/resources/application.properties` ligne 24  

**Description :**  
Combiné avec `verifyServerCertificate=false` (SEC-NEW-07), le paramètre `allowPublicKeyRetrieval=true` dans l'URL JDBC amplifie le risque MITM :

```properties
spring.datasource.url=jdbc:mysql://...?useSSL=true&requireSSL=true&verifyServerCertificate=false&allowPublicKeyRetrieval=true
```

Avec `allowPublicKeyRetrieval=true`, le client MySQL récupère la clé publique RSA du serveur **sans vérification**. Un attaquant MITM peut servir sa propre clé publique, intercepter le mot de passe MySQL chiffré avec sa clé, et le déchiffrer.

**Risque :** Interception des credentials MySQL en cas de MITM.

**Correctif :**  
Supprimer `allowPublicKeyRetrieval=true` et configurer le truststore :
```properties
spring.datasource.url=jdbc:mysql://...?useSSL=true&requireSSL=true&verifyServerCertificate=true&trustCertificateKeyStoreUrl=file:/path/to/truststore.jks&allowPublicKeyRetrieval=false
```

---

### SEC-NEW-19 — cAdvisor en mode `privileged: true` dans docker-compose

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 14 — Infrastructure / Conteneur  
**Fichier :** `docker/docker-compose.yml` ligne 232  

**Description :**  
Le conteneur cAdvisor est configuré en mode privilégié :

```yaml
cadvisor:
    image: gcr.io/cadvisor/cadvisor:latest
    privileged: true   # ← ligne 232
```

Un conteneur privilégié a un accès **complet** au kernel de l'hôte. Si cAdvisor est compromis (CVE dans l'image, supply chain attack), l'attaquant obtient un accès root à l'hôte Docker.

**Risque :** Évasion de conteneur → compromission de l'hôte.

**Correctif :**  
Utiliser des capabilities spécifiques au lieu de `privileged` :
```yaml
cadvisor:
    image: gcr.io/cadvisor/cadvisor:v0.49.1  # pinned version
    # privileged: true  ← SUPPRIMER
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    devices:
      - /dev/kmsg:/dev/kmsg
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker:/var/lib/docker:ro
```

---

### SEC-NEW-20 — SecurityMonitoringController : `e.getMessage()` exposé dans le template

**Sévérité :** 🟡 MOYENNE  
**Catégorie :** 8 — Données sensibles  
**Fichier :** `src/main/java/com/gedavocat/controller/SecurityMonitoringController.java` ligne 56  

**Description :**  
```java
} catch (Exception e) {
    log.error("Erreur lors de la génération du tableau de bord de sécurité", e);
    model.addAttribute("error", "Erreur lors du chargement du rapport: " + e.getMessage());
    return "admin/security-monitoring";
}
```

Le message d'exception (potentiellement contenant des noms de classes, des stack traces partiels) est injecté dans le template Thymeleaf via le model attribute `error`. Même si Thymeleaf escape le HTML par défaut, le contenu affiché peut révéler des détails d'infrastructure internes à un administrateur compromis ou via un screen capture.

**Correctif :**
```java
log.error("Erreur rapport sécurité [ref={}]", UUID.randomUUID(), e);
model.addAttribute("error", "Une erreur interne est survenue. Consultez les logs.");
```

---

### SEC-NEW-21 — `spring-boot-devtools` dans les dépendances Maven

**Sévérité :** 🔵 BASSE  
**Catégorie :** 3 — Configuration  
**Fichier :** `pom.xml` ligne 159  

**Description :**  
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

Bien que marqué `<optional>true</optional>` (exclut du fat JAR par défaut), la présence de `spring-boot-devtools` dans le POM peut activer des fonctionnalités de debug en développement local qui persistent si le profil n'est pas correctement configuré : live reload, cache désactivé, actuator exposé, H2 console auto-activée.

**Risque :** Surface d'attaque élargie en cas de mauvaise configuration de profil.

**Correctif :**  
Limiter explicitement au profil dev avec un profile Maven :
```xml
<profiles>
    <profile>
        <id>dev</id>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-devtools</artifactId>
                <scope>runtime</scope>
                <optional>true</optional>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

---

### SEC-NEW-22 — Scripts inline dans les templates (cause racine de CSP unsafe-inline)

**Sévérité :** 🔵 BASSE  
**Catégorie :** 12 — Sécurité des en-têtes / XSS  
**Fichiers :**  
- `src/main/resources/templates/subscription/pricing.html` (lignes 347-349, 526-561) — `onclick`, `<script>` inline  
- `src/main/resources/templates/signatures/new.html` (ligne 319) — `<script th:inline="javascript">`  
- `src/main/resources/templates/signatures/index.html` (lignes 65, 69, 186) — `onclick`, `<script>`  
- `src/main/resources/templates/settings/index.html` (lignes 37-46, 145, 271, 279) — `onclick`, `<script>`  
- `src/main/resources/templates/payment/pricing.html` (lignes 20, 23) — `onclick`  

**Description :**  
Au moins **5 templates** utilisent des handlers `onclick` inline et/ou des blocs `<script>` inline. Ce modèle est la **cause racine** de la nécessité de `'unsafe-inline'` dans la CSP (SEC-NEW-15), désactivant la protection XSS la plus efficace du navigateur.

L'utilisation de `th:inline="javascript"` dans `signatures/new.html` est particulièrement risquée car elle permet l'injection de variables côté serveur directement dans le JavaScript.

**Correctif :**  
Phase 1 : Externaliser tous les blocs `<script>` dans des fichiers `.js` dédiés sous `static/js/`.  
Phase 2 : Remplacer les `onclick="..."` par `addEventListener()` dans les fichiers JS externes.  
Phase 3 : Implémenter CSP nonce-based :
```java
// SecurityConfig.java — génération dynamique de nonce
.contentSecurityPolicy(csp -> csp.policyDirectives(
    "script-src 'self' 'nonce-" + cspNonce + "' https://js.stripe.com; ..."))
```

---

### SEC-NEW-23 — NotificationController sans contrôle d'accès au niveau classe

**Sévérité :** 🔵 BASSE  
**Catégorie :** 5 — Contrôle d'accès (Défense en profondeur)  
**Fichier :** `src/main/java/com/gedavocat/controller/NotificationController.java`  

**Description :**  
`NotificationController` ne porte **aucune** annotation `@PreAuthorize` au niveau classe. Il utilise `@AuthenticationPrincipal UserDetails` pour récupérer l'utilisateur courant et `userRepository.findByEmail()`, mais ne vérifie jamais le rôle. Si un utilisateur est authentifié (n'importe quel rôle), il peut accéder à l'API notifications.

Le contrôle IDOR sur `markAsRead()` est présent (passe `user.getId()` au service), mais l'absence de `@PreAuthorize` au niveau classe signifie qu'un compte avec un rôle inattendu (ex: compte désactivé mais session active) peut interagir avec l'API.

```java
@Controller
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
// ← MANQUE : @PreAuthorize("isAuthenticated()") minimum
public class NotificationController {
```

**Correctif :**
```java
@Controller
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {
```

---

## Points positifs observés

| Aspect | Évaluation |
|--------|-----------|
| ✅ Pas de `th:utext` dans les templates | XSS output encoding OK |
| ✅ Toutes les `@Query` utilisent des paramètres nommés JPQL | Pas de SQL injection |
| ✅ Redirections internes hardcodées (sauf 2 exceptions) | Pas d'open redirect majeur |
| ✅ CSRF activé sur tous les formulaires web | Protection CSRF correcte (tokens `_csrf` vérifiés) |
| ✅ Session cookie : HttpOnly, Secure, SameSite=Strict | Cookie sécurisé |
| ✅ BCrypt(12) pour les mots de passe | Hashage fort |
| ✅ Rate limiting sur les endpoints d'auth | Anti brute-force (Bucket4j) |
| ✅ DocumentService upload avec triple validation | Upload sécurisé (ext + MIME + magic bytes) |
| ✅ Multi-tenant via Hibernate filter | Isolation firm correcte + fail-closed |
| ✅ `server.error.include-stacktrace=never` | Pas de stacktrace en prod |
| ✅ `spring.jpa.hibernate.ddl-auto=validate` | Pas d'altération automatique du schéma |
| ✅ H2 console désactivée (+ denyAll dans SecurityConfig) | Double protection |
| ✅ Actuator complètement désactivé (profil principal) | Pas de fuite de métriques |
| ✅ JwtService `@PostConstruct` validation | Secret JWT vide/court/dummy bloque le démarrage |
| ✅ StripeService `isConfigured()` guard | Checkout bloqué si clés dummy |
| ✅ Stripe webhook signature vérification (`Webhook.constructEvent`) | Webhooks authentifiés |
| ✅ StrictHttpFirewall sans relaxation | Path traversal encodé bloqué |
| ✅ HSTS 2 ans avec preload + includeSubDomains | Transport sécurisé |
| ✅ `frame-ancestors 'none'` dans CSP | Anti-clickjacking |
| ✅ Permissions-Policy restrictive | Caméra, micro, géoloc désactivés |
| ✅ MFA secrets chiffrés AES-256-GCM (AttributeConverter) | Protection données MFA at-rest |
| ✅ User.password annoté `@JsonIgnore` | Pas de sérialisation du hash |
| ✅ User.stripeCustomerId/stripeSubscriptionId `@JsonIgnore` | Pas de fuite Stripe |
| ✅ EmailService utilise `escapeHtml()` systématiquement | Anti-XSS dans les emails |
| ✅ Dockerfile non-root (uid 1001) + JRE-only | Surface conteneur minimale |
| ✅ Docker ports liés à 127.0.0.1 | Pas d'exposition réseau externe |
| ✅ Grafana hardened (read_only, no-new-privileges, resource limits) | Monitoring sécurisé |
| ✅ User entity avec validation Bean (`@NotBlank`, `@Size`, `@Email`, `@Pattern`) | Validation modèle complète |
| ✅ Account lockout avec `resetAttempts()` après succès | Anti brute-force résilient |
| ✅ Session fixation protection (`newSession()`) | Pas de fixation de session |
| ✅ Max 1 session concurrente | Pas de session hijack parallèle |
| ✅ `@InitBinder` pour mass assignment (ClientController) | Binding restreint |
| ✅ Notifications : IDOR check via `user.getId()` | Ownership vérifié |
| ✅ OWASP dependency-check plugin (CVSS 7.0 fail threshold) | Scan de dépendances automatisé |

---

## Priorité de remédiation (mise à jour Juillet 2025)

| Priorité | IDs | Effort estimé |
|----------|-----|---------------|
| ✅ Corrigé | SEC-NEW-01 | — |
| 🔴 Immédiat (< 1 jour) | SEC-NEW-02, SEC-NEW-03 | 1h chacun |
| 🟠 Court terme (< 1 semaine) | SEC-NEW-04, SEC-NEW-05, SEC-NEW-06, SEC-NEW-07, SEC-NEW-17 | 2-4h chacun |
| 🟡 Moyen terme (< 1 mois) | SEC-NEW-08 à SEC-NEW-13, SEC-NEW-18, SEC-NEW-19, SEC-NEW-20 | 1-2h chacun |
| 🔵 Planifié (backlog) | SEC-NEW-14 à SEC-NEW-16, SEC-NEW-21, SEC-NEW-22, SEC-NEW-23 | Variable |

---

## Annexe : Fichiers audités (Juillet 2025)

### Contrôleurs (37 fichiers)
- AuthController, AdminController, AdminApiController, DocumentController, TestDataController
- ClientController, CaseController, PaymentController, DatabaseMigrationController
- PasswordResetController, SecurityAdminController, SecurityAuditController
- InvoiceController, SettingsController, CaseShareController, SubscriptionController
- SignatureController, RPVAController, ClientPortalController, CollaboratorPortalController
- HuissierPortalController, RgpdController, ComplianceController, DocumentShareController
- DashboardController, NotificationController, SecurityMonitoringController, SitemapController
- AppointmentController, InvoiceWebController, MaintenanceController, LegalController
- FaviconController, ClientFeaturesController, AppointmentClientController
- CollaboratorInvitationController, HuissierInvitationController

### Services (clés audités)
- JwtService, AuthService, DocumentService, StripeService, EmailService, UserService
- YousignService, AdminMetricsService, PRAService, RefreshTokenService

### Configuration
- SecurityConfig, JwtAuthenticationFilter, MultiTenantFilter, RateLimitingFilter
- application.properties, application-prod.properties, application-h2.properties
- application-secure.properties, pom.xml, Dockerfile, docker-compose.yml, entrypoint.sh
- logback-spring.xml, .env.example

### Modèles
- User.java (465 lignes — complet)

### Templates (vérification patterns)
- Recherche exhaustive : `th:utext` (0 occurrences), `<script>` inline (5+ templates), `onclick` (8+ templates), `_csrf` tokens (présents dans formulaires)

