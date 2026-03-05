# AUDIT EXHAUSTIF — DocAvocat (GedAvocat)
**Date** : 5 mars 2026  
**Périmètre** : Application complète (37 contrôleurs, ~155 routes, 82 templates, 6 rôles)  
**Méthodologie** : Analyse statique de code + cartographie des flux + simulation d'attaques

---

## TABLE DES MATIÈRES

1. [Bugs Critiques](#1-bugs-critiques)
2. [Bugs Fonctionnels](#2-bugs-fonctionnels)
3. [Failles de Sécurité](#3-failles-de-sécurité)
4. [Problèmes UX](#4-problèmes-ux)
5. [Problèmes Performance](#5-problèmes-performance)
6. [Liens Cassés / Incohérences de Routage](#6-liens-cassés--incohérences-de-routage)
7. [Workflows Cassés](#7-workflows-cassés)
8. [Cas Limites Problématiques](#8-cas-limites-problématiques)
9. [Résumé Priorisé](#9-résumé-priorisé)

---

## 1. BUGS CRITIQUES

### CRIT-01 — Rafraîchir la page de succès Stripe prolonge l'abonnement gratuitement
- **Fichier** : `src/main/java/com/gedavocat/controller/SubscriptionController.java` (lignes 97-120)
- **Description** : `GET /subscription/success?session_id=xxx` appelle `activateSubscription()` à chaque chargement. Aucune vérification si l'utilisateur est déjà ACTIVE. Chaque rafraîchissement recalcule `subscriptionEndsAt = now() + 1 mois`, prolongeant l'abonnement indéfiniment.
- **Gravité** : 🔴 CRITIQUE
- **Étapes de reproduction** :
  1. Souscrire un abonnement via Stripe
  2. Arriver sur `/subscription/success?session_id=xxx`
  3. Rafraîchir la page → `subscriptionEndsAt` avance d'un mois à chaque refresh
  4. Mettre le lien en favoris et y revenir dans 1 mois → re-prolongation gratuite
- **Cause** : Absence de garde d'idempotence dans `paymentSuccess()`
- **Solution** : Ajouter un check `if (user.getSubscriptionStatus() == ACTIVE && user.getSubscriptionEndsAt().isAfter(now())) return;` au début de la méthode. Ou mieux : ne pas activer via la page success mais uniquement via le webhook.

---

### CRIT-02 — Utilisateur sans cabinet (firm=null) voit TOUTES les données de tous les cabinets
- **Fichier** : `src/main/java/com/gedavocat/security/MultiTenantFilter.java` (lignes 80-103)
- **Description** : Quand `user.getFirm()` est null et l'utilisateur n'est pas ADMIN, le filtre Hibernate de tenant n'est PAS appliqué. Le code log un `SEC-ALERT` mais appelle `filterChain.doFilter()` sans aucune restriction. L'utilisateur accède à TOUTES les données : clients, dossiers, documents de TOUS les cabinets.
- **Gravité** : 🔴 CRITIQUE
- **Étapes de reproduction** :
  1. Créer un utilisateur LAWYER dont la création de Firm échoue (ou supprimer le firm manuellement)
  2. Se connecter
  3. Accéder à `/clients` → tous les clients de tous les cabinets sont visibles
- **Cause** : Le commentaire dit "REFUSER l'accès" mais le code ne bloque PAS la requête
- **Solution** : Ajouter `response.sendError(403)` ou `response.sendRedirect("/error")` + `return` quand `firm` est null

---

### CRIT-03 — Deux systèmes de paiement parallèles (Stripe + PayPlug) écrivent sur les mêmes champs
- **Fichiers** : `SubscriptionController.java` (Stripe) + `PaymentController.java` (PayPlug)
- **Description** : Deux contrôleurs indépendants modifient `subscriptionPlan`, `subscriptionStatus`, `subscriptionEndsAt` sur le même objet `User`. Aucune coordination. Un webhook Stripe peut écraser l'état setté par PayPlug et inversement.
- **Gravité** : 🔴 CRITIQUE
- **Solution** : Désactiver un des deux systèmes ou ajouter un champ `paymentProvider` et n'autoriser qu'un seul fournisseur par utilisateur.

---

### CRIT-04 — L'annulation PayPlug ne contacte PAS PayPlug → l'utilisateur continue à être débité
- **Fichier** : `src/main/java/com/gedavocat/controller/PaymentController.java` (lignes 170-188)
- **Description** : `POST /payment/cancel-subscription` ne fait que `user.setSubscriptionStatus(CANCELLED)` en base. Aucun appel API à PayPlug. Le prélèvement automatique continue.
- **Gravité** : 🔴 CRITIQUE
- **Solution** : Appeler l'API PayPlug pour annuler le paiement récurrent, ou si PayPlug n'offre pas cette fonctionnalité, en informer clairement l'utilisateur.

---

### CRIT-05 — Le webhook `invoice.payment_succeeded` n'est pas idempotent et prolonge +1 mois même pour les abonnements annuels
- **Fichier** : `src/main/java/com/gedavocat/controller/SubscriptionController.java` (lignes 494-512)
- **Description** : (a) Toujours `plusMonths(1)` sans vérifier si l'abonnement est mensuel ou annuel. (b) Si Stripe retente le webhook, l'abonnement est prolongé à chaque tentative.
- **Gravité** : 🔴 CRITIQUE
- **Solution** : (a) Lire le `billing_interval` depuis l'objet Invoice/Subscription Stripe. (b) Stocker les event IDs traités pour garantir l'idempotence.

---

## 2. BUGS FONCTIONNELS

### FUNC-01 — Le downgrade de plan prend effet immédiatement (pas en fin de période)
- **Fichier** : `SubscriptionController.java` (lignes 228-240)
- **Description** : Quand un utilisateur downgrade, `user.setSubscriptionPlan(newPlan)` est appelé immédiatement côté DB. L'UX affiche "Le changement prend effet au prochain renouvellement" (change-plan.html L264), mais le code l'applique instantanément.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Stocker le plan futur dans un champ `pendingPlan` et l'appliquer via webhook au renouvellement.

---

### FUNC-02 — Le webhook `customer.subscription.updated` n'actualise jamais le plan
- **Fichier** : `SubscriptionController.java` (lignes 415-445)
- **Description** : Le handler ne met à jour que `subscriptionStatus`. Si le plan est changé via le Dashboard Stripe, la base locale ne reflète pas le changement.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Lire le price_id depuis le webhook et mapper vers le plan local.

---

### FUNC-03 — Aucune protection contre la double souscription
- **Fichier** : `SubscriptionController.java` (lignes 55-85)
- **Description** : Un utilisateur ACTIVE peut accéder à `/subscription/checkout?plan=X` et créer une nouvelle session Stripe. Le callback écrase le plan existant.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Vérifier `user.hasActiveSubscription()` au début de checkout et rediriger vers `/subscription/manage`.

---

### FUNC-04 — Page de succès PayPlug n'active PAS l'abonnement
- **Fichier** : `PaymentController.java` (lignes 95-110)
- **Description** : `/payment/success` appelle `payPlugService.verifyPayment()` mais ne modifie PAS le statut de l'utilisateur en ACTIVE. L'activation ne se fait que par webhook. Si le webhook échoue, l'utilisateur a payé mais n'a pas d'abonnement actif.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Activer l'abonnement dans le handler success aussi (avec idempotence).

---

### FUNC-05 — Les codes de vérification email sont perdus au redémarrage
- **Fichier** : `src/main/java/com/gedavocat/service/EmailVerificationService.java` (ligne 27)
- **Description** : ConcurrentHashMap en mémoire. Un redémarrage du serveur entre l'inscription et la vérification invalide tous les codes en attente.
- **Gravité** : 🟠 MINEUR
- **Solution** : Stocker les codes en base de données avec une date d'expiration.

---

### FUNC-06 — La période de facturation (yearly) est perdue après vérification email
- **Fichier** : `AuthController.java` (lignes 196-199)
- **Description** : Après vérification, la redirection est toujours `?period=monthly`. Si l'utilisateur avait choisi annuel à l'inscription, c'est perdu.
- **Gravité** : 🟠 MINEUR
- **Solution** : Stocker la période choisie dans la session ou sur l'objet User.

---

### FUNC-07 — Pas de machine à états pour les transitions de statut d'abonnement
- **Description** : Rien n'empêche de passer de INACTIVE à CANCELLED, ou de PAYMENT_FAILED à ACTIVE sans paiement réel. `activateSubscription()` set ACTIVE sans vérifier l'état courant.
- **Gravité** : 🟠 MINEUR
- **Solution** : Implémenter un state machine avec transitions autorisées.

---

### FUNC-08 — Plan invalide dans l'URL provoque une fuite du message d'erreur
- **Fichier** : `SubscriptionController.java` (ligne 192)
- **Description** : `User.SubscriptionPlan.valueOf(plan.toUpperCase())` lance `IllegalArgumentException` avec le message "No enum constant..." qui est affiché à l'utilisateur.
- **Gravité** : 🟠 MINEUR
- **Solution** : Valider le plan en amont et afficher un message générique.

---

## 3. FAILLES DE SÉCURITÉ

### SEC-01 — IDOR : tout CLIENT peut télécharger n'importe quel document
- **Fichier** : `DocumentController.java` (lignes 236-250)
- **Description** : Le check d'ownership est : `if (!isAdmin && !isClient && !document.getCaseEntity().getLawyer().getId().equals(user.getId()))`. Si l'utilisateur a `ROLE_CLIENT`, le check est entièrement bypassé via le short-circuit `!isClient`. Aucune vérification que le client est associé au dossier du document.
- **Gravité** : 🔴 CRITIQUE (mais atténué par @PreAuthorize class-level qui limite aux LAWYER/ADMIN/LAWYER_SECONDARY)
- **Note** : L'impact réel dépend de si un CLIENT peut atteindre cette route. Le `@PreAuthorize` de classe bloque normalement les CLIENT. Mais si cette vérification est modifiée ou si les SecurityConfig rules changent, c'est exploitable.
- **Solution** : Ajouter un check `client.getCases().contains(document.getCaseEntity())` pour les clients.

---

### SEC-02 — La suppression RGPD ne supprime ni les données personnelles des clients, ni les documents, ni les fichiers
- **Fichier** : `RgpdController.java` (lignes 147-174)
- **Description** : Seul l'objet User est anonymisé. Les entités Client (nom, email, téléphone), Case, Document (métadonnées), fichiers physiques sur disque, AuditLog et abonnement Stripe restent intacts.
- **Gravité** : 🔴 CRITIQUE (non-conformité RGPD)
- **Solution** : Implémenter une cascade de suppression/anonymisation complète incluant les clients, dossiers, documents et fichiers physiques. Annuler l'abonnement Stripe.

---

### SEC-03 — Le sanitizer HTML pour la signature email est bypassable (regex)
- **Fichier** : `SettingsController.java` (lignes 345-370)
- **Description** : `sanitizeHtml()` utilise des expressions régulières pour bloquer les tags dangereux (approche blocklist). Les regex sont contournables par des techniques classiques (tags imbriqués, encodage, etc.).
- **Gravité** : 🟡 MAJEUR
- **Solution** : Utiliser une bibliothèque de sanitization HTML comme OWASP Java HTML Sanitizer.

---

### SEC-04 — Les liens de partage de dossier sont accessibles sans authentification
- **Fichier** : `CaseShareController.java` (lignes 144-173)
- **Description** : `GET /cases/shared?token=xxx` est public. Aucune vérification d'identité du visiteur. Le `recipientEmail` n'est jamais comparé à l'identité réelle du visiteur. Quiconque possède l'URL peut voir le dossier et tous ses documents.
- **Gravité** : 🟡 MAJEUR (design intentionnel ? mais risque si token fuite)
- **Atténuation** : Les tokens sont des UUID cryptographiquement forts (40 hex chars). Brute-force impraticable. Mais une fuite d'URL (email forward, historique navigateur, logs proxy) expose les données.
- **Solution** : Ajouter une option de protection par mot de passe ou vérification d'email.

---

### SEC-05 — InvoiceController : vérification d'ownership incertaine pour les clients
- **Fichier** : `InvoiceController.java` (lignes 85-93)
- **Description** : `GET /api/invoices/{id}` envoie `user.getId()` comme `checkLawyerId` même pour les utilisateurs CLIENT. Le service reçoit un ID client là où il attend un ID avocat.
- **Gravité** : 🟡 MAJEUR — à vérifier dans InvoiceService
- **Solution** : Distinguer les checks selon le rôle de l'utilisateur.

---

### SEC-06 — Endpoint debug accessible aux clients en non-prod
- **Fichier** : `ClientPortalController.java` (lignes 569-599)
- **Description** : `/my-cases/api/debug-status` leake `userId`, `clientId`, nombre de dossiers. Accessible à tout CLIENT car il matche `/my-cases/**` dans SecurityConfig.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter `@Profile("dev")` ou `@PreAuthorize("hasRole('ADMIN')")`.

---

### SEC-07 — Suppression de compte zombie peut hijacker un email pré-vérification
- **Fichier** : `AuthService.java` (lignes 46-49)
- **Description** : Si A s'inscrit avec email@x.com mais ne vérifie pas, B peut s'inscrire avec le même email. L'ancien compte de A est supprimé. Si A vérifie ensuite son code (toujours en mémoire), le comportement est imprévisible.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter un délai minimum (24h) avant de supprimer les comptes non vérifiés.

---

### SEC-08 — Le lockout et le rate limiting sont en mémoire uniquement
- **Fichiers** : `AccountLockoutService.java`, `RateLimitingFilter.java`, `EmailVerificationService.java`
- **Description** : Toutes les protections anti-brute-force utilisent `ConcurrentHashMap`. Perdu au redémarrage, non partagé entre instances.
- **Gravité** : 🟠 MINEUR (impact uniquement en cas de clustering ou redémarrage fréquent)
- **Solution** : Utiliser Redis ou la base de données.

---

### SEC-09 — Pas de validation @Valid sur les rendez-vous
- **Fichier** : `AppointmentController.java` (ligne 172)
- **Description** : `POST /appointments/create` accepte un `@ModelAttribute Appointment` sans `@Valid`. Aucune validation serveur des données (titre, date, etc.).
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter `@Valid` et des contraintes sur l'entité Appointment.

---

### SEC-10 — Pas de validation sur les paramètres de signature
- **Fichier** : `SignatureController.java` (lignes 161-171)
- **Description** : `POST /signatures/create` accepte `signerEmail`, `signerFirstName`, etc. en `@RequestParam` sans aucune validation (format email, longueur, etc.).
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter un DTO avec validation `@Valid`.

---

### SEC-11 — Pas de validation sur le profil client
- **Fichier** : `ClientPortalController.java` (lignes 536-558)
- **Description** : `POST /my-cases/profile` accepte `phone`, `address`, `siret`, `birthDate` etc. sans aucune validation. `birthDate` reçu en String jamais parsé/validé.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter un DTO avec validation.

---

## 4. PROBLÈMES UX

### UX-01 — Incohérence des mots de passe acceptés selon le formulaire
- **Fichiers** : `RegisterRequest.java` vs `PasswordValidator.java`
- **Description** : La regex d'inscription accepte tout caractère non-alphanumérique comme spécial (`[^A-Za-z0-9]`). Le validateur utilisé au reset/invitation n'accepte que `@$!%*?&#+\-_`. Un mot de passe avec `(` ou `{` passe l'inscription mais sera refusé au changement de mot de passe.
- **Gravité** : 🟠 MINEUR
- **Solution** : Harmoniser les deux regex.

---

### UX-02 — Changement de mot de passe AJAX sans confirmation
- **Fichier** : `SettingsController.java` (lignes 307-337)
- **Description** : L'endpoint AJAX `/settings/change-password` n'exige pas `confirmPassword`. L'endpoint formulaire `/settings/password` l'exige. Incohérence.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter le champ `confirmPassword` à l'endpoint AJAX.

---

### UX-03 — Boutons icon-only sans `aria-label`
- **Fichier** : `layout.html` (lignes 254, 263)
- **Description** : Le bouton hamburger mobile et le bouton de notifications n'ont pas de `aria-label`. Les lecteurs d'écran ne peuvent pas les identifier.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter `aria-label="Menu"` et `aria-label="Notifications"`.

---

### UX-04 — Pas de lien "skip to content" pour la navigation au clavier
- **Fichier** : `layout.html`
- **Description** : Les utilisateurs clavier doivent naviguer à travers toute la sidebar avant d'atteindre le contenu principal.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter un lien caché `<a href="#main-content" class="skip-link">Aller au contenu</a>`.

---

## 5. PROBLÈMES PERFORMANCE

### PERF-01 — Chart.js chargé depuis CDN sur TOUTES les pages
- **Fichier** : `layout.html` (ligne 32)
- **Description** : `cdn.jsdelivr.net/npm/chart.js@4.4.0` est chargé sur chaque page via le layout, mais n'est utilisé que sur le dashboard.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Charger Chart.js uniquement dans les templates qui l'utilisent.

---

### PERF-02 — Aucun cache-control sur les fichiers statiques
- **Fichier** : `application.properties`
- **Description** : Pas de `spring.web.resources.cache` configuré. Les CSS/JS (231 KB) ne bénéficient d'aucun header de cache navigateur.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Ajouter `spring.web.resources.cache.cachecontrol.max-age=86400` (ou plus) et activer le versioning.

---

### PERF-03 — CSS/JS non minifiés
- **Description** : 231 KB de CSS et 51 KB de JS servis en texte brut non minifié.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter un plugin Maven de minification ou utiliser un CDN avec compression.

---

### PERF-04 — Risque N+1 sur plusieurs requêtes CaseRepository
- **Description** : `findByClientId`, `findByLawyerIdAndStatus`, `findAccessibleCases` n'utilisent pas `@EntityGraph` ni `JOIN FETCH`. Accéder aux documents/clients depuis ces résultats génère des requêtes N+1.
- **Gravité** : 🟠 MINEUR
- **Solution** : Ajouter `@EntityGraph(attributePaths = {"documents", "client"})` aux requêtes concernées.

---

### PERF-05 — Fichiers CSS dupliqués (24 KB gaspillés)
- **Description** : `pricing.css` (13.7 KB) et `pricing-new.css` (10.5 KB) coexistent. `payement-success.css` (faute de frappe) et `subscription-success.css` aussi.
- **Gravité** : 🟠 MINEUR
- **Solution** : Supprimer les fichiers obsolètes.

---

## 6. LIENS CASSÉS / INCOHÉRENCES DE ROUTAGE

### LINK-01 — Répertoire template `payement/` avec faute de frappe
- **Fichier** : `src/main/resources/templates/payement/pricing.html`
- **Description** : Un répertoire `payement/` (au lieu de `payment/`) existe avec un template `pricing.html`. Aucun contrôleur ne pointe vers ce template → orphelin/inaccessible.
- **Gravité** : 🟠 MINEUR
- **Solution** : Supprimer le répertoire `payement/`.

---

### LINK-02 — Liens hardcodés sans th:href dans success.html
- **Fichier** : `subscription/success.html` (lignes 33, 66)
- **Description** : `href="/clients/new"` et `href="/settings"` en dur au lieu de `th:href="@{...}"`. Fonctionnel mais cassera si l'app est déployée sous un context path.
- **Gravité** : 🟠 MINEUR
- **Solution** : Utiliser `th:href="@{/clients/new}"`.

---

### LINK-03 — Confusion /payment/manage vs /subscription/manage
- **Description** : La sidebar pointe vers `/payment/manage`. Le `SubscriptionController` sert `/subscription/manage`. Les deux rendent le même template `payment/manage`. Le formulaire d'annulation dans le template poste vers `/subscription/cancel-subscription` même quand on vient de `/payment/manage`.
- **Gravité** : 🟡 MAJEUR
- **Solution** : Unifier les routes. Supprimer le doublon PaymentController si Stripe est le seul fournisseur actif.

---

## 7. WORKFLOWS CASSÉS

### WF-01 — Workflow d'annulation PayPlug
1. Utilisateur va sur `/payment/manage`
2. Clique "Annuler mon abonnement"
3. Le formulaire POST vers `/subscription/cancel-subscription` (Stripe!)
4. Le code essaie d'annuler via Stripe (pas PayPlug) → échoue silencieusement car pas de subscription Stripe
5. Le statut local passe à CANCELLED mais PayPlug continue les prélèvements
- **Impact** : L'utilisateur pense avoir annulé mais est toujours débité

---

### WF-02 — Workflow RGPD "supprimer mon compte"
1. Utilisateur demande la suppression de compte
2. Le User est anonymisé
3. Mais : les dossiers, les données clients, les documents, les fichiers restent
4. L'abonnement Stripe n'est pas annulé → continue à être débité
5. Le RGPD exige la suppression de toutes les données personnelles
- **Impact** : Non-conformité RGPD + redevances continues

---

### WF-03 — Workflow inscription annuelle
1. Utilisateur choisit un plan annuel
2. S'inscrit et vérifie son email
3. Redirection vers checkout avec `period=monthly` (hardcodé)
4. L'utilisateur paie pour un mois au lieu d'un an
- **Impact** : Perte de revenu + confusion utilisateur

---

### WF-04 — Workflow de partage de dossier
1. Avocat partage un dossier avec un client via lien
2. Le lien est accessible à QUICONQUE possède l'URL
3. Si l'email contenant le lien est transféré → données confidentielles exposées
4. Aucun contrôle d'identité du visiteur
- **Impact** : Fuite potentielle de données juridiques confidentielles

---

## 8. CAS LIMITES PROBLÉMATIQUES

### EDGE-01 — Inscription simultanée avec le même email
Si deux utilisateurs s'inscrivent avec le même email à quelques secondes d'intervalle, le second supprime le premier. Condition de course possible.

### EDGE-02 — Changement de plan pendant que le webhook est en cours de traitement
Si l'utilisateur change de plan localement pendant qu'un webhook Stripe est traité, les deux mises à jour peuvent se chevaucher (race condition sur les champs User).

### EDGE-03 — Utilisateur supprime son navigateur cookie pendant le checkout Stripe
L'utilisateur va sur Stripe, sa session Spring expire, il revient sur la page success → ne peut pas retrouver son identité. Le webhook doit prendre le relais, mais le callback success affichera une erreur.

### EDGE-04 — Créer plus de clients que la limite du plan
La vérification du nombre de clients se fait dans `change-plan.html` côté template mais la vérification côté serveur dans le contrôleur de changement de plan doit aussi être confirmée. Un utilisateur pourrait tenter de créer des clients au-delà de sa limite via requête directe.

### EDGE-05 — Upload de fichier de 50 MB exact (limite)
La limite est 50 MB. Un fichier de 50 MB exact pourrait être rejeté selon que la comparaison est `<` ou `<=`. À vérifier.

### EDGE-06 — Caractères Unicode dans les noms de fichiers uploadés
Les noms de fichiers avec des emojis, caractères arabes, ou caractères spéciaux pourraient poser des problèmes dans le header `Content-Disposition` lors du téléchargement.

### EDGE-07 — Multiples onglets avec des formulaires différents
L'utilisateur ouvre deux onglets : un pour créer un client, un pour en modifier un autre. Le token CSRF est partagé via la session. Spring Security gère ça correctement avec les tokens synchronisés, mais si l'un des onglets soumet d'abord, le second pourrait avoir un CSRF invalide si la politique est "single-use".

---

## 9. RÉSUMÉ PRIORISÉ

### 🔴 Correctifs immédiats (à faire cette semaine)

| # | Issue | Type |
|---|-------|------|
| CRIT-01 | Page success pas idempotente — prolonge abonnement au refresh | Bug |
| CRIT-02 | MultiTenantFilter firm=null → accès cross-tenant | Sécurité |
| CRIT-04 | PayPlug cancel ne contacte pas PayPlug | Bug |
| CRIT-05 | Webhook invoice.payment_succeeded : +1 mois pour annuel + pas idempotent | Bug |
| SEC-02 | RGPD : suppression incomplète (clients, docs, fichiers, Stripe) | Conformité |

### 🟡 Correctifs importants (à faire sous 2 semaines)

| # | Issue | Type |
|---|-------|------|
| CRIT-03 | Deux systèmes de paiement parallèles | Architecture |
| FUNC-01 | Downgrade immédiat (UX dit "au renouvellement") | Bug |
| FUNC-02 | Webhook ne synchro pas les changements de plan | Bug |
| FUNC-03 | Double souscription possible | Bug |
| FUNC-04 | PayPlug success n'active pas l'abonnement | Bug |
| SEC-03 | HTML sanitizer bypassable (regex) | Sécurité |
| SEC-05 | InvoiceController ownership client incertain | Sécurité |
| LINK-03 | Confusion routes /payment/ vs /subscription/ | Architecture |
| PERF-01 | Chart.js chargé partout | Performance |
| PERF-02 | Pas de cache HTTP sur fichiers statiques | Performance |

### 🟠 Correctifs souhaitables (backlog)

| # | Issue | Type |
|---|-------|------|
| FUNC-05 | Codes vérification email en mémoire | Fiabilité |
| FUNC-06 | Période yearly perdue après vérification | Bug |
| SEC-04 | Liens partage sans authentification | Sécurité |
| SEC-06 | Endpoint debug accessible | Sécurité |
| SEC-07 | Zombie account email hijack | Sécurité |
| SEC-08 | Rate limiting en mémoire | Fiabilité |
| SEC-09/10/11 | Validation manquante (RDV, signatures, profil client) | Input |
| UX-01/02/03/04 | Incohérences UX mineures | UX |
| PERF-03/04/05 | Minification, N+1, fichiers dupliqués | Performance |
| LINK-01/02 | Templates orphelins, liens hardcodés | Cleanup |

---

**Total : 41 problèmes identifiés**
- 🔴 5 critiques
- 🟡 10 majeurs
- 🟠 26 mineurs

**Aucune faille permettant une injection SQL ou XSS directe n'a été trouvée.** Les principales préoccupations sont : l'isolation multi-tenant, l'idempotence des webhooks, la conformité RGPD, et la coexistence de deux systèmes de paiement.
