# AUDIT COMPLET — Logique métier & UX
## DocAvocat Spring Boot SaaS — Mars 2026

---

## Sommaire

| Catégorie | Critiques | Majeurs | Mineurs | Total |
|-----------|-----------|---------|---------|-------|
| BIZ — Logique métier | 5 | 8 | 4 | 17 |
| UX — Expérience utilisateur | 2 | 5 | 6 | 13 |
| TPL — Template / Contrôleur | 3 | 4 | 3 | 10 |
| SEC — Sécurité applicative | 2 | 3 | 1 | 6 |
| **TOTAL** | **12** | **20** | **14** | **46** |

---

## BIZ — Logique métier

### BIZ-01 · CRITICAL — Idempotence en mémoire perdue au redémarrage
- **Fichier** : `src/main/java/com/gedavocat/controller/SubscriptionController.java` L39-L41
- **Description** : Les sets `processedSessionIds` et `processedEventIds` (ConcurrentHashMap) sont volatils. Tout redémarrage de l'application (déploiement, crash, scaling) efface l'historique d'idempotence. Un même événement Stripe peut alors être retraité : double activation d'abonnement, double prolongation.
- **Reproduction** :
  1. Souscrire un abonnement (session Checkout traitée).
  2. Redémarrer l'application.
  3. L'utilisateur rafraîchit `/subscription/success?session_id=…` → l'abonnement est réactivé / la date de fin recalculée.
- **Correctif recommandé** : Stocker les identifiants traités en base (table `processed_stripe_events`) ou dans Redis. Ajouter un index unique sur l'event_id.

---

### BIZ-02 · CRITICAL — Fuite mémoire par clear() brutal du set d'idempotence
- **Fichier** : `src/main/java/com/gedavocat/controller/SubscriptionController.java` L410-L412
- **Description** : `if (processedEventIds.size() > 10_000) { processedEventIds.clear(); }` — quand le set atteint 10 000 entrées, il est entièrement vidé. Tous les événements déjà traités sont oubliés d'un coup, rouvrant la porte aux doublons massifs. C'est pire que le problème qu'il essaie de résoudre.
- **Reproduction** : Recevoir > 10 000 webhooks → clear() → les webhooks reçus juste avant le clear() peuvent être rejoués.
- **Correctif recommandé** : Supprimer les entrées les plus anciennes (LRU / TTL) ou persister en base.

---

### BIZ-03 · CRITICAL — Double endpoint d'annulation d'abonnement
- **Fichier** : `src/main/java/com/gedavocat/controller/SubscriptionController.java` L330 (`POST /subscription/cancel-subscription`) et `src/main/java/com/gedavocat/controller/PaymentController.java` (contient aussi un `POST /payment/cancel-subscription`)
- **Description** : Deux endpoints distincts permettent d'annuler un abonnement. Ils n'ont pas la même logique (l'un distingue trial vs payé, l'autre non). Un utilisateur ou un attaquant pourrait appeler l'un puis l'autre et produire un état incohérent.
- **Reproduction** : POST vers `/payment/cancel-subscription` puis POST vers `/subscription/cancel-subscription` (ou vice-versa).
- **Correctif recommandé** : Supprimer le doublon dans `PaymentController` et ne garder qu'un seul point d'entrée canonique.

---

### BIZ-04 · CRITICAL — PAYMENT_FAILED exclut l'accès sans grâce
- **Fichier** : `src/main/java/com/gedavocat/model/User.java` L335-L345
- **Description** : `hasActiveSubscription()` ne reconnaît que `ACTIVE`, `TRIAL`, `CANCELLED`. Un utilisateur dont le paiement est en échec (`PAYMENT_FAILED` — mis par le webhook `invoice.payment_failed`) perd instantanément l'accès, sans période de grâce ni notification. Stripe laisse pourtant typiquement 3 à 7 jours de retry.
- **Reproduction** :
  1. Un utilisateur a un abonnement `ACTIVE`.
  2. Son paiement échoue (carte expirée).
  3. Le webhook positionne `PAYMENT_FAILED`.
  4. L'utilisateur ne peut plus accéder à aucune fonctionnalité immédiatement.
- **Correctif recommandé** : Ajouter `PAYMENT_FAILED` à `hasActiveSubscription()` avec une grâce de 7 jours, ou créer un statut `PAST_DUE` distinct. Envoyer un email de relance.

---

