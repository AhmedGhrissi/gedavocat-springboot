# 📅 Module Calendrier et Planification

## Vue d'ensemble

Le module **Calendrier et Planification** permet aux avocats de gérer efficacement leurs rendez-vous avec les clients et leurs audiences au tribunal. Il offre une interface intuitive avec vue calendrier interactive et notifications automatiques.

## ✨ Fonctionnalités principales

### 1. Gestion des rendez-vous
- **Création de rendez-vous** avec toutes les informations nécessaires
- **Types de rendez-vous** :
  - 👥 Rendez-vous client
  - ⚖️ Audience tribunal
  - 💻 Visioconférence
  - 📞 Appel téléphonique
  - 🏢 Visite sur site
  - 📋 Réunion interne
  - 📌 Autre

### 2. Vues multiples
- **Vue calendrier** : Affichage mensuel/hebdomadaire/quotidien avec FullCalendar
- **Vue liste** : Liste détaillée avec filtres avancés
- **Rendez-vous du jour** : Accès rapide aux rendez-vous d'aujourd'hui
- **Rendez-vous à venir** : Planning des prochains rendez-vous

### 3. Fonctionnalités avancées
- ✅ **Gestion des statuts** : Planifié, Confirmé, En cours, Terminé, Annulé, Reporté
- 🔔 **Rappels automatiques** : Notifications par email avant les rendez-vous
- 🏛️ **Informations tribunal** : Nom du tribunal, salle d'audience, nom du juge
- 🔗 **Liens visioconférence** : Intégration pour les rendez-vous en ligne
- 📝 **Notes privées** : Notes personnelles non visibles par le client
- 🎨 **Codage couleur** : Personnalisation de l'affichage dans le calendrier
- ⚠️ **Détection de conflits** : Alerte en cas de chevauchement d'horaires

### 4. Intégrations
- 📁 **Liaison avec dossiers** : Association des rendez-vous aux dossiers clients
- 👤 **Liaison avec clients** : Référence automatique au client concerné
- 📊 **Statistiques** : Compteurs et indicateurs de performance

## 🚀 Utilisation

### Accès au module
Cliquez sur **"📅 Calendrier"** dans le menu latéral gauche.

### Créer un rendez-vous

1. Cliquez sur **"+ Nouveau rendez-vous"**
2. Remplissez les informations :
   - Type de rendez-vous
   - Titre (ex: "Consultation divorce")
   - Date et heure de début/fin
   - Client associé (optionnel)
   - Dossier associé (optionnel)
   - Lieu du rendez-vous

3. Pour une **audience au tribunal**, renseignez :
   - Nom du tribunal
   - Salle d'audience
   - Nom du juge

4. Configurez les **rappels** :
   - Activer/désactiver les rappels
   - Délai avant le rendez-vous (en minutes)

5. Cliquez sur **"Enregistrer"**

### Modifier un rendez-vous

1. Cliquez sur le rendez-vous dans le calendrier ou la liste
2. Modifiez les informations souhaitées
3. Actions disponibles :
   - ✅ Confirmer
   - ⏸️ Annuler
   - ✔️ Marquer comme terminé
   - 🗑️ Supprimer

### Filtrer les rendez-vous

En **vue liste**, utilisez les filtres :
- 📋 **Tous** : Tous les rendez-vous
- 📅 **Aujourd'hui** : Rendez-vous du jour
- 🔜 **À venir** : Rendez-vous futurs
- ⚖️ **Audiences tribunal** : Uniquement les audiences

## 🔔 Rappels automatiques

### Configuration
- Les rappels sont activés par défaut (1 heure avant)
- Personnalisable pour chaque rendez-vous
- Envoi automatique par email

### Processus
1. Le système vérifie toutes les 15 minutes
2. Identifie les rendez-vous dans la fenêtre de rappel
3. Envoie un email à l'avocat
4. Envoie un email au client (si associé)
5. Marque le rappel comme envoyé

### Format du rappel
```
Rappel de votre rendez-vous:

📅 Consultation divorce
🕒 20/02/2026 à 10:00
📋 Type: Rendez-vous client
👤 Client: Marie Dupont
📍 Lieu: Cabinet - Bureau 2

Description:
Premier rendez-vous pour discuter de la procédure
```

## 🗄️ Structure de la base de données

