# 🔧 Correctifs Prioritaires à Appliquer

## 🔐 SÉCURITÉ - CRITIQUE (À faire MAINTENANT)

### 1. Ajouter IDs et Labels Accessibles au Formulaire

**Fichier:** `calendar.html` - Modal "Nouveau Rendez-vous"

Remplacer les champs sans ID par ceux-ci :

```html
<!-- Type -->
<label for="modalType" class="form-label">
    Type <span class="text-danger" aria-label="requis">*</span>
</label>
<select name="type" id="modalType" class="form-select" 
        required aria-required="true">
    <!-- ...options... -->
</select>

<!-- Titre -->
<label for="modalTitle" class="form-label">
    Titre <span class="text-danger" aria-label="requis">*</span>
</label>
<input type="text" name="title" id="modalTitle" class="form-control" 
       required aria-required="true"
       minlength="3" maxlength="200"
       placeholder="Ex: Rendez-vous avec M. Dupont">

<!-- Dates avec validation -->
<label for="modalStartDate" class="form-label">
    Début <span class="text-danger" aria-label="requis">*</span>
</label>
<input type="datetime-local" name="startDate" id="modalStartDate" 
       class="form-control" required aria-required="true"
       min="2026-01-01T00:00" max="2030-12-31T23:59">

<!-- Lieu avec limite de caractères -->
<label for="modalLocation" class="form-label">Lieu</label>
<input type="text" name="location" id="modalLocation" 
       class="form-control" maxlength="200"
       placeholder="Ex: Cabinet, Palais de justice...">

<!-- URL visio avec validation -->
<label for="modalVideoLink" class="form-label">Lien visio</label>
<input type="url" name="videoConferenceLink" id="modalVideoLink" 
       class="form-control" 
       pattern="https?://.+"
       placeholder="https://meet.google.com/...">
```

---

### 2. Améliorer Boutons avec ARIA Labels

```html
<!-- Bouton éditer -->
<a th:href="@{/appointments/{id}/edit(id=${appointment.id})}" 
   class="btn btn-sm btn-outline-primary" 
   style="border-radius: 8px;"
   aria-label="Modifier le rendez-vous">
    <i class="fas fa-edit" aria-hidden="true"></i>
</a>

<!-- Bouton supprimer (à ajouter) -->
<button type="button" 
        class="btn btn-sm btn-outline-danger" 
        onclick="confirmDelete(${appointment.id})"
        aria-label="Supprimer le rendez-vous">
    <i class="fas fa-trash" aria-hidden="true"></i>
</button>
```

---

### 3. Validation JavaScript des Dates

Ajouter après la fonction `calendar.render()` :

```javascript
// Validation : la date de fin doit être après la date de début
document.getElementById('modalStartDate').addEventListener('change', function() {
    const startDate = this.value;
    const endDateInput = document.getElementById('modalEndDate');
    
    if (startDate) {
        // Définir la date min de fin = date de début + 15 minutes
        const start = new Date(startDate);
        start.setMinutes(start.getMinutes() + 15);
        endDateInput.min = start.toISOString().slice(0, 16);
        
        // Si la date de fin est avant la date de début, l'ajuster
        if (endDateInput.value && new Date(endDateInput.value) < new Date(startDate)) {
            endDateInput.value = start.toISOString().slice(0, 16);
        }
    }
});

// Validation avant soumission
document.querySelector('#newAppointmentModal form').addEventListener('submit', function(e) {
    const start = new Date(document.getElementById('modalStartDate').value);
    const end = new Date(document.getElementById('modalEndDate').value);
    
    if (end <= start) {
        e.preventDefault();
        alert('La date de fin doit être après la date de début');
        return false;
    }
    
    // Feedback visuel
    const submitBtn = this.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Création...';
});
```

---

### 4. Améliorer Contraste des Couleurs

Dans le `<style>` du header :

```css
/* Améliorer le contraste WCAG 2.1 AA */
.text-muted {
    color: #475569 !important; /* Au lieu de #94a3b8 */
}

.badge.bg-secondary {
    background-color: #475569 !important; /* Au lieu de #6b7280 */
}

/* S'assurer que le texte sur badges colorés est lisible */
.badge {
    font-weight: 600;
    padding: 0.375em 0.75em;
}
```

---

### 5. Ajouter Région Live pour Lecteurs d'Écran

Après la div `calendar-sidebar-mobile` :

```html
<!-- Annonces pour lecteurs d'écran -->
<div role="status" aria-live="polite" aria-atomic="true" 
     class="visually-hidden" id="calendar-status">
</div>
```

Dans le JavaScript, ajouter :

```javascript
// Fonction pour annoncer les changements
function announceToScreenReader(message) {
    const status = document.getElementById('calendar-status');
    if (status) {
        status.textContent = message;
        setTimeout(() => status.textContent = '', 3000);
    }
}

// Utiliser lors des changements de vue
calendar.on('datesSet', function(info) {
    announceToScreenReader('Calendrier mis à jour : ' + info.view.title);
});

// Après chargement des événements
calendar.on('eventsSet', function(events) {
    announceToScreenReader(events.length + ' rendez-vous affichés');
});
```

---

### 6. Navigation Clavier dans le Calendrier

Ajouter après `calendar.render()` :