### BIZ-05 · CRITICAL — Plan de pricing incohérent entre template et code
- **Fichier** : `src/main/resources/templates/subscription/pricing.html` L385-L420 (noms : ESSENTIEL / PROFESSIONNEL / CABINET_PLUS) vs `src/main/java/com/gedavocat/model/User.java` (enum SubscriptionPlan contient aussi SOLO / CABINET / ENTERPRISE)
- **Description** : La page pricing affiche les anciens plans (ESSENTIEL 49€, PROFESSIONNEL 99€, CABINET_PLUS 199€) et dirige vers `/register?plan=ESSENTIEL`. Le StripeService configure aussi ces plans legacy. Pourtant l'enum `SubscriptionPlan` contient également SOLO, CABINET, ENTERPRISE (nouveaux plans). Aucune page ne propose les nouveaux plans. Les prix Stripe ne correspondent qu'aux anciens.
- **Reproduction** : Aller sur `/subscription/pricing` → seuls les plans legacy sont visibles. Si un code tente `SubscriptionPlan.valueOf("SOLO")` ça marche, mais il n'y a pas de priceId Stripe correspondant.
- **Correctif recommandé** : Aligner les plans : soit nettoyer l'enum des plans inutilisés, soit mettre à jour la page pricing et le StripeService avec les bons plans. Documenter la migration.

---

### BIZ-06 · MAJOR — Dashboard metrics.unpaidInvoices et metrics.upcomingDeadlines toujours à 0
- **Fichier** : `src/main/java/com/gedavocat/controller/DashboardController.java` L156-L159
- **Description** : Les métriques `unpaidInvoices` et `upcomingDeadlines` sont codées en dur à `0` avec le commentaire `// à implémenter`. Le template dashboard affiche ces KPI avec des fallbacks hardcodés (`5` factures impayées, `8` échéances) quand la valeur est `null`, ce qui affiche des chiffres **faux** si le modèle est correctement alimenté. Si `metrics.unpaidInvoices` vaut `0` (la valeur codée en dur), le KPI affiche `0` au lieu de la réalité.
- **Reproduction** : Se connecter en tant qu'avocat → le dashboard montre `0` factures impayées même s'il y en a.
- **Correctif recommandé** : Requêter `InvoiceRepository` pour compter les factures impayées. Requêter les RDV/échéances dans les 7 prochains jours.

---

### BIZ-07 · MAJOR — Dashboard activité récente hardcodée dans le template
- **Fichier** : `src/main/resources/templates/dashboard/index.html` L66-L109
- **Description** : La section « Activité récente » affiche du contenu statique (« Dossier clôturé — Affaire DUPONT vs MARTIN — 26 février », etc.). Le contrôleur prépare pourtant `recentActivities` depuis les `AuditLog`, mais le template **n'utilise jamais cet attribut** et affiche du contenu fictif.
- **Reproduction** : Se connecter → la timeline montre toujours les mêmes activités inventées, quelle que soit l'activité réelle de l'utilisateur.
- **Correctif recommandé** : Remplacer le HTML statique par une boucle Thymeleaf `th:each="activity : ${recentActivities}"`.

---

### BIZ-08 · MAJOR — Variables openCount/closedCount/archivedCount non alimentées dans CaseController.listCases()
- **Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java` L43-L56 (controller) vs `src/main/resources/templates/cases/list.html` L156-L176 (template)
- **Description** : Le template `cases/list.html` affiche des KPI de statut (`${openCount}`, `${closedCount}`, `${archivedCount}`) mais le contrôleur `CaseController.listCases()` ne met PAS ces attributs dans le modèle. Résultat : les compteurs affichent `0` grâce au fallback `?:` dans le template, même quand il y a des dossiers.
- **Reproduction** : Aller sur `/cases` → les compteurs « En cours », « Clôturés », « Archivés » affichent tous `0`.
- **Correctif recommandé** : Ajouter dans `listCases()` :
  ```java
  model.addAttribute("openCount", cases.stream().filter(c -> c.getStatus() == CaseStatus.OPEN).count());
  model.addAttribute("closedCount", cases.stream().filter(c -> c.getStatus() == CaseStatus.CLOSED).count());
  model.addAttribute("archivedCount", cases.stream().filter(c -> c.getStatus() == CaseStatus.ARCHIVED).count());
  ```

---

### BIZ-09 · MAJOR — CaseController.viewCase() ne fournit pas permissions, shareMap, appointments
- **Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java` L100-L112 (controller) vs `src/main/resources/templates/cases/view.html` L400-L465 (template)
- **Description** : Le template `cases/view.html` référence :
  - `${permissions}` pour la sidebar « Intervenants » (collaborateurs/huissiers)
  - `${shareMap}` pour les checkboxes de partage de documents
  - `${appointments}` pour la section rendez-vous
  
  Mais `CaseController.viewCase()` ne met que `case`, `documents` et `documentCount` dans le modèle. Les trois attributs manquants provoqueront une évaluation à `null` côté Thymeleaf, affichant systématiquement la vue vide (« Aucun intervenant », checkboxes non cochées, etc.) même quand des données existent.
