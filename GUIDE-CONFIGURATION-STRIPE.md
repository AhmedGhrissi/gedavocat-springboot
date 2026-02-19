# 🔑 Guide complet - Configuration Stripe pour GED Avocat

## 📌 Introduction

Ce guide vous explique **étape par étape** comment obtenir vos clés API Stripe pour intégrer les paiements dans votre application GED Avocat.

---

## 🚀 Étape 1 : Créer un compte Stripe (si vous n'en avez pas)

1. **Allez sur** : https://stripe.com
2. Cliquez sur **"Sign up"** (Inscription) ou **"Commencer"**
3. Remplissez le formulaire avec :
   - Votre email professionnel
   - Un mot de passe sécurisé
   - Votre nom
   - Votre pays (France)
4. **Vérifiez votre email** (vous recevrez un lien de confirmation)
5. Connectez-vous à votre compte

> ⚠️ **Note** : Vous n'avez PAS besoin de remplir les informations bancaires pour tester !

---

## 🔧 Étape 2 : Activer le mode TEST

Une fois connecté au **Dashboard Stripe** :

1. En haut à droite de l'écran, vous verrez un **toggle switch**
2. Assurez-vous que **"Mode test"** est activé (couleur bleue/violette)
3. Vous verrez l'indication **"TEST MODE"** ou **"Mode test"** dans l'interface

```
┌─────────────────────────────────────┐
│  [Stripe Logo]        [🔵 Mode test] │  ← Assurez-vous qu'il est activé
│                                      │
│  Dashboard                           │
└─────────────────────────────────────┘
```

> ✅ **Important** : En mode test, aucun vrai paiement ne sera effectué. Vous pouvez tester librement !

---

## 🔑 Étape 3 : Récupérer vos clés API

### 3.1 Accéder aux clés API

1. Dans le menu latéral gauche, cliquez sur **"Developers"** (ou "Développeurs")
2. Cliquez sur **"API keys"** (Clés API)
3. Vous arrivez sur une page avec 2 clés :

### 3.2 Récupérer la clé publiable (Publishable key)

- **Nom** : "Publishable key" ou "Clé publiable"
- **Format** : Commence par `pk_test_...`
- **Visible directement** : Cette clé est déjà affichée
- **Exemple** : `pk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890`

```
┌────────────────────────────────────────────────────┐
│ Publishable key (Clé publiable)                    │
│ pk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890     │  ← Copiez cette valeur
│ [📋 Copy]                                          │
└────────────────────────────────────────────────────┘
```

**👉 Copiez cette clé** et remplacez `pk_test_dummy_key` dans votre `application.properties`

### 3.3 Récupérer la clé secrète (Secret key)

- **Nom** : "Secret key" ou "Clé secrète"
- **Format** : Commence par `sk_test_...`
- **Cachée par défaut** : Cliquez sur **"Reveal test key"** (Révéler la clé)
- **Exemple** : `sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0987654321`

```
┌────────────────────────────────────────────────────┐
│ Secret key (Clé secrète)                           │
│ •••••••••••••••••••  [Reveal test key] ←Cliquez ici│
└────────────────────────────────────────────────────┘

Après avoir cliqué :

┌────────────────────────────────────────────────────┐
│ Secret key (Clé secrète)                           │
│ sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0987654321     │  ← Copiez cette valeur
│ [📋 Copy]                                          │
└────────────────────────────────────────────────────┘
```

**👉 Copiez cette clé** et remplacez `sk_test_dummy_key` dans votre `application.properties`

> ⚠️ **IMPORTANT** : Ne partagez JAMAIS votre clé secrète publiquement !

---

## 🔔 Étape 4 : Configurer le Webhook

Les webhooks permettent à Stripe de notifier votre application lors d'événements (paiement réussi, abonnement créé, etc.)

### 4.1 Créer un endpoint webhook

1. Toujours dans **"Developers"**, cliquez sur **"Webhooks"**
2. Cliquez sur **"Add endpoint"** ou **"Ajouter un point de terminaison"**

### 4.2 Configurer l'URL du webhook

Dans le formulaire qui s'affiche :

**Endpoint URL** : Entrez exactement cette URL
```
http://localhost:8081/subscription/webhook
```

> 📝 **Note** : Pour la production, vous devrez utiliser votre URL publique avec HTTPS

### 4.3 Sélectionner les événements à écouter

Cochez les événements suivants (ou cliquez sur "Select events") :

**Section "checkout"** :
- ✅ `checkout.session.completed`
- ✅ `checkout.session.expired`

**Section "customer"** :
- ✅ `customer.subscription.created`
- ✅ `customer.subscription.updated`
- ✅ `customer.subscription.deleted`
- ✅ `customer.subscription.trial_will_end`

**Section "invoice"** :
- ✅ `invoice.payment_succeeded`
- ✅ `invoice.payment_failed`
- ✅ `invoice.upcoming`

### 4.4 Sauvegarder et récupérer le signing secret

1. Cliquez sur **"Add endpoint"** (Ajouter)
2. Vous êtes redirigé vers la page du webhook créé
3. Cherchez la section **"Signing secret"**
4. Cliquez sur **"Reveal"** (Révéler)
5. **Copiez** la valeur qui commence par `whsec_...`

```
┌────────────────────────────────────────────────────┐
│ Signing secret                                     │
│ •••••••••••••••••••  [Reveal] ← Cliquez ici        │
└────────────────────────────────────────────────────┘

Après avoir cliqué :

┌────────────────────────────────────────────────────┐
│ Signing secret                                     │
│ whsec_1234567890abcdefghijklmnopqrstuvwxyz        │  ← Copiez cette valeur
│ [📋 Copy]                                          │
└────────────────────────────────────────────────────┘
```

