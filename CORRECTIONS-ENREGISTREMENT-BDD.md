# Corrections des problèmes d'enregistrement en base de données

## Date : 2026-02-19

## Problèmes identifiés

1. **Erreur JavaScript** : `Failed to construct 'URL': Invalid URL` dans le layout
2. **Pas d'enregistrement en BDD** pour les dossiers (Cases)
3. **Même problème pour les clients** (Clients)

## Corrections apportées

### 1. Layout.html - Erreur JavaScript ✅

**Fichier** : `src/main/resources/templates/layout.html`

**Problème** : L'utilisation de `new URL(link.href)` échouait sur les liens relatifs

**Solution** : Remplacement par `link.getAttribute('href')` avec vérification des chemins relatifs

```javascript
// AVANT (ligne 644)
const linkPath = new URL(link.href).pathname;

// APRÈS
const linkPath = link.getAttribute('href');
if (linkPath && linkPath.startsWith('/')) {
    // traitement...
}
```

---

### 2. Modèle Case - Problème avec @Data de Lombok ✅

**Fichier** : `src/main/java/com/gedavocat/model/Case.java`

**Problème** : L'annotation `@Data` génère des méthodes `equals()` et `hashCode()` qui incluent les relations bidirectionnelles, causant des boucles infinies et des problèmes avec JPA

**Solution** : Remplacement par des annotations plus spécifiques :

```java
// AVANT
@Data
@NoArgsConstructor
@AllArgsConstructor

// APRÈS
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"lawyer", "client", "documents", "permissions"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
```

---

### 3. Modèle Client - Même problème ✅

**Fichier** : `src/main/java/com/gedavocat/model/Client.java`

**Solution identique** : Remplacement de `@Data` par des annotations spécifiques :

```java
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"lawyer", "clientUser", "cases"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
```

---

### 4. CaseController - Gestion des erreurs améliorée ✅

**Fichier** : `src/main/java/com/gedavocat/controller/CaseController.java`

**Améliorations** :

1. **Validation du clientId** : Ajout de vérification manuelle avec message d'erreur
2. **Logs de débogage** : Ajout de traces pour suivre le flux d'exécution
3. **Gestion des erreurs** : Ajout de `model.addAttribute("isEdit", false/true)` dans tous les cas d'erreur
4. **Try-catch amélioré** : Affichage complet des stack traces

```java
// Validation manuelle du clientId
if (clientId == null || clientId.isEmpty()) {
    result.rejectValue("client", "error.case", "Veuillez sélectionner un client");
}

// Logs de débogage
System.out.println("=== DEBUG createCase: clientId reçu = " + clientId);
System.out.println("=== DEBUG createCase: case.name = " + caseEntity.getName());

// En cas d'erreur, retourner le modèle complet
model.addAttribute("isEdit", false);
```

---

### 5. ClientController - Corrections identiques ✅

**Fichier** : `src/main/java/com/gedavocat/controller/ClientController.java`

**Améliorations** :

1. **Logs de débogage** dans `createClient()` et `updateClient()`
2. **Gestion des erreurs** : Ajout du modèle client dans les cas d'erreur
3. **Try-catch amélioré** : Affichage des exceptions complètes

```java
// Avant (en cas d'erreur)
return "clients/form";

// Après (en cas d'erreur)
model.addAttribute("client", client);
model.addAttribute("error", e.getMessage());
return "clients/form";
```

---

### 6. CaseService - Logs et robustesse ✅

**Fichier** : `src/main/java/com/gedavocat/service/CaseService.java`

**Améliorations** :

1. **Logs détaillés** à chaque étape du processus
2. **Gestion de l'ID** : Génération conditionnelle si l'ID est null
3. **Audit non bloquant** : L'échec de l'audit ne bloque plus la création

```java
// Générer un nouvel ID si nécessaire
if (caseEntity.getId() == null || caseEntity.getId().isEmpty()) {
    caseEntity.setId(UUID.randomUUID().toString());
}

// Audit non bloquant
try {
    auditService.log(...);
} catch (Exception e) {
    System.err.println("Erreur lors de l'audit: " + e.getMessage());
    // Ne pas bloquer la création si l'audit échoue
}
```