- **Reproduction** : Associer un collaborateur et un RDV à un dossier → afficher `/cases/{id}` → les sections sont vides.
- **Correctif recommandé** : Injecter `PermissionRepository` et `AppointmentService`, charger les données : `permissions`, `shareMap` (via `DocumentShareService`), `appointments`.

---

### BIZ-10 · MAJOR — cases/view.html : compteur Signatures hardcodé à 0
- **Fichier** : `src/main/resources/templates/cases/view.html` L128
- **Description** : Le KPI « Signatures » affiche `0` en dur dans le HTML au lieu d'utiliser une variable du modèle. Le contrôleur ne fournit pas non plus de count de signatures.
- **Reproduction** : Visualiser un dossier avec des signatures → le KPI affiche toujours `0`.
- **Correctif recommandé** : Ajouter `signatureCount` au modèle et utiliser `th:text="${signatureCount}"`.

---

### BIZ-11 · MAJOR — ClientController InitBinder bloque accessEndsAt
- **Fichier** : `src/main/java/com/gedavocat/controller/ClientController.java` L43-L45
- **Description** : L'`@InitBinder` autorise uniquement les champs `name, email, phone, address, clientType, companyName, siret`. Le formulaire `clients/form.html` contient un champ `accessEndsAt` (date de fin d'accès du client) qui est silencieusement ignoré par le binding → le champ ne sera jamais persisté malgré sa présence dans le formulaire.
- **Reproduction** :
  1. Éditer un client et définir une date « Fin d'accès ».
  2. Sauvegarder.
  3. La date n'est pas enregistrée.
- **Correctif recommandé** : Ajouter `"accessEndsAt"` à la liste `setAllowedFields()`, ou retirer le champ du formulaire s'il n'est pas souhaité.

---

### BIZ-12 · MAJOR — clients/form.html utilise firstName/lastName mais InitBinder autorise "name"
- **Fichier** : `src/main/java/com/gedavocat/controller/ClientController.java` L43-L45 vs le formulaire `clients/form.html`
- **Description** : L'InitBinder autorise le champ `"name"` mais le formulaire HTML utilise `th:field="*{firstName}"` et `th:field="*{lastName}"`. Si le modèle `Client` sépare `firstName` et `lastName`, ces champs sont bloqués par l'InitBinder. Inversement, si `Client` n'a qu'un champ `name`, le formulaire bind sur des champs inexistants.
- **Reproduction** : Créer un client → selon le modèle Client, soit le nom n'est pas bindé, soit il y a une erreur silencieuse.
- **Correctif recommandé** : Aligner l'InitBinder avec les vrais noms de champs du modèle Client.

---

### BIZ-13 · MAJOR — Trust section pricing page dit "PayPlug" alors que le paiement passe par Stripe
- **Fichier** : `src/main/resources/templates/subscription/pricing.html` L459-L462
- **Description** : La section de confiance affiche « Powered by PayPlug — Solution française certifiée PCI-DSS ». La FAQ confirme « via notre partenaire PayPlug ». Pourtant le paiement réel est géré par `StripeService`. Cette information trompeuse peut poser un problème légal et de confiance.
- **Reproduction** : Visiter `/subscription/pricing` → section « Paiement 100% sécurisé ».
- **Correctif recommandé** : Remplacer par « Powered by Stripe » ou rendre dynamique selon le provider actif.

---

### BIZ-14 · MINOR — Pas de détection de conflit pour les rendez-vous
- **Fichier** : `src/main/java/com/gedavocat/controller/AppointmentController.java` et `AppointmentService.java`
- **Description** : Il n'existe aucune vérification empêchant la création de deux rendez-vous qui se chevauchent pour le même avocat au même horaire.
- **Reproduction** : Créer deux RDV à la même date/heure pour le même avocat → les deux sont enregistrés sans avertissement.
- **Correctif recommandé** : Ajouter une requête `findOverlapping(lawyerId, start, end)` et rejeter ou avertir en cas de conflit.

