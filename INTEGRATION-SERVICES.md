# 🔌 GUIDE D'INTÉGRATION DES SERVICES EXTERNES

## Vue d'ensemble

GED Avocat intègre 3 services essentiels pour les avocats français :

1. **Yousign** - Signature électronique française (gratuite jusqu'à 5 signatures/mois)
2. **RPVA (e-Barreau)** - Communication électronique avec les juridictions
3. **Stripe** - Plateforme de paiement pour les abonnements

---

## 📝 1. YOUSIGN - Signature Électronique

### Pourquoi Yousign ?
- ✅ **Solution 100% française**
- ✅ **Gratuite** jusqu'à 5 signatures par mois
- ✅ **Conforme eIDAS** (Règlement européen)
- ✅ **Certificat qualifié** disponible
- ✅ **Signature par SMS** (OTP)

### Configuration

#### Étape 1 : Créer un compte Yousign

1. Allez sur https://yousign.com
2. Créez un compte **gratuit**
3. Vérifiez votre email
4. Accédez à votre dashboard

#### Étape 2 : Obtenir la clé API

1. Dans le dashboard Yousign, allez dans **Paramètres**
2. Cliquez sur **API Keys**
3. Créez une nouvelle clé API
4. Copiez la clé (format : `ys_xxxxxxxxxxxxx`)

#### Étape 3 : Configurer GED Avocat

Dans `application.properties` :

```properties
# Yousign Configuration
yousign.api.key=ys_votre_cle_api_ici
yousign.api.url=https://api.yousign.com
yousign.api.version=v3
```

### Utilisation

#### Créer une demande de signature

```java
@Autowired
private YousignService yousignService;

// Créer une signature
Map<String, Object> result = yousignService.createSignatureRequest(
    "/path/to/document.pdf",     // Chemin du document
    "Jean Dupont",                // Nom du signataire
    "jean.dupont@email.com",      // Email du signataire
    "advanced"                    // Niveau: simple, advanced, qualified
);

String signatureId = (String) result.get("id");
```

#### Vérifier le statut

```java
Map<String, Object> status = yousignService.getSignatureStatus(signatureId);
String state = (String) status.get("status"); // ongoing, completed, cancelled
```

#### Télécharger le document signé

```java
byte[] signedDocument = yousignService.downloadSignedDocument(signatureId);
// Sauvegarder le document
```

### Niveaux de signature

| Niveau | Description | Usage |
|--------|-------------|-------|
| `simple` | Signature électronique simple | Documents internes |
| `advanced` | Signature électronique avancée | Contrats, mandats |
| `qualified` | Signature qualifiée eIDAS | Actes authentiques |

### Tarification Yousign

- **Gratuit** : 5 signatures/mois
- **Solo** : 25€/mois - 50 signatures
- **Business** : 100€/mois - 200 signatures
- **Sur mesure** : Contactez Yousign

---

## ⚖️ 2. RPVA (e-Barreau)

### Qu'est-ce que le RPVA ?

Le **Réseau Privé Virtuel des Avocats** (anciennement e-Barreau) permet :
- 📤 Communiquer électroniquement avec les juridictions
- 📥 Recevoir des communications des tribunaux
- 📋 Déposer des actes et pièces
- 🔔 Recevoir des notifications

### Configuration

#### Prérequis

1. **Être avocat inscrit** à un Barreau français
2. **Certificat électronique** délivré par le CNB (Conseil National des Barreaux)
3. **Compte e-Barreau** activé

#### Étape 1 : Obtenir le certificat électronique

1. Contactez votre Ordre des Avocats
2. Demandez un certificat RGS** (Référentiel Général de Sécurité)
3. Téléchargez et installez le certificat

#### Étape 2 : Activer e-Barreau

1. Connectez-vous à https://www.e-barreau.fr
2. Activez votre compte avec votre certificat
3. Configurez vos notifications

#### Étape 3 : Configurer GED Avocat

```properties
# RPVA Configuration
rpva.api.url=https://api.e-barreau.fr
rpva.api.key=VOTRE_CLE_API_E_BARREAU
rpva.certificate.path=/path/to/certificate.p12
rpva.certificate.password=votre_mot_de_passe
```

### Utilisation

#### Envoyer une communication

```java
@Autowired
private RPVAService rpvaService;

// Envoyer une communication
Map<String, Object> result = rpvaService.sendCommunication(
    "TJ-PARIS",                    // Code juridiction
    "RG 23/12345",                 // Référence dossier
    "Dépôt de conclusions",        // Objet
    "Veuillez trouver...",         // Contenu
    new String[]{"/docs/piece1.pdf"}  // Pièces jointes
);
```

#### Recevoir les communications

```java
// Récupérer les communications reçues
Map<String, Object> communications = rpvaService.getReceivedCommunications(
    LocalDateTime.now().minusDays(7),  // Depuis 7 jours
    LocalDateTime.now(),                 // Jusqu'à aujourd'hui
    "unread"                            // Statut: unread, read, all
);
```

#### Télécharger un accusé de réception

```java
byte[] receipt = rpvaService.downloadReceipt(communicationId);
```

### Codes juridictions (exemples)

| Code | Juridiction |
|------|-------------|
| TJ-PARIS | Tribunal Judiciaire de Paris |
| TJ-LYON | Tribunal Judiciaire de Lyon |
| CA-PARIS | Cour d'Appel de Paris |
| CC-PARIS | Cour de Cassation |

### Sécurité RPVA

- 🔐 **Certificat électronique obligatoire**
- 🔒 **Connexion chiffrée TLS**
- ✍️ **Signature électronique** de chaque envoi
- 📋 **Accusé de réception** systématique

---

## 💳 3. STRIPE - Paiements et Abonnements

### Pourquoi Stripe ?

- ✅ **Compatible France** (SEPA, CB française)
- ✅ **PCI-DSS** compliant (sécurité cartes)
- ✅ **Gestion d'abonnements** automatique
- ✅ **Facturation automatisée**
- ✅ **TVA française** gérée

### Configuration

#### Étape 1 : Créer un compte Stripe

1. Allez sur https://stripe.com/fr
2. Créez un compte **gratuit**
3. Activez votre compte (vérification identité)

#### Étape 2 : Obtenir les clés API

1. Dans le dashboard Stripe, allez dans **Développeurs** → **Clés API**
2. Notez :
   - **Clé publique** (commence par `pk_test_` ou `pk_live_`)
   - **Clé secrète** (commence par `sk_test_` ou `sk_live_`)

#### Étape 3 : Configurer les webhooks

1. Dans **Développeurs** → **Webhooks**
2. Ajoutez un endpoint : `https://votre-domaine.com/subscription/webhook`
3. Sélectionnez les événements :
   - `checkout.session.completed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `invoice.payment_succeeded`
   - `invoice.payment_failed`
4. Copiez le **Secret de signature** (commence par `whsec_`)

#### Étape 4 : Configurer GED Avocat

```properties
# Stripe Configuration
stripe.api.key=sk_test_VOTRE_CLE_SECRETE
stripe.publishable.key=pk_test_VOTRE_CLE_PUBLIQUE
stripe.webhook.secret=whsec_VOTRE_SECRET_WEBHOOK
```

### Plans d'abonnement

#### Plan Solo - 29,99€/mois
```properties
app.subscription.solo.price=29.99
```
- 10 clients maximum
- 10 GB stockage
- 5 signatures Yousign/mois
- Support email

#### Plan Cabinet - 99,99€/mois
```properties
app.subscription.cabinet.price=99.99
```
- 100 clients
- 100 GB stockage
- Multi-utilisateurs (5)
- Signatures illimitées
- Support prioritaire

#### Plan Enterprise - 299,99€/mois
```properties
app.subscription.enterprise.price=299.99
```
- Clients illimités
- 1 TB stockage
- Utilisateurs illimités
- API complète
- Support 24/7

### Utilisation

#### Créer une session de paiement

L'utilisateur clique sur "Choisir un plan" → redirigé vers Stripe Checkout

```java
@Autowired
private StripePaymentService stripeService;

// Créer une session Checkout
Map<String, Object> session = stripeService.createCheckoutSession(
    userId,
    "cabinet",  // Plan: solo, cabinet, enterprise
    "https://monsite.com/success",
    "https://monsite.com/cancel"
);

// Rediriger vers Stripe
return "redirect:" + session.get("url");
```

#### Gérer le portail client

Permettre à l'utilisateur de gérer son abonnement :

```java
Map<String, Object> portal = stripeService.createCustomerPortal(
    userId,
    "https://monsite.com/dashboard"
);

return "redirect:" + portal.get("url");
```

### Webhooks

Les webhooks Stripe notifient automatiquement l'application :

1. **Paiement réussi** → Activer l'abonnement
2. **Paiement échoué** → Notifier l'utilisateur
3. **Abonnement annulé** → Désactiver l'accès
4. **Abonnement renouvelé** → Prolonger l'accès

### Sécurité des paiements

- 🔐 **3D Secure** obligatoire (authentification forte)
- 💳 **Aucune carte stockée** sur vos serveurs
- 🔒 **PCI-DSS Level 1** (certification maximale)
- 📊 **Détection de fraude** automatique

### Tests

Cartes de test Stripe :

| Carte | Résultat |
|-------|----------|
| 4242 4242 4242 4242 | Paiement réussi |
| 4000 0000 0000 9995 | Paiement refusé |
| 4000 0025 0000 3155 | 3D Secure requis |

---

## 🔗 Intégration complète

### Workflow d'inscription

1. **Utilisateur s'inscrit** → Compte créé (gratuit)
2. **Choisit un plan** → Redirigé vers Stripe
3. **Paye avec Stripe** → Webhook reçu
4. **Abonnement activé** → Accès complet
5. **Signature Yousign** → Jusqu'à 5/mois (Solo) ou illimité (Cabinet/Enterprise)
6. **Connexion RPVA** → Si certificat configuré

### Synergies

- **Yousign + RPVA** : Signer et envoyer au tribunal
- **Stripe + Yousign** : Plus de signatures selon le plan
- **RPVA + GED** : Archivage automatique des communications

---

## ⚙️ Configuration complète

Fichier `application.properties` complet :

```properties
# Yousign
yousign.api.key=ys_VOTRE_CLE
yousign.api.url=https://api.yousign.com
yousign.api.version=v3

# RPVA
rpva.api.url=https://api.e-barreau.fr
rpva.api.key=VOTRE_CLE_RPVA
rpva.certificate.path=/path/to/cert.p12
rpva.certificate.password=PASSWORD

# Stripe
stripe.api.key=sk_live_VOTRE_CLE
stripe.publishable.key=pk_live_VOTRE_CLE
stripe.webhook.secret=whsec_VOTRE_SECRET

# Prix
app.subscription.solo.price=29.99
app.subscription.cabinet.price=99.99
app.subscription.enterprise.price=299.99
```

---

## 📊 Récapitulatif des coûts

### Services gratuits
- **Yousign** : 5 signatures/mois gratuites
- **Stripe** : Pas de frais fixes, seulement % transaction

### Coûts variables
- **Stripe** : 1,4% + 0,25€ par transaction (cartes EU)
- **SEPA** : 0,4% (pas de minimum)
- **Yousign** : Au-delà de 5 signatures, 25€/mois

### ROI pour vous

| Plan | Prix/mois | Commission Stripe | Gain net |
|------|-----------|-------------------|----------|
| Solo | 29,99€ | ~0,70€ | ~29€ |
| Cabinet | 99,99€ | ~1,70€ | ~98€ |
| Enterprise | 299,99€ | ~4,50€ | ~295€ |

---

## ✅ Checklist d'activation

### Yousign
- [ ] Compte créé sur yousign.com
- [ ] Clé API obtenue
- [ ] Clé configurée dans `application.properties`
- [ ] Test de signature effectué

### RPVA
- [ ] Certificat électronique obtenu
- [ ] Compte e-Barreau activé
- [ ] Certificat configuré dans l'application
- [ ] Test de communication effectué

### Stripe
- [ ] Compte Stripe créé
- [ ] Identité vérifiée
- [ ] Clés API obtenues
- [ ] Webhook configuré
- [ ] Test de paiement effectué
- [ ] Plans tarifaires configurés

---

## 🆘 Support

### Yousign
- 📧 support@yousign.com
- 📚 https://developers.yousign.com

### RPVA (e-Barreau)
- 📧 support@e-barreau.fr
- 📞 01 44 32 48 48

### Stripe
- 📧 support@stripe.com
- 📚 https://stripe.com/docs
- 💬 Chat en direct (dashboard)

---

**Toutes les intégrations sont maintenant configurées ! 🎉**
