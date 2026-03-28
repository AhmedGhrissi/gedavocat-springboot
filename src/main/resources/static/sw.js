// ════════════════════════════════════════════════
//   SERVICE WORKER — DocAvocat PWA
//   Cache-first pour les assets, network-first pour les pages
// ════════════════════════════════════════════════

const CACHE_NAME = 'docavocat-v3';
const STATIC_ASSETS = [
    '/css/layout.css',
    '/css/global-unified-theme.css',
    '/css/app.css',
    '/css/responsive-mobile.css',
    '/js/app.js',
    '/js/main.js',
    '/manifest.json',
    '/img/icons/icon-192x192.png',
    '/img/icons/icon-512x512.png'
];

// Install — cache static assets
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(STATIC_ASSETS))
            .then(() => self.skipWaiting())
    );
});

// Activate — clean up old caches
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(
                keys.filter(key => key !== CACHE_NAME)
                    .map(key => caches.delete(key))
            )
        ).then(() => self.clients.claim())
    );
});

// Fetch — network-first for navigations, cache-first for assets
self.addEventListener('fetch', event => {
    const request = event.request;

    // Skip non-GET requests
    if (request.method !== 'GET') return;

    // Skip cross-origin requests (Google Fonts, CDNs, extensions, etc.)
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;

    // Skip API calls and form submissions
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/logout')) return;

    // Navigation requests: network-first with offline fallback
    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request)
                .then(response => {
                    if (response.ok && response.type !== 'opaqueredirect') {
                        const clone = response.clone();
                        caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
                    }
                    return response;
                })
                .catch(() => caches.match(request).then(cached => cached || caches.match('/dashboard')))
        );
        return;
    }

    // Static assets: cache-first
    if (request.destination === 'style' ||
        request.destination === 'script' ||
        request.destination === 'image' ||
        request.destination === 'font') {
        event.respondWith(
            caches.match(request).then(cached => {
                if (cached) return cached;
                return fetch(request).then(response => {
                    if (response.ok) {
                        const clone = response.clone();
                        caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
                    }
                    return response;
                });
            })
        );
        return;
    }

    // All other requests: network-first
    event.respondWith(
        fetch(request)
            .then(response => {
                if (response.ok) {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
                }
                return response;
            })
            .catch(() => caches.match(request))
    );
});