---

### BIZ-15 · MINOR — Collaborateurs ne peuvent ni télécharger ni prévisualiser les documents
- **Fichier** : `src/main/java/com/gedavocat/controller/CollaboratorPortalController.java` L166-L178
- **Description** : Les endpoints download et preview retournent systématiquement `403 Forbidden`. Les collaborateurs peuvent uploader des documents mais pas les consulter. Cela rend la collaboration inefficace.
- **Reproduction** : Se connecter en tant que collaborateur → cliquer sur prévisualiser/télécharger → `403`.
- **Correctif recommandé** : Implémenter le téléchargement/prévisualisation avec vérification d'accès (via `DocumentShareService`), ou au minimum documenter cette limitation dans l'interface.

---

### BIZ-16 · MINOR — HuissierPortalController.updateProfile() ne valide pas les entrées
- **Fichier** : `src/main/java/com/gedavocat/controller/HuissierPortalController.java` L200-L207
- **Description** : `phone` et `officeNumber` sont directement affectés à l'entity User sans aucune validation (regex, longueur). Contrairement au `ClientPortalController.updateProfile()` qui valide rigoureusement le téléphone et les autres champs.
- **Reproduction** : POST vers `/my-cases-huissier/profile` avec `phone=<script>alert(1)</script>`.
- **Correctif recommandé** : Appliquer la même validation regex que pour le profil client.

---

### BIZ-17 · MINOR — CollaboratorPortalController.updateProfile() ne valide pas non plus
- **Fichier** : `src/main/java/com/gedavocat/controller/CollaboratorPortalController.java` L299-L307
- **Description** : Même problème que BIZ-16 pour le profil collaborateur.
- **Correctif recommandé** : Appliquer la même validation que pour le profil client.

---

## UX — Expérience utilisateur

### UX-01 · CRITICAL — Dashboard Quick Actions et Header utilisent href hardcodé au lieu de th:href
- **Fichier** : `src/main/resources/templates/dashboard/index.html` L24-L28, L123-L149
- **Description** : Les liens « Nouveau dossier » (`href="/cases/new"`), « Nouveau client » (`href="/clients/new"`), et les 3 actions rapides utilisent des `href` HTML bruts au lieu de `th:href="@{...}"`. Si l'application est déployée avec un context path (ex: `/app/`), tous ces liens seront cassés.
- **Reproduction** : Déployer l'app avec `server.servlet.context-path=/app` → cliquer sur « Nouveau dossier » → 404.
- **Correctif recommandé** : Remplacer tous les `href="/..."` par `th:href="@{/...}"`.

---

### UX-02 · CRITICAL — Suppression de facture (AJAX DELETE) sans token CSRF → échec systématique 403
- **Fichier** : `src/main/resources/templates/invoices/index.html` L270-L295
- **Description** : La fonction `deleteInvoice()` effectue un `fetch('/api/invoices/' + id, { method: 'DELETE' })` mais n'inclut PAS le header `X-CSRF-TOKEN`. Spring Security rejette la requête avec un 403 Forbidden. Le CSRF token est disponible dans les meta tags de `layout.html` mais n'est pas lu ici.
- **Reproduction** :
  1. Aller sur `/invoices`.
  2. Cliquer sur « Supprimer » une facture.
  3. Confirmer → erreur « Erreur lors de la suppression ».
- **Correctif recommandé** :
  ```javascript
  headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
  }
  ```

---