---

### 7. ClientService - Corrections identiques ✅

**Fichier** : `src/main/java/com/gedavocat/service/ClientService.java`

**Améliorations identiques** :

1. Logs détaillés
2. Génération conditionnelle de l'ID
3. Audit non bloquant

---

### 8. Application.properties - Activation des logs SQL ✅

**Fichier** : `src/main/resources/application.properties`

**Modification** : Activation des logs SQL pour le débogage

```properties
# AVANT
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# APRÈS
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

---

## Tests à effectuer

### 1. Test création de dossier

1. Redémarrer l'application
2. Aller sur `/cases/new`
3. Remplir le formulaire (nom, client, description)
4. Cliquer sur "Créer"
5. **Vérifier dans la console** :
   - `=== DEBUG createCase: clientId reçu = ...`
   - `=== DEBUG CaseService.createCase START ===`
   - Requête SQL `INSERT INTO cases ...`
   - `=== Dossier enregistré en base de données ===`

### 2. Test modification de dossier

1. Aller sur un dossier existant
2. Cliquer sur "Modifier"
3. Changer le nom ou la description
4. Cliquer sur "Modifier"
5. **Vérifier dans la console** :
   - `=== DEBUG updateCase: ID = ...`
   - Requête SQL `UPDATE cases ...`
   - `=== DEBUG updateCase: Dossier mis à jour avec succès`

### 3. Test création de client

1. Aller sur `/clients/new`
2. Remplir le formulaire (nom, email, téléphone)
3. Cliquer sur "Enregistrer"
4. **Vérifier dans la console** :
   - `=== DEBUG createClient: client.name = ...`
   - `=== DEBUG ClientService.createClient START ===`
   - Requête SQL `INSERT INTO clients ...`
   - `=== Client enregistré en base de données ===`

### 4. Test modification de client

1. Aller sur un client existant
2. Cliquer sur "Modifier"
3. Changer le nom ou l'email
4. Cliquer sur "Enregistrer"
5. **Vérifier dans la console** :
   - `=== DEBUG updateClient: ID = ...`
   - Requête SQL `UPDATE clients ...`
   - `=== Client mis à jour en base de données ===`

---

## Diagnostic si le problème persiste

Si après ces corrections, l'enregistrement ne fonctionne toujours pas, les logs vous indiqueront exactement où ça bloque :

1. **Si pas de logs du tout** → Le formulaire ne soumet pas correctement (vérifier le HTML)
2. **Si erreur de validation** → Vérifier les annotations `@Valid` et les contraintes
3. **Si erreur "Client non trouvé"** → Problème avec le paramètre `clientId`
4. **Si pas de requête SQL** → Problème de transaction ou de configuration JPA
5. **Si requête SQL mais pas en BDD** → Problème de commit de transaction

---

## Points importants

### Pourquoi remplacer @Data ?

L'annotation `@Data` de Lombok est pratique mais dangereuse avec JPA car elle génère automatiquement :

- `equals()` et `hashCode()` incluant TOUTES les propriétés (même les relations)
- Cela crée des boucles infinies avec les relations bidirectionnelles
- Peut causer des problèmes de lazy loading

**Solution** : Utiliser uniquement l'ID dans `equals()` et `hashCode()` avec `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`

### Pourquoi les logs de débogage ?

Les logs permettent de :

1. Tracer le flux d'exécution
2. Voir les valeurs des paramètres reçus
3. Identifier l'étape exacte où ça échoue
4. Vérifier que les requêtes SQL sont bien exécutées

### Pourquoi l'audit non bloquant ?

Si le service d'audit échoue (base de données temporairement indisponible, erreur de configuration, etc.), cela ne doit PAS empêcher la création du dossier ou du client. C'est une fonctionnalité secondaire qui ne doit pas bloquer les fonctionnalités principales.

---

## Prochaines étapes

1. **Redémarrer l'application**
2. **Tester la création d'un dossier**
3. **Tester la création d'un client**
4. **Partager les logs de la console** si le problème persiste

Les logs détaillés permettront d'identifier précisément le problème restant.
