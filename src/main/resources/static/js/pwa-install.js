(function(window, document) {
    'use strict';

    function mergeOptions(base, extra) {
        var output = Object.assign({}, base, extra || {});
        output.copy = Object.assign({}, base.copy || {}, (extra && extra.copy) || {});
        return output;
    }

    function initDocAvocatPwaInstall(userOptions) {
        var options = mergeOptions({
            contextKey: 'default',
            installButtonSelector: '[data-install-app]',
            bannerId: 'pwaInstallBanner',
            titleId: 'pwaInstallBannerTitle',
            noteId: 'pwaInstallBannerNote',
            actionId: 'pwaInstallAction',
            dismissId: 'pwaInstallDismiss',
            serviceWorkerUrl: '/sw.js',
            dismissDays: 3,
            autoShowDelayMs: 2200,
            minimumVisitsBeforeAutoShow: 2,
            copy: {
                defaultTitle: 'Installer DocAvocat',
                defaultNote: 'Ajoutez l\'application à votre écran d\'accueil pour y accéder plus vite.',
                iosTitle: 'Installer DocAvocat sur iPhone',
                iosNote: 'Touchez Partager puis “Sur l’écran d’accueil” pour l’ajouter comme une app.',
                androidTitle: 'Installer DocAvocat',
                androidNote: 'Dans le menu ⋮ de Chrome ou Edge, choisissez “Installer l\'application” ou “Ajouter à l\'écran d\'accueil”.',
                desktopTitle: 'Installer DocAvocat sur votre ordinateur',
                desktopNote: 'Dans Chrome ou Edge, cliquez sur l\'icône d\'installation dans la barre d\'adresse, ou ouvrez le menu ⋮ puis “Installer DocAvocat”.',
                unsupportedTitle: 'Installation rapide limitée',
                unsupportedNote: 'L\'installation est mieux prise en charge dans Chrome, Edge ou Safari sur iPhone.',
                dismissLabel: 'Plus tard'
            }
        }, userOptions);

        var installButtons = Array.from(document.querySelectorAll(options.installButtonSelector));
        var installBanner = document.getElementById(options.bannerId);
        var installBannerTitle = document.getElementById(options.titleId);
        var installBannerNote = document.getElementById(options.noteId);
        var installBannerAction = document.getElementById(options.actionId);
        var installBannerDismiss = document.getElementById(options.dismissId);
        var userAgent = window.navigator.userAgent || '';
        var isStandalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
        var isIos = /iphone|ipad|ipod/i.test(userAgent);
        var isSafari = /safari/i.test(userAgent) && !/crios|fxios|edgios|opr|chrome|android/i.test(userAgent);
        var isAndroid = /android/i.test(userAgent);
        var isEdge = /edg/i.test(userAgent);
        var isOpera = /opr/i.test(userAgent);
        var isChrome = /chrome|crios/i.test(userAgent) && !isEdge && !isOpera;
        var isChromiumFamily = !isIos && (isChrome || isEdge || isOpera);
        var supportsManualInstall = (isIos && isSafari) || isChromiumFamily;
        var dismissKey = 'docavocat-pwa-dismissed-until';
        var visitCountKey = 'docavocat-pwa-visit-count';
        var visitSessionKey = 'docavocat-pwa-visit-counted-session';
        var nudgeSessionKey = 'docavocat-pwa-nudge-shown-session:' + options.contextKey;
        var deferredPrompt = null;
        var autoShowTimer = null;

        function setInstallButtonsVisible(visible) {
            installButtons.forEach(function(button) {
                button.hidden = !visible;
            });
        }

        function hideInstallBanner() {
            if (installBanner) {
                installBanner.hidden = true;
            }
        }

        function showInstallBanner(mode) {
            if (!installBanner || isStandalone) return;

            var title = options.copy.defaultTitle;
            var note = options.copy.defaultNote;
            var showAction = !!deferredPrompt;

            if (mode === 'ios') {
                title = options.copy.iosTitle;
                note = options.copy.iosNote;
                showAction = false;
            } else if (mode === 'android') {
                title = options.copy.androidTitle;
                note = options.copy.androidNote;
                showAction = false;
            } else if (mode === 'desktop') {
                title = options.copy.desktopTitle;
                note = options.copy.desktopNote;
                showAction = false;
            } else if (mode === 'unsupported') {
                title = options.copy.unsupportedTitle;
                note = options.copy.unsupportedNote;
                showAction = false;
            }

            if (installBannerTitle) installBannerTitle.textContent = title;
            if (installBannerNote) installBannerNote.textContent = note;
            if (installBannerAction) {
                installBannerAction.hidden = !showAction;
            }

            installBanner.hidden = false;
        }

        function dismissInstallBanner() {
            hideInstallBanner();
            try {
                window.localStorage.setItem(dismissKey, String(Date.now() + (options.dismissDays * 24 * 60 * 60 * 1000)));
            } catch (error) {
                console.warn('Unable to persist install banner dismissal:', error);
            }
        }

        function canAutoShowBanner() {
            try {
                return Date.now() >= Number(window.localStorage.getItem(dismissKey) || '0');
            } catch (error) {
                return true;
            }
        }

        function getVisitCount() {
            try {
                return Number(window.localStorage.getItem(visitCountKey) || '0');
            } catch (error) {
                return 0;
            }
        }

        function incrementVisitCountOncePerSession() {
            try {
                if (!window.sessionStorage.getItem(visitSessionKey)) {
                    window.sessionStorage.setItem(visitSessionKey, '1');
                    window.localStorage.setItem(visitCountKey, String(getVisitCount() + 1));
                }
            } catch (error) {
                // Ignore storage access failures silently.
            }
        }

        function resolveManualMode() {
            if (isIos && isSafari) return 'ios';
            if (isAndroid && isChromiumFamily) return 'android';
            if (isChromiumFamily) return 'desktop';
            return 'unsupported';
        }

        function shouldShowNudge() {
            if (isStandalone || !canAutoShowBanner()) return false;
            if (getVisitCount() < options.minimumVisitsBeforeAutoShow) return false;

            try {
                return !window.sessionStorage.getItem(nudgeSessionKey);
            } catch (error) {
                return true;
            }
        }

        function rememberNudgeShown() {
            try {
                window.sessionStorage.setItem(nudgeSessionKey, '1');
            } catch (error) {
                // Ignore storage access failures silently.
            }
        }

        function scheduleManualNudge() {
            if (!supportsManualInstall || !shouldShowNudge()) return;

            window.clearTimeout(autoShowTimer);
            autoShowTimer = window.setTimeout(function() {
                if (!shouldShowNudge() || deferredPrompt || document.visibilityState !== 'visible') return;
                setInstallButtonsVisible(true);
                showInstallBanner(resolveManualMode());
                rememberNudgeShown();
            }, options.autoShowDelayMs);
        }

        function registerServiceWorker() {
            if (!('serviceWorker' in window.navigator)) return;

            window.addEventListener('load', function() {
                window.navigator.serviceWorker.register(options.serviceWorkerUrl).catch(function(error) {
                    console.warn('SW registration failed:', error);
                });
            }, { once: true });
        }

        function refreshInstallState() {
            var shouldShowButtons = !isStandalone && (supportsManualInstall || !!deferredPrompt);
            setInstallButtonsVisible(shouldShowButtons);

            if (isStandalone) {
                hideInstallBanner();
            }
        }

        async function handleInstallClick(event) {
            event.preventDefault();

            if (deferredPrompt) {
                showInstallBanner('prompt');
                deferredPrompt.prompt();

                try {
                    var choice = await deferredPrompt.userChoice;
                    if (choice.outcome === 'accepted') {
                        setInstallButtonsVisible(false);
                        hideInstallBanner();
                    } else {
                        scheduleManualNudge();
                    }
                } catch (error) {
                    console.warn('PWA prompt failed:', error);
                }

                deferredPrompt = null;
                return;
            }

            showInstallBanner(resolveManualMode());
        }

        incrementVisitCountOncePerSession();
        refreshInstallState();

        installButtons.forEach(function(button) {
            button.addEventListener('click', handleInstallClick);
        });

        if (installBannerDismiss) {
            installBannerDismiss.textContent = options.copy.dismissLabel;
            installBannerDismiss.addEventListener('click', dismissInstallBanner);
        }

        window.addEventListener('beforeinstallprompt', function(event) {
            event.preventDefault();
            deferredPrompt = event;
            setInstallButtonsVisible(true);
            if (canAutoShowBanner()) {
                showInstallBanner('prompt');
                rememberNudgeShown();
            }
        });

        window.addEventListener('appinstalled', function() {
            deferredPrompt = null;
            hideInstallBanner();
            setInstallButtonsVisible(false);
            try {
                window.localStorage.removeItem(dismissKey);
            } catch (error) {
                // Ignore storage access failures silently.
            }
        });

        var displayModeQuery = window.matchMedia('(display-mode: standalone)');
        if (typeof displayModeQuery.addEventListener === 'function') {
            displayModeQuery.addEventListener('change', refreshInstallState);
        }

        document.addEventListener('visibilitychange', function() {
            if (document.visibilityState === 'visible') {
                refreshInstallState();
                scheduleManualNudge();
            }
        });

        window.addEventListener('pageshow', function() {
            refreshInstallState();
            scheduleManualNudge();
        });

        registerServiceWorker();
        scheduleManualNudge();
    }

    window.initDocAvocatPwaInstall = initDocAvocatPwaInstall;
})(window, document);