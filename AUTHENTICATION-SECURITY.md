# Authentification et Contrôle d'accès par rôle

## 📋 Vue d'ensemble

L'application **GED Avocat** implémente un système d'authentification robuste avec **séparation stricte des accès** selon les rôles utilisateurs.

## 👥 Rôles utilisateurs

### 1. **LAWYER (Avocat)**
- **Accès complet** à tous les dossiers qu'il gère
- Peut créer, modifier, supprimer des dossiers et clients
- Peut uploader et gérer des documents
- Accès aux signatures électroniques
- Accès au tableau de bord complet

**Routes accessibles :**
- `/dashboard` - Tableau de bord
- `/clients/**` - Gestion des clients
- `/cases/**` - Gestion des dossiers
- `/documents/**` - Gestion des documents
- `/signatures/**` - Signatures électroniques
- `/rpva/**` - Intégration RPVA
- `/permissions/**` - Gestion des permissions

### 2. **CLIENT**
- **Accès UNIQUEMENT à ses propres dossiers**
- Consultation en lecture seule de ses dossiers
- Téléchargement de ses documents
- Aucun accès aux fonctionnalités de gestion

**Routes accessibles :**
- `/my-cases` - Liste de SES dossiers uniquement
- `/my-cases/{id}` - Détail d'UN de ses dossiers (avec vérification de propriété)
- `/my-cases/{id}/documents` - Documents de SES dossiers
- `/documents/{id}/download` - Téléchargement de documents (avec vérification)

### 3. **LAWYER_SECONDARY (Avocat secondaire)**
- Collaborateur d'un cabinet
- Accès aux dossiers selon les permissions accordées
- Pas de gestion des clients ou abonnements

### 4. **ADMIN**
- Accès total à toutes les fonctionnalités
- Gestion des utilisateurs
- Accès aux logs d'audit
- Configuration système

## 🔒 Sécurité implémentée

### 1. **Au niveau Spring Security** (`SecurityConfig.java`)
```java
// Pages avocats - INTERDITES aux clients
.requestMatchers("/cases/**", "/documents/**", "/clients/**")
    .hasAnyRole("LAWYER", "ADMIN", "LAWYER_SECONDARY")

// Pages clients - Accès restreint
.requestMatchers("/my-cases/**")
    .hasAnyRole("CLIENT", "LAWYER", "ADMIN")
```

### 2. **Au niveau contrôleur** (`@PreAuthorize`)
```java
// CaseController - Réservé aux avocats
@PreAuthorize("hasAnyRole('LAWYER', 'ADMIN', 'LAWYER_SECONDARY')")

// ClientPortalController - Réservé aux clients
@PreAuthorize("hasRole('CLIENT')")
```

### 3. **Au niveau métier** (Vérification de propriété)
Dans `ClientPortalController`, chaque action vérifie que :
```java
// Vérifier que le dossier appartient bien au client connecté
if (!caseEntity.getClient().getId().equals(client.getId())) {
    throw new RuntimeException("Accès non autorisé à ce dossier");
}
```

## 🚀 Redirection automatique à la connexion

Lors de la connexion, l'utilisateur est redirigé selon son rôle :

- **CLIENT** → `/my-cases` (ses dossiers)
- **LAWYER/ADMIN** → `/dashboard` (tableau de bord complet)

## 🔐 Protection des données

### Pour les CLIENTS :
1. ✅ Ne peuvent voir QUE leurs propres dossiers
2. ✅ Impossible d'accéder aux dossiers d'autres clients
3. ✅ Impossible d'accéder aux sections de gestion (clients, création de dossiers)
4. ✅ Téléchargement de documents avec vérification de propriété
5. ✅ Aucune modification possible (lecture seule)

### Pour les AVOCATS :
1. ✅ Accès à tous LEURS dossiers et clients
2. ✅ Impossible d'accéder aux dossiers d'autres avocats (sauf si permissions)
3. ✅ Gestion complète de leurs dossiers
4. ✅ Upload et suppression de documents

## 📊 Architecture de séparation

```
┌─────────────────────────────────────────┐
│         AUTHENTIFICATION                │
│      (AuthController + AuthService)     │
└──────────────┬──────────────────────────┘
               │
               ├─── ROLE: CLIENT
               │    │
               │    └─→ ClientPortalController
               │        └─→ /my-cases/**
               │            ├─ Vérification userId → clientId
               │            ├─ Vérification propriété dossier
               │            └─ Lecture seule
               │
               └─── ROLE: LAWYER/ADMIN
                    │
                    ├─→ CaseController (/cases/**)
                    ├─→ DocumentController (/documents/**)
                    ├─→ ClientController (/clients/**)
                    └─→ Accès complet gestion
```

## 🛡️ Points de contrôle de sécurité

| Niveau | Contrôle | Méthode |
|--------|----------|---------|
| 1. Réseau | Configuration Spring Security | `SecurityConfig` |
| 2. Contrôleur | Annotations `@PreAuthorize` | Contrôleurs |
| 3. Métier | Vérification de propriété | Services |
| 4. Base de données | Relations et contraintes | Modèles JPA |

## ✅ Tests de sécurité recommandés

1. **Client essaie d'accéder à `/cases`** → Erreur 403 Forbidden
2. **Client essaie d'accéder au dossier d'un autre client** → Erreur 403
3. **Client essaie de télécharger un document qui ne lui appartient pas** → Erreur 403
4. **Avocat essaie d'accéder au dossier d'un autre avocat** → Erreur 403
5. **Client connecté redirigé vers** → `/my-cases`
6. **Avocat connecté redirigé vers** → `/dashboard`

## 🔄 Flux d'authentification

```
1. Utilisateur se connecte (email + password)
   ↓
2. AuthService vérifie les credentials
   ↓
3. Spring Security charge le User avec son ROLE
   ↓
4. Redirection selon le rôle :
   - CLIENT → /my-cases
   - LAWYER → /dashboard
   ↓
5. Chaque requête est vérifiée par :
   - Spring Security (autorisations de base)
   - @PreAuthorize (contrôle au niveau contrôleur)
   - Service métier (vérification de propriété)
```

## 📝 Création d'un compte client

Pour qu'un client ait accès à son portail :

1. L'avocat crée un **Client** dans `/clients/new`
2. L'avocat peut créer un **compte utilisateur** pour ce client
3. Le client reçoit ses identifiants
4. Le client se connecte et est redirigé vers `/my-cases`
5. Le client voit UNIQUEMENT les dossiers où il est défini comme client

## 🔗 Relation Client ↔ User

```java
// Modèle Client
@ManyToOne
@JoinColumn(name = "client_user_id")
private User clientUser;  // Compte utilisateur lié (optionnel)
```

Cette relation permet de faire le lien entre :
- Un **User** avec `role = CLIENT`
- Une entité **Client** (dans la base des clients de l'avocat)

## 🎯 Résumé

✅ **Séparation stricte** : Avocats et clients ont des contrôleurs différents
✅ **Vérification multiniveau** : Sécurité au niveau réseau, contrôleur et métier
✅ **Protection des données** : Un client ne peut JAMAIS voir les dossiers d'un autre
✅ **Redirection intelligente** : Chaque rôle arrive sur sa page appropriée
✅ **Lecture seule pour clients** : Aucune modification possible via le portail client
