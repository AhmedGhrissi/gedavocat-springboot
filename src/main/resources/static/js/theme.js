/* ===================================================================
   DocAvocat - Mode Clair UNIQUEMENT
   Le mode sombre est DÉSACTIVÉ pour garantir une cohérence totale
   =================================================================== */

(function() {
    'use strict';

    // FORCER le mode clair en permanence
    function forceLight() {
        const html = document.documentElement;
        html.classList.remove('dark');
        localStorage.removeItem('docavocat-theme');
    }
    
    // Appliquer immédiatement
    forceLight();
    
    // Réappliquer au chargement du DOM
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', forceLight);
    }
    
    // Masquer les boutons de basculement de thème s'ils existent
    document.addEventListener('DOMContentLoaded', function() {
        const toggleBtn = document.getElementById('darkModeToggle');
        if (toggleBtn) {
            toggleBtn.style.display = 'none';
        }
    });
    
})();
