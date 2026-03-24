(function(window, document) {
    'use strict';

    function initLandingPublicNav() {
        var mobileMenuBtn = document.getElementById('landingMobileMenuBtn');
        var mobileMenu = document.getElementById('landingMobileMenu');

        if (!mobileMenuBtn || !mobileMenu) {
            return;
        }

        function setExpanded(expanded) {
            mobileMenu.hidden = !expanded;
            mobileMenu.classList.toggle('open', expanded);
            mobileMenuBtn.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            mobileMenuBtn.innerHTML = expanded
                ? '<i class="fas fa-times"></i>'
                : '<i class="fas fa-bars"></i>';
        }

        function closeMenu() {
            setExpanded(false);
        }

        function toggleMenu() {
            setExpanded(!mobileMenu.classList.contains('open'));
        }

        mobileMenuBtn.addEventListener('click', function(event) {
            event.preventDefault();
            toggleMenu();
        });

        mobileMenu.querySelectorAll('a, button').forEach(function(item) {
            item.addEventListener('click', function() {
                if (window.innerWidth <= 768) {
                    closeMenu();
                }
            });
        });

        window.addEventListener('resize', function() {
            if (window.innerWidth > 768) {
                closeMenu();
            }
        });

        document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape') {
                closeMenu();
            }
        });

        setExpanded(false);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initLandingPublicNav, { once: true });
    } else {
        initLandingPublicNav();
    }
})(window, document);