```javascript
// Rendre les jours navigables au clavier
document.addEventListener('keydown', function(e) {
    if (document.activeElement.classList.contains('fc-daygrid-day')) {
        const days = Array.from(document.querySelectorAll('.fc-daygrid-day'));
        const currentIndex = days.indexOf(document.activeElement);
        
        switch(e.key) {
            case 'ArrowRight':
                e.preventDefault();
                if (days[currentIndex + 1]) days[currentIndex + 1].focus();
                break;
            case 'ArrowLeft':
                e.preventDefault();
                if (days[currentIndex - 1]) days[currentIndex - 1].focus();
                break;
            case 'ArrowDown':
                e.preventDefault();
                if (days[currentIndex + 7]) days[currentIndex + 7].focus();
                break;
            case 'ArrowUp':
                e.preventDefault();
                if (days[currentIndex - 7]) days[currentIndex - 7].focus();
                break;
            case 'Enter':
            case ' ':
                e.preventDefault();
                document.activeElement.click();
                break;
        }
    }
});

// Rendre les jours focusables
calendar.on('viewDidMount', function() {
    document.querySelectorAll('.fc-daygrid-day').forEach(function(day, index) {
        day.setAttribute('tabindex', index === 0 ? '0' : '-1');
        day.setAttribute('role', 'button');
        const date = day.dataset.date;
        day.setAttribute('aria-label', 'Sélectionner le ' + new Date(date).toLocaleDateString('fr-FR'));
    });
});
```

---

## ⚡ PERFORMANCE - HAUTE PRIORITÉ

### 7. Cache Client pour Événements

Remplacer la fonction `events` dans FullCalendar :

```javascript
// Cache simple avec expiration
const eventCache = new Map();
const CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

events: function(info, successCallback, failureCallback) {
    var startDate = info.start.toISOString().split('T')[0];
    var endDate = info.end.toISOString().split('T')[0];
    const cacheKey = `${startDate}-${endDate}`;
    const cached = eventCache.get(cacheKey);
    
    // Utiliser le cache si valide
    if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
        console.log('📦 Using cached events');
        successCallback(cached.data);
        return;
    }
    
    console.log('🌐 Loading events from server:', startDate, 'to', endDate);
    
    fetch('/appointments/api/events?start=' + startDate + '&end=' + endDate)
        .then(response => {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.json();
        })
        .then(data => {
            console.log('✅ Events loaded:', data.length, 'event(s)');
            // Mettre en cache
            eventCache.set(cacheKey, { data, timestamp: Date.now() });
            successCallback(data);
        })
        .catch(error => {
            console.error('❌ Error loading events:', error);
            appAlert('Erreur lors du chargement du calendrier.', 'danger');
            failureCallback(error);
        });
}
```

---

### 8. Optimiser Requêtes SQL

**Fichier:** `AppointmentRepository.java`

Ajouter cette méthode :

```java
@Query("SELECT DISTINCT a FROM Appointment a " +
       "LEFT JOIN FETCH a.client " +
       "LEFT JOIN FETCH a.case " +
       "LEFT JOIN FETCH a.lawyer " +
       "WHERE a.lawyer.id = :lawyerId " +
       "AND a.appointmentDate BETWEEN :start AND :end " +
       "ORDER BY a.appointmentDate ASC")
List<Appointment> findByLawyerAndDateRangeWithDetails(
    @Param("lawyerId") Long lawyerId,
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end
);
```

Utiliser dans le service au lieu de la méthode actuelle.

---

## 🎨 UX - MOYENNE PRIORITÉ

### 9. Confirmation avant Suppression

Ajouter fonction JavaScript :

```javascript
function confirmDelete(appointmentId) {
    if (confirm('⚠️ Êtes-vous sûr de vouloir supprimer ce rendez-vous ?\n\nCette action est irréversible.')) {
        const csrfToken = document.querySelector('meta[name="_csrf"]').content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
        
        fetch('/appointments/' + appointmentId + '/delete', {
            method: 'DELETE',
            headers: {
                [csrfHeader]: csrfToken
            }
        })
        .then(response => {
            if (response.ok) {
                window.location.reload();
            } else {
                alert('Erreur lors de la suppression');
            }
        });
    }
}
```

---

### 10. Filtres Rapides

Ajouter avant les cartes statistiques :

```html
<div class="d-flex gap-2 mb-3 flex-wrap">
    <button class="btn btn-sm btn-outline-secondary" onclick="filterEvents('all')">
        Tous
    </button>
    <button class="btn btn-sm btn-outline-primary" onclick="filterEvents('CLIENT_MEETING')">
        <i class="fas fa-user"></i> Clients
    </button>
    <button class="btn btn-sm btn-outline-danger" onclick="filterEvents('COURT_HEARING')">
        <i class="fas fa-gavel"></i> Audiences
    </button>
    <button class="btn btn-sm btn-outline-info" onclick="filterEvents('INTERNAL')">
        <i class="fas fa-users"></i> Internes
    </button>
</div>

<script>
function filterEvents(type) {
    if (type === 'all') {
        calendar.getEvents().forEach(e => e.setProp('display', 'auto'));
    } else {
        calendar.getEvents().forEach(e => {
            const shouldShow = e.extendedProps.type === type;
            e.setProp('display', shouldShow ? 'auto' : 'none');
        });
    }
}
</script>
```

---

## 📝 Checklist Application

- [ ] Ajouter IDs aux champs du formulaire
- [ ] Ajouter aria-labels sur boutons d'action
- [ ] Validation JavaScript des dates
- [ ] Améliorer contraste couleurs
- [ ] Ajouter région live ARIA
- [ ] Navigation clavier calendrier
- [ ] Cache client événements
- [ ] Optimiser requêtes SQL
- [ ] Confirmation suppression
- [ ] Filtres rapides

---

**Temps estimé:** 2-3 heures  
**Impact:** Sécurité ⬆️ Accessibilité ⬆️ Performance ⬆️  
**Priorité:** 🔴 HAUTE