**👉 Copiez cette clé** et remplacez `whsec_dummy_secret` dans votre `application.properties`

---

## 📝 Étape 5 : Mettre à jour application.properties

Ouvrez votre fichier `application.properties` et remplacez les valeurs par défaut :

```properties
# Stripe
stripe.api.key=sk_test_VOTRE_CLE_SECRETE_ICI
stripe.publishable.key=pk_test_VOTRE_CLE_PUBLIABLE_ICI
stripe.webhook.secret=whsec_VOTRE_WEBHOOK_SECRET_ICI
```

**Exemple avec de vraies clés** (ne partagez jamais les vraies !) :

```properties
# Stripe
stripe.api.key=sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0987654321
stripe.publishable.key=pk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
stripe.webhook.secret=whsec_1234567890abcdefghijklmnopqrstuvwxyz
```

---

## 🧪 Étape 6 : Tester les paiements

### Cartes de test Stripe

Stripe fournit des cartes de test pour simuler différents scénarios :

#### ✅ Paiement réussi
```
Numéro : 4242 4242 4242 4242
Date : N'importe quelle date future (ex: 12/34)
CVC : N'importe quel 3 chiffres (ex: 123)
Code postal : N'importe lequel (ex: 75001)
```

#### ❌ Paiement refusé
```
Numéro : 4000 0000 0000 0002
Date : N'importe quelle date future
CVC : N'importe quel 3 chiffres
```

#### 🔐 Authentification 3D Secure requise
```
Numéro : 4000 0027 6000 3184
Date : N'importe quelle date future
CVC : N'importe quel 3 chiffres
```

#### 💳 Autres cartes de test

Stripe fournit de nombreuses autres cartes pour tester différents scénarios :
https://stripe.com/docs/testing#cards

---

## 🔍 Étape 7 : Vérifier que tout fonctionne

### 7.1 Redémarrer votre application

```bash
# Arrêtez l'application si elle tourne
# Puis relancez-la pour charger les nouvelles configurations
mvn spring-boot:run
```

### 7.2 Tester un paiement

1. Allez sur votre page de tarification : http://localhost:8081/subscription/pricing
2. Choisissez un plan (Solo, Cabinet, Enterprise)
3. Cliquez sur "Souscrire"
4. Vous serez redirigé vers Stripe Checkout
5. Utilisez une **carte de test** (4242 4242 4242 4242)
6. Validez le paiement

### 7.3 Vérifier dans le Dashboard Stripe

1. Retournez sur https://dashboard.stripe.com
2. Assurez-vous d'être en **Mode test**
3. Allez dans **"Payments"** (Paiements)
4. Vous devriez voir votre paiement de test
5. Allez dans **"Customers"** (Clients) pour voir le client créé
6. Allez dans **"Subscriptions"** (Abonnements) pour voir l'abonnement

### 7.4 Vérifier les webhooks

1. Dans le Dashboard Stripe, allez dans **"Developers" > "Webhooks"**
2. Cliquez sur votre webhook
3. Vous verrez la liste des événements envoyés
4. Si tout fonctionne, vous verrez des événements avec un statut ✅ (réussi)

---

## 🐛 Dépannage

### Problème : "Invalid API Key provided"

**Solution** : Vérifiez que :
- Vous êtes en **mode test** dans Stripe
- Votre clé commence bien par `sk_test_` (pas `sk_live_`)
- Vous avez bien redémarré l'application après modification

### Problème : "Webhook signature verification failed"

**Solution** :
- Vérifiez que le `webhook.secret` commence par `whsec_`
- Assurez-vous d'avoir copié le bon secret (celui du webhook que vous avez créé)
- Redémarrez l'application

### Problème : Les webhooks ne sont pas reçus

**Solution** :
- Si vous testez en local, Stripe ne peut pas envoyer les webhooks à `localhost`
- **Option 1** : Utilisez Stripe CLI pour tester localement
  ```bash
  stripe listen --forward-to localhost:8081/subscription/webhook
  ```
- **Option 2** : Utilisez un tunnel comme ngrok pour exposer localhost
- **Option 3** : Testez uniquement le checkout, les webhooks fonctionneront en production

---

## 📚 Ressources utiles

- **Documentation Stripe** : https://stripe.com/docs
- **Cartes de test** : https://stripe.com/docs/testing
- **Dashboard Stripe** : https://dashboard.stripe.com
- **Stripe CLI** (pour tester les webhooks localement) : https://stripe.com/docs/stripe-cli

---

## ✅ Checklist finale

Avant de tester, vérifiez que :

- [ ] Compte Stripe créé
- [ ] Mode test activé (toggle en haut à droite)
- [ ] Clé publiable (pk_test_...) copiée dans application.properties
- [ ] Clé secrète (sk_test_...) copiée dans application.properties
- [ ] Webhook créé avec l'URL http://localhost:8081/subscription/webhook
- [ ] Événements sélectionnés (checkout.*, customer.subscription.*, invoice.payment.*)
- [ ] Webhook secret (whsec_...) copié dans application.properties
- [ ] Application redémarrée
- [ ] Test de paiement avec carte 4242 4242 4242 4242

---

## 🎉 Félicitations !

Si vous avez suivi toutes les étapes, votre intégration Stripe est maintenant configurée et prête pour les tests ! 

Vous pouvez maintenant tester les abonnements et paiements en toute sécurité en mode test, sans effectuer de vrais paiements.

---

**💡 Astuce** : Gardez ce guide à portée de main, vous en aurez besoin pour configurer les clés de production plus tard !
