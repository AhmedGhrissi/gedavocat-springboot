# ===================================================================
# Guide de démarrage en mode LOCAL
# ===================================================================

## 📋 Prérequis

1. **MySQL** installé et démarré sur `localhost:3306`
   - Username: `root`
   - Password: `root`
   - La base `gedavocat` sera créée automatiquement

2. **Java 17+** installé
3. **Maven** (ou utiliser le wrapper `mvnw.cmd` inclus)

## 🚀 Démarrage rapide

### Option 1: Script automatique (recommandé)
```bash
start-local.bat
```

### Option 2: Ligne de commande
```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### Option 3: Depuis votre IDE (Eclipse/IntelliJ)
1. Ouvrez les configurations de lancement
2. Ajoutez une variable d'environnement : `SPRING_PROFILES_ACTIVE=local`
3. Lancez l'application

## 🔧 Configuration locale

Le fichier `application-local.properties` active automatiquement :

✅ **Logs en mode DEBUG** - Tous les détails des requêtes SQL et HTTP
✅ **Stacktraces complets** - Pour déboguer facilement les erreurs
✅ **Cache Thymeleaf désactivé** - Modifications HTML visibles immédiatement
✅ **Session cookie non-secure** - Pour travailler en HTTP local
✅ **Actuator complet** - Tous les endpoints de monitoring activés
✅ **Email Ethereal** - Emails de test (voir les emails sur https://ethereal.email)

## 🌐 Accès à l'application

Une fois démarrée, l'application est accessible sur :
- **URL principale** : http://localhost:8081
- **Health check** : http://localhost:8081/actuator/health
- **Tous les endpoints actuator** : http://localhost:8081/actuator

## 👤 Comptes de test

Pour tester le portail client, vous aurez besoin de :

1. **Un compte avocat** (pour créer des dossiers)
2. **Un compte client** lié à un utilisateur

### Créer un compte de test

Vous pouvez soit :
- Utiliser l'interface d'inscription
- Insérer directement dans la base de données
- Utiliser les données du dump SQL si disponible

## 🐛 Vérification des corrections

Pour vérifier que les corrections du portail client fonctionnent :

1. Connectez-vous avec un compte CLIENT
2. Accédez à **Mes dossiers** via l'URL : http://localhost:8081/my-cases
3. Vérifiez que les dossiers s'affichent correctement
4. Cliquez sur "Voir" pour accéder au détail d'un dossier
5. Vérifiez que les liens fonctionnent correctement

## 📊 Vérification de la base de données

```sql
-- Vérifier les clients et leurs utilisateurs liés
SELECT 
    c.id, 
    c.name, 
    c.email, 
    c.client_user_id,
    u.email as user_email
FROM clients c
LEFT JOIN users u ON c.client_user_id = u.id;

-- Vérifier les dossiers d'un client
SELECT 
    ca.id,
    ca.name,
    ca.reference,
    ca.status,
    cl.name as client_name
FROM cases ca
JOIN clients cl ON ca.client_id = cl.id
WHERE cl.id = 'CLIENT_ID_ICI';
```

## 🔍 Debug des problèmes courants

### Problème : Les dossiers ne s'affichent pas

**Vérifiez :**
1. Le client a bien un `client_user_id` défini
2. Des dossiers existent pour ce client dans la table `cases`
3. Les logs montrent bien la requête SQL

**Logs à surveiller :**
```
Client portal: user=xxx clientId=xxx returned X cases
```

### Problème : Erreur 404 sur /my-cases

**Solution :**
- Les corrections ont déjà été appliquées dans `cases.html`
- Vérifiez que vous utilisez bien la version corrigée

### Problème : Erreur de connexion MySQL

**Solution :**
```bash
# Vérifier que MySQL tourne
mysql -u root -proot -e "SELECT 1"

# Créer la base manuellement si nécessaire
mysql -u root -proot -e "CREATE DATABASE gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
```

## 📝 Différences local vs production

| Paramètre | Local | Production |
|-----------|-------|------------|
| Logs | DEBUG | WARN/INFO |
| Stacktraces | Complets | Masqués |
| Cache Thymeleaf | Désactivé | Activé |
| Cookie secure | false | true |
| SQL visible | Oui | Non |
| Actuator | Tous endpoints | Health seulement |

## 🔄 Rechargement à chaud

En mode local, vous pouvez :
- Modifier les templates HTML → Rechargement automatique
- Modifier les fichiers Java → Redémarrage nécessaire (ou utiliser Spring DevTools)
- Modifier les CSS/JS statiques → Rafraîchir le navigateur

## 📧 Tests des emails

Les emails sont envoyés vers Ethereal Email (compte de test) :
- **Accès** : https://ethereal.email/messages
- **Login** : ashly.murazik19@ethereal.email
- **Password** : 4KyQ2nJhg98wrhsxJE

Tous les emails envoyés par l'application sont visibles dans cette boîte.

## 🛑 Arrêt de l'application

- Appuyez sur `Ctrl+C` dans le terminal
- Ou fermez la fenêtre du script

## 💡 Astuces

1. **Garder les logs ouverts** pour voir en temps réel ce qui se passe
2. **Utiliser l'inspecteur réseau** du navigateur pour déboguer les requêtes
3. **Vérifier les logs SQL** pour comprendre les requêtes exécutées
4. **Tester avec plusieurs navigateurs** pour éviter les problèmes de cache

## 📞 Support

Si vous rencontrez des problèmes :
1. Vérifiez les logs dans le terminal
2. Consultez les logs SQL pour voir les requêtes
3. Vérifiez la base de données directement
4. Activez le mode TRACE si nécessaire