### Table `appointments`

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | VARCHAR(36) | Identifiant unique |
| `title` | VARCHAR(200) | Titre du rendez-vous |
| `description` | TEXT | Description détaillée |
| `appointment_date` | DATETIME | Date et heure de début |
| `end_date` | DATETIME | Date et heure de fin |
| `type` | ENUM | Type de rendez-vous |
| `status` | ENUM | Statut actuel |
| `lawyer_id` | VARCHAR(36) | ID de l'avocat |
| `client_id` | VARCHAR(36) | ID du client (optionnel) |
| `case_id` | VARCHAR(36) | ID du dossier (optionnel) |
| `location` | VARCHAR(200) | Lieu du rendez-vous |
| `court_name` | VARCHAR(200) | Nom du tribunal |
| `court_room` | VARCHAR(50) | Salle d'audience |
| `judge_name` | VARCHAR(100) | Nom du juge |
| `send_reminder` | BOOLEAN | Activer les rappels |
| `reminder_sent` | BOOLEAN | Rappel envoyé |
| `reminder_minutes_before` | INT | Minutes avant rappel |
| `notes` | TEXT | Notes privées |
| `video_conference_link` | VARCHAR(500) | Lien visio |
| `color` | VARCHAR(7) | Couleur d'affichage |

### Index optimisés
- Index sur `lawyer_id` pour recherches rapides
- Index sur `appointment_date` pour tri chronologique
- Index sur `status` pour filtres
- Index composite pour les rappels

## 📊 API REST

### Endpoints disponibles

```
GET    /appointments              Vue calendrier
GET    /appointments/list         Vue liste
GET    /appointments/new          Formulaire création
POST   /appointments/create       Créer un rendez-vous
GET    /appointments/{id}/edit    Formulaire édition
POST   /appointments/{id}/update  Mettre à jour
POST   /appointments/{id}/delete  Supprimer
POST   /appointments/{id}/confirm Confirmer
POST   /appointments/{id}/cancel  Annuler
POST   /appointments/{id}/complete Marquer terminé

GET    /appointments/api/events   JSON pour FullCalendar
```

## 🎨 Technologies utilisées

- **Backend** : Spring Boot, JPA/Hibernate
- **Frontend** : Thymeleaf, Bootstrap 5
- **Calendrier** : FullCalendar 6.1.10
- **Base de données** : MySQL/MariaDB
- **Scheduling** : Spring @Scheduled

## 🔧 Configuration

### Activer les tâches planifiées

Dans votre classe principale, ajoutez :

```java
@EnableScheduling
@SpringBootApplication
public class GedAvocatApplication {
    // ...
}
```

### Configuration des rappels

Dans `application.properties` :

```properties
# Intervalle de vérification des rappels (ms)
appointment.reminder.check.interval=900000

# Fenêtre de rappel (heures)
appointment.reminder.window.hours=2
```

## 📈 Statistiques disponibles

- Nombre de rendez-vous planifiés
- Nombre de rendez-vous confirmés
- Nombre de rendez-vous terminés
- Nombre de rendez-vous annulés
- Taux de présence
- Audiences à venir

## 🔮 Évolutions futures

- [ ] Synchronisation avec Google Calendar / Outlook
- [ ] Export iCalendar (.ics)
- [ ] Rappels SMS (via Twilio)
- [ ] Rappels push (notifications navigateur)
- [ ] Salle d'attente virtuelle
- [ ] Confirmation de présence par le client
- [ ] Rapports d'activité mensuels
- [ ] Suggestions de créneaux disponibles
- [ ] Gestion des récurrences
- [ ] Zone tampon entre rendez-vous

## 🐛 Dépannage

### Les rappels ne sont pas envoyés

1. Vérifiez que `@EnableScheduling` est activé
2. Vérifiez les logs : `tail -f logs/application.log | grep Reminder`
3. Vérifiez la configuration email

### Conflit d'horaire non détecté

- La détection se fait dans une fenêtre de ±30 minutes
- Vérifiez que les rendez-vous ne sont pas annulés
- Consultez les logs pour les erreurs

### Calendrier ne s'affiche pas

1. Vérifiez la console navigateur (F12)
2. Vérifiez que FullCalendar CSS/JS sont chargés
3. Vérifiez l'endpoint `/appointments/api/events`

## 📝 Notes importantes

- Les rendez-vous sont liés à l'avocat créateur
- La suppression d'un avocat supprime ses rendez-vous (CASCADE)
- La suppression d'un client/dossier conserve le rendez-vous (SET NULL)
- Les rappels sont envoyés une seule fois
- Les audiences terminées sont archivées automatiquement

## 🤝 Support

Pour toute question ou problème :
- Consultez les logs dans `logs/application.log`
- Vérifiez la table `appointments` dans la base de données
- Contactez l'équipe de support technique

---

**Version** : 1.0.0  
**Dernière mise à jour** : Février 2026  
**Auteur** : GED Avocat Team