### UX-03 · MAJOR — Pricing page est autonome (pas de layout.html) → pas de sidebar ni navigation
- **Fichier** : `src/main/resources/templates/subscription/pricing.html` L1-L8
- **Description** : La page pricing n'utilise pas `layout:decorate="~{layout}"`. C'est une page HTML complète autonome avec sa propre navbar minimale (seulement « Se connecter » / « Accéder à l'app »). Si un utilisateur connecté accède à `/subscription/pricing`, il n'a plus la sidebar de navigation et doit utiliser le bouton retour du navigateur.
- **Reproduction** : Se connecter → aller sur `/subscription/pricing`.
- **Correctif recommandé** : Créer deux versions (authentifié et public) ou intégrer dans le layout avec un flag pour cacher la sidebar.

---

### UX-04 · MAJOR — KPI Dashboard affichent des données factices quand null
- **Fichier** : `src/main/resources/templates/dashboard/index.html` L41-L56
- **Description** : Les KPI utilisent des fallbacks comme `${metrics.activeCases != null ? metrics.activeCases : 12}`, `${metrics.unpaidInvoices != null ? metrics.unpaidInvoices : 5}`, etc. Si `metrics` est null (ce qui ne devrait pas arriver mais pourrait), le dashboard affiche **12 dossiers, 5 factures impayées, 3 signatures, 8 échéances** en données complètement fictives. Les sous-titres (« +2 ce mois-ci », « -1 cette semaine ») sont aussi hardcodés.
- **Reproduction** : Le dashboard affiche toujours « +2 ce mois-ci » et « -1 cette semaine » quelle que soit la réalité.
- **Correctif recommandé** : Retirer les fallbacks fictifs (utiliser `0`), rendre les sous-titres dynamiques ou les supprimer.

---

### UX-05 · MAJOR — document-share toggle AJAX n'envoie pas de CSRF token
- **Fichier** : `src/main/resources/templates/cases/view.html` L542-L570
- **Description** : Les toggles de partage de documents (checkboxes collaborateur/huissier) et le bulk toggle effectuent des fetch POST vers `/api/document-shares/toggle` et `/api/document-shares/bulk-toggle` sans header CSRF. Selon la configuration de Spring Security, ces requêtes seront rejetées en 403.
- **Reproduction** : Sur `/cases/{id}`, activer/désactiver une checkbox de partage → erreur réseau ou 403.
- **Correctif recommandé** : Ajouter `'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content` aux headers fetch.

---

### UX-06 · MAJOR — cases/view.html « Nouveau rendez-vous » redirige vers /appointments sans contexte de dossier
- **Fichier** : `src/main/resources/templates/cases/view.html` L337 et L459
- **Description** : Le bouton « Nouveau » pour les RDV utilise `th:href="@{/appointments}"` — il mène à la liste générale des rendez-vous, pas à la création d'un RDV lié au dossier en cours. L'utilisateur perd le contexte du dossier.
- **Reproduction** : Sur `/cases/ABC` → cliquer « Nouveau rendez-vous » → redirigé vers `/appointments` sans lien avec le dossier.
- **Correctif recommandé** : Diriger vers `@{/appointments/new(caseId=${case.id})}` si un tel endpoint existe, sinon le créer.

---

### UX-07 · MAJOR — Formulaire d'import facture action="/invoices" hardcodé
- **Fichier** : `src/main/resources/templates/invoices/index.html` (formulaire d'import)
- **Description** : Le formulaire d'import utilise `action="/invoices"` au lieu de `th:action="@{/invoices}"`. Problème de context-path.
- **Reproduction** : Déployer avec un context-path non-root → import cassé.
- **Correctif recommandé** : Utiliser `th:action="@{/invoices/import}"`.

---

### UX-08 · MINOR — Absence d'état vide pour les signatures dans cases/view.html
- **Fichier** : `src/main/resources/templates/cases/view.html` L120-L135
- **Description** : La section KPI Signatures existe mais il n'y a pas de section « Signatures » dédiée dans le contenu (contrairement à Documents et Rendez-vous). Les signatures du dossier ne sont visibles nulle part sur la page de détail.
- **Reproduction** : Un dossier a des signatures → elles ne sont pas listées sur `/cases/{id}`.
- **Correctif recommandé** : Ajouter un onglet/section « Signatures » avec la liste, ou lier vers `/signatures?caseId=...`.

---

### UX-09 · MINOR — Breadcrumb et navigation hors layout non cohérents
- **Fichier** : Multiples templates portal (client-portal, collaborator-portal, huissier-portal)
- **Description** : Les portails n'ont pas de breadcrumb cohérent. Le client-portal utilise `pending.html` comme page d'erreur d'accès (qui ne contient pas de navigation), laissant l'utilisateur sans moyen de revenir en arrière.
- **Correctif recommandé** : Uniformiser les pages d'erreur d'accès avec un bouton retour.

---

### UX-10 · MINOR — 20+ liens hardcodés sans th:href dans les templates
- **Fichier** : Multiples templates (dashboard, invoices, appointments…)
- **Description** : Au moins 20 occurrences de `href="/cases/new"`, `href="/invoices"`, etc. au lieu de `th:href="@{/...}"`.
- **Liste partielle** :
  - `dashboard/index.html` : 5 occurrences (header + quick actions)
  - `invoices/index.html` : formulaire filter action
  - `layout.html` : `scanner.js` src
- **Correctif recommandé** : Rechercher et remplacer tous les `href="/"` par `th:href="@{/}"`.

---

### UX-11 · MINOR — RPVA : liste des juridictions hardcodée (5 seulement)
- **Fichier** : `src/main/java/com/gedavocat/controller/RPVAController.java` L130-L136
- **Description** : Seuls 5 tribunaux sont proposés (Paris, Lyon, Marseille, CA Paris, Cour de Cassation). La France compte ~164 TJ et 36 CA.
- **Correctif recommandé** : Charger les juridictions depuis une base de données ou un fichier de référence.

---

### UX-12 · MINOR — Page pricing : billing toggle JS ne met pas à jour le paramètre pour redirect vers checkout
- **Fichier** : `src/main/resources/templates/subscription/pricing.html` L543-L556
- **Description** : Le JS du toggle billing met à jour `btn.href = '/register?plan=...&billing=...'` mais les boutons mènent à `/register` (inscription) et non à `/subscription/checkout`. Le parcours est : pricing → register → email verification → login → checkout. Le paramètre `billing` (monthly/annual) est passé via register mais rien ne garantit qu'il persiste jusqu'au checkout.
- **Reproduction** : Choisir « Annuel » → s'inscrire → le checkout peut créer un abonnement mensuel par défaut.
- **Correctif recommandé** : S'assurer que le paramètre `billing` est persisté (en session ou dans l'URL redirection post-inscription).

---

### UX-13 · MINOR — CollaboratorPortalController : download/preview retournent 403 sans message explicatif
- **Fichier** : `src/main/java/com/gedavocat/controller/CollaboratorPortalController.java` L166-L178
- **Description** : Les endpoints retournent un ResponseEntity 403 vide. L'utilisateur voit une page blanche ou une erreur navigateur sans explication.
- **Correctif recommandé** : Retourner une page d'erreur avec message « Le téléchargement n'est pas autorisé pour les collaborateurs ».

---

## TPL — Template / Contrôleur (mismatches)

### TPL-01 · CRITICAL — cases/view.html référence ${permissions} non alimenté par CaseController
- **Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java` L100-L112 vs `src/main/resources/templates/cases/view.html` L400-L440
- **Description** : Voir BIZ-09. Le template affiche la sidebar « Intervenants » avec une boucle sur `${permissions}`, mais le contrôleur ne fournit pas cet attribut. Résultat : section toujours vide.
- **Impact** : L'avocat ne voit jamais ses collaborateurs/huissiers sur la page dossier.

---

### TPL-02 · CRITICAL — cases/view.html référence ${shareMap} non alimenté par CaseController
- **Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java` vs `src/main/resources/templates/cases/view.html` L285-L300
- **Description** : Les checkboxes de partage de documents utilisent `th:checked="${shareMap != null and shareMap.containsKey(doc.id) ...}"`. Le contrôleur ne fournit pas `shareMap`. Toutes les checkboxes apparaissent décochées même si des partages existent.
- **Impact** : L'état de partage des documents est invisible.

---

### TPL-03 · CRITICAL — cases/view.html référence ${appointments} non alimenté par CaseController
- **Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java` vs `src/main/resources/templates/cases/view.html` L103-L108, L330-L350
- **Description** : Le KPI « Rendez-vous » utilise `${#lists.size(appointments)}` et la section affiche les RDV via `th:each="apt : ${appointments}"`. Le contrôleur ne fournit pas `appointments`. Le KPI affiche `0` et la liste est vide.
- **Impact** : Les rendez-vous liés au dossier ne sont jamais visibles.

---

### TPL-04 · MAJOR — cases/list.html KPI : openCount/closedCount/archivedCount absents
- **Fichier** : Voir BIZ-08.
- **Description** : Template attend ces variables, le contrôleur ne les fournit pas.
- **Impact** : Les compteurs de statut affichent tous `0`.

---

### TPL-05 · MAJOR — dashboard/index.html n'utilise pas recentActivities (contenu hardcodé)
- **Fichier** : Voir BIZ-07.
- **Description** : Le contrôleur prépare `recentActivities` depuis les `AuditLog`, mais le template ignore cet attribut et affiche du contenu statique fictif.

---

### TPL-06 · MAJOR — InvoiceWebController.show() passe null comme lawyerId au service
- **Fichier** : `src/main/java/com/gedavocat/controller/InvoiceWebController.java` L218
- **Description** : `invoiceService.getInvoiceById(id, null)` — le service reçoit `null` comme lawyerId. Selon l'implémentation du service, cela peut (a) lancer une SecurityException, (b) bypasser la vérification ownership, ou (c) retourner la facture sans vérification. Le contrôleur effectue sa propre vérification ownership ensuite, mais le comportement du service avec `null` est fragile.
- **Reproduction** : Accéder à `/invoices/{id}` → comportement dépend de l'implémentation exacte du service.
- **Correctif recommandé** : Passer `user.getId()` avec un flag admin ou utiliser une surcharge explicite `getInvoiceByIdNoFilter(id)`.

---

### TPL-07 · MAJOR — InvoiceController utilise des conventions de string fragiles pour l'ownership
- **Fichier** : `src/main/java/com/gedavocat/controller/InvoiceController.java` L103-L113
- **Description** : L'ownership checking utilise des conventions de strings magiques : `"ADMIN_BYPASS"` et `"CLIENT_" + userId`. Ce pattern est fragile, non typé, et pourrait mener à des bypasses si le lawyerId d'un utilisateur commence par "ADMIN_BYPASS" ou "CLIENT_".
- **Correctif recommandé** : Utiliser un enum ou une surcharge de méthode explicite (`getInvoiceByIdAsAdmin()`, `getInvoiceByIdAsClient(clientId)`).

---

### TPL-08 · MINOR — layout.html charge scanner.js avec href hardcodé
- **Fichier** : `src/main/resources/templates/layout.html` L494
- **Description** : `<script src="/js/scanner.js" defer>` au lieu de `th:src="@{/js/scanner.js}"`. Problème de context-path.
- **Correctif recommandé** : Utiliser `th:src`.

---

### TPL-09 · MINOR — CaseController.viewCase() utilise RuntimeException au lieu d'AccessDeniedException
- **Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java` L103
- **Description** : `throw new RuntimeException("Acces non autorise")` au lieu de `throw new AccessDeniedException(...)`. Cela retourne un 500 au lieu d'un 403, et le message d'erreur est en ASCII sans accents.
- **Correctif recommandé** : `throw new AccessDeniedException("Accès non autorisé à ce dossier")`.

---

### TPL-10 · MINOR — RPVAController.sendCommunication() ne vérifie pas l'ownership de l'utilisateur sur le dossier
- **Fichier** : `src/main/java/com/gedavocat/controller/RPVAController.java` L156-L180
- **Description** : Le POST `/rpva/send` vérifie que l'utilisateur existe mais ne déduit pas le dossier depuis l'entrée `caseReference` et ne vérifie pas l'ownership. Le GET `/rpva/send` vérifie l'ownership du dossier si `caseId` est passé, mais le POST ne le fait pas.
- **Correctif recommandé** : Si un `caseId` est lié à la communication, vérifier l'ownership dans le POST aussi.

---

## SEC — Sécurité applicative

### SEC-01 · CRITICAL — InvoiceWebController.importInvoice() : Path Traversal potentiel sur le nom de fichier
- **Fichier** : `src/main/java/com/gedavocat/controller/InvoiceWebController.java` L166-L172
- **Description** : Le nom du fichier uploadé est nettoyé avec `replaceAll("[^a-zA-Z0-9._-]", "_")` mais est préfixé d'un UUID, ce qui atténue partiellement le risque. Cependant, le fichier est transféré avec `file.transferTo(dest.toFile())` et le `docUrl` est stocké en base comme `/uploads/invoices/{filename}`. Si un endpoint sert statiquement ce dossier, un fichier malveillant (ex: `.jsp`, `.html`) pourrait être exécuté.
- **Correctif recommandé** : Valider le Content-Type du fichier (n'accepter que PDF/images), utiliser un nom entièrement généré (UUID seul), et servir les fichiers avec `Content-Disposition: attachment`.

---

### SEC-02 · CRITICAL — InvoiceWebController stocke les fichiers sur le système de fichiers local sans chiffrement
- **Fichier** : `src/main/java/com/gedavocat/controller/InvoiceWebController.java` L166-L172
- **Description** : Les factures importées sont stockées en clair sur le filesystem à `{uploadDir}/../invoices/`. En SaaS multi-tenant avec des données d'avocat (secret professionnel), les fichiers devraient être chiffrés au repos.
- **Correctif recommandé** : Chiffrer les fichiers au repos (AES-256-GCM) ou utiliser un stockage objet (S3) avec chiffrement côté serveur.

---

### SEC-03 · MAJOR — Pas de @PreAuthorize sur plusieurs endpoints de InvoiceWebController
- **Fichier** : `src/main/java/com/gedavocat/controller/InvoiceWebController.java` L140 (import)
- **Description** : L'endpoint `POST /invoices/import` a `@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN')")` mais certains endpoints (comme le controller class-level) n'ont pas d'annotation globale. Si un nouvel endpoint est ajouté sans `@PreAuthorize`, il sera accessible à tous les rôles.
- **Correctif recommandé** : Ajouter `@PreAuthorize` au niveau de la classe.

---

### SEC-04 · MAJOR — RPVA : RPVAController utilise RuntimeException au lieu d'exceptions de sécurité typées
- **Fichier** : `src/main/java/com/gedavocat/controller/RPVAController.java` (multiples endroits)
- **Description** : Les erreurs « Utilisateur non trouvé » lancent des `RuntimeException`. Si l'exception est attrapée par un handler global qui affiche le message, cela peut révéler des informations internes.
- **Correctif recommandé** : Utiliser des exceptions typées (`UsernameNotFoundException`, `AccessDeniedException`).

---

### SEC-05 · MAJOR — CollaboratorPortalController et HuissierPortalController : updateProfile() sans validation d'entrée
- **Fichier** : Voir BIZ-16 et BIZ-17.
- **Description** : Pas de validation des champs phone/barNumber/officeNumber. Stockage direct de l'entrée utilisateur. Risque de XSS stocké si ces valeurs sont affichées sans échappement dans un template.
- **Correctif recommandé** : Valider avec regex, limiter la longueur, et s'assurer que Thymeleaf échappe les valeurs (ce qui est le cas par défaut avec `th:text`).

---

### SEC-06 · MINOR — Webhook Stripe retourne le message d'exception dans le body
- **Fichier** : `src/main/java/com/gedavocat/controller/SubscriptionController.java` L456
- **Description** : Le webhook retourne `ResponseEntity.badRequest().body("Erreur: " + e.getMessage())`. En production, le message d'exception peut contenir des détails d'implémentation (noms de classe, stack trace partiel).
- **Correctif recommandé** : Retourner un message générique `"Webhook processing error"` et loguer le détail côté serveur.

---

## Résumé des actions prioritaires

### Priorité 1 — Bloquants (à corriger immédiatement)
| ID | Action |
|----|--------|
| BIZ-01 | Persister l'idempotence Stripe en base |
| BIZ-04 | Ajouter PAYMENT_FAILED avec grâce dans hasActiveSubscription() |
| UX-02 | Ajouter CSRF token au DELETE facture |
| BIZ-09 / TPL-01-03 | Alimenter permissions, shareMap, appointments dans CaseController.viewCase() |
| SEC-01 | Valider type MIME et sécuriser le stockage des fichiers importés |

### Priorité 2 — Impact métier fort (semaine courante)
| ID | Action |
|----|--------|
| BIZ-05 | Aligner plans pricing ↔ enum ↔ Stripe |
| BIZ-06-07 | Rendre dashboard dynamique (activités réelles, métriques calculées) |
| BIZ-08 / TPL-04 | Ajouter openCount/closedCount/archivedCount dans CaseController |
| BIZ-11-12 | Corriger InitBinder clients (accessEndsAt, firstName/lastName) |
| BIZ-13 | Corriger « Powered by PayPlug » → « Powered by Stripe » |
| UX-05 | Ajouter CSRF aux toggles de partage de documents |

### Priorité 3 — Améliorations (sprint suivant)
| ID | Action |
|----|--------|
| BIZ-03 | Supprimer le double endpoint cancel |
| BIZ-14 | Détection de conflits RDV |
| BIZ-15 | Permettre download/preview aux collaborateurs |
| UX-01/10 | Remplacer tous les href hardcodés par th:href |
| TPL-09 | RuntimeException → AccessDeniedException |
| SEC-06 | Masquer les messages d'erreur webhook |
