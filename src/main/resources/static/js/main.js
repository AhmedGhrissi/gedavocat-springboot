/* ===================================================================
   GED Avocat - JavaScript principal
   =================================================================== */

document.addEventListener('DOMContentLoaded', function() {
    
    // Toggle menu mobile
    const navbarToggle = document.getElementById('navbarToggle');
    const navbarMenu = document.getElementById('navbarMenu');
    
    if (navbarToggle && navbarMenu) {
        navbarToggle.addEventListener('click', function() {
            navbarMenu.classList.toggle('active');
        });
    }
    
    // Toggle dropdown mobile
    const dropdowns = document.querySelectorAll('.has-dropdown');
    dropdowns.forEach(dropdown => {
        dropdown.addEventListener('click', function(e) {
            if (window.innerWidth <= 768) {
                e.preventDefault();
                this.classList.toggle('active');
            }
        });
    });
    
    // Fermer les alerts
    const alertCloseButtons = document.querySelectorAll('.alert-close');
    alertCloseButtons.forEach(button => {
        button.addEventListener('click', function() {
            const alert = this.closest('.alert');
            alert.style.display = 'none';
        });
    });
    
    // Auto-fermeture des alerts après 5 secondes
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.opacity = '0';
            setTimeout(() => {
                alert.style.display = 'none';
            }, 300);
        }, 5000);
    });
    
    // Confirmation avant suppression — handled by appConfirm in layout.html
    
    // Validation de formulaire
    const forms = document.querySelectorAll('.needs-validation');
    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            if (!form.checkValidity()) {
                e.preventDefault();
                e.stopPropagation();
            }
            form.classList.add('was-validated');
        });
    });
    
    // Prévisualisation d'image
    const imageInputs = document.querySelectorAll('input[type="file"][data-preview]');
    imageInputs.forEach(input => {
        input.addEventListener('change', function() {
            const previewId = this.getAttribute('data-preview');
            const preview = document.getElementById(previewId);
            
            if (this.files && this.files[0] && preview) {
                const reader = new FileReader();
                reader.onload = function(e) {
                    preview.src = e.target.result;
                };
                reader.readAsDataURL(this.files[0]);
            }
        });
    });
    
    // Recherche en temps réel
    const searchInputs = document.querySelectorAll('[data-search]');
    searchInputs.forEach(input => {
        let timeout;
        input.addEventListener('input', function() {
            clearTimeout(timeout);
            const searchTerm = this.value.toLowerCase();
            const targetSelector = this.getAttribute('data-search');
            const items = document.querySelectorAll(targetSelector);
            
            timeout = setTimeout(() => {
                items.forEach(item => {
                    const text = item.textContent.toLowerCase();
                    if (text.includes(searchTerm)) {
                        item.style.display = '';
                    } else {
                        item.style.display = 'none';
                    }
                });
            }, 300);
        });
    });
    
    // Copier dans le presse-papiers
    const copyButtons = document.querySelectorAll('[data-copy]');
    copyButtons.forEach(button => {
        button.addEventListener('click', function() {
            const text = this.getAttribute('data-copy');
            navigator.clipboard.writeText(text).then(() => {
                showToast('Copié dans le presse-papiers', 'success');
            });
        });
    });
    
    // Toast notification
    window.showToast = function(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `alert alert-${type}`;
        toast.style.position = 'fixed';
        toast.style.top = '20px';
        toast.style.right = '20px';
        toast.style.zIndex = '9999';
        toast.style.minWidth = '300px';
        toast.innerHTML = `
            <span>${message}</span>
            <button class="alert-close">&times;</button>
        `;
        
        document.body.appendChild(toast);
        
        const closeBtn = toast.querySelector('.alert-close');
        closeBtn.addEventListener('click', () => {
            toast.remove();
        });
        
        setTimeout(() => {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    };
});

// Utilitaires

// Formater les dates
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('fr-FR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// Formater la taille des fichiers
function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

// Fonction de debounce
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// Fonction pour basculer le panneau de notifications (appelée depuis des boutons onclick="toggleNotifPanel()")
window.toggleNotifPanel = function() {
    try {
        // Rechercher un panneau de notifications courant
        let panel = document.getElementById('notifPanel') || document.querySelector('.notification-panel');
        if (!panel) {
            // Si aucun panneau trouvé, créer un panneau simple et l'insérer dans le header
            panel = document.createElement('div');
            panel.id = 'notifPanel';
            panel.className = 'notification-panel';
            panel.style.position = 'absolute';
            panel.style.right = '16px';
            panel.style.top = '56px';
            panel.style.width = '320px';
            panel.style.maxHeight = '60vh';
            panel.style.overflowY = 'auto';
            panel.style.background = '#fff';
            panel.style.boxShadow = '0 8px 24px rgba(15,23,42,0.12)';
            panel.style.borderRadius = '8px';
            panel.style.padding = '8px';
            panel.style.zIndex = '1200';
            document.body.appendChild(panel);
        }

        const isOpen = panel.classList.contains('open');
        if (isOpen) {
            panel.classList.remove('open');
            panel.style.display = 'none';
            return;
        }

        // Ouvrir et charger les notifications via l'API (si l'utilisateur est connecté)
        panel.style.display = 'block';
        panel.classList.add('open');
        panel.innerHTML = '<div style="padding:12px;color:#64748B">Chargement des notifications...</div>';

        fetch('/api/notifications', { credentials: 'same-origin' })
            .then(resp => {
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                return resp.json();
            })
            .then(data => {
                const items = data.notifications || [];
                if (items.length === 0) {
                    panel.innerHTML = '<div style="padding:12px;color:#64748B">Aucune notification</div>';
                    return;
                }
                const list = document.createElement('div');
                list.style.display = 'flex';
                list.style.flexDirection = 'column';
                list.style.gap = '6px';
                items.forEach(n => {
                    const row = document.createElement('a');
                    row.href = n.link || '#';
                    row.style.display = 'block';
                    row.style.padding = '10px';
                    row.style.borderRadius = '6px';
                    row.style.textDecoration = 'none';
                    row.style.color = '#0f172a';
                    row.style.background = n.read ? 'transparent' : '#f8fafc';
                    row.innerHTML = `<div style="font-weight:600;font-size:14px">${n.title}</div><div style="font-size:13px;color:#64748B">${n.message}</div>`;
                    list.appendChild(row);
                });
                panel.innerHTML = '';
                panel.appendChild(list);
            })
            .catch(err => {
                panel.innerHTML = '<div style="padding:12px;color:#ef4444">Impossible de charger les notifications</div>';
                console.warn('Erreur chargement notifications', err);
            });
    } catch (e) {
        console.error('toggleNotifPanel error', e);
    }
};