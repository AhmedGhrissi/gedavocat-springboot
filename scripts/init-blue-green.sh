#!/bin/bash
# ============================================================
# init-blue-green.sh — Initialisation Blue/Green sur Hetzner
#
# A exécuter UNE SEULE FOIS sur le serveur lors de la mise en place.
# Configure nginx pour le Blue/Green deploy zero-downtime.
#
# Usage :
#   ssh root@<SERVER_HOST> "bash -s" < scripts/init-blue-green.sh
# ============================================================
set -e

NGINX_CONF_DIR="/etc/nginx/conf.d"
UPSTREAM_FILE="${NGINX_CONF_DIR}/upstream-app.conf"
NGINX_MAIN="/etc/nginx/sites-available/docavocat.conf"

echo "=== Initialisation Blue/Green Deploy ==="

# 1. Créer le fichier upstream (slot blue par défaut)
if [ ! -f "$UPSTREAM_FILE" ]; then
  mkdir -p "$NGINX_CONF_DIR"
  echo "upstream docavocat_app { server 127.0.0.1:8080; }" > "$UPSTREAM_FILE"
  echo "Fichier upstream créé : blue (port 8080)"
else
  echo "Fichier upstream déjà présent :"
  cat "$UPSTREAM_FILE"
fi

# 2. Vérifier que nginx inclut conf.d/
if ! grep -q "include /etc/nginx/conf.d" /etc/nginx/nginx.conf 2>/dev/null; then
  echo "ATTENTION: /etc/nginx/nginx.conf n'inclut pas conf.d/ — ajout manuel requis"
  echo "  Ajouter dans le bloc http {} :"
  echo "  include /etc/nginx/conf.d/*.conf;"
else
  echo "nginx.conf inclut bien conf.d/"
fi

# 3. Vérifier que docavocat.conf utilise http://docavocat_app
if grep -q "proxy_pass http://127.0.0.1:8080" "$NGINX_MAIN" 2>/dev/null; then
  echo "ATTENTION: $NGINX_MAIN pointe encore sur 127.0.0.1:8080 (hardcoded)"
  echo "Mettez à jour le fichier nginx avec la version du repo (docker/nginx/docavocat.conf)"
elif grep -q "proxy_pass http://docavocat_app" "$NGINX_MAIN" 2>/dev/null; then
  echo "nginx docavocat.conf correctement configuré avec upstream docavocat_app"
else
  echo "ATTENTION: impossible de vérifier $NGINX_MAIN — vérifiez manuellement"
fi

# 4. Test nginx
echo "=== Test configuration nginx ==="
nginx -t

# 5. Reload nginx
echo "=== Reload nginx ==="
nginx -s reload

# 6. Vérifier que app-blue est bien en cours
echo "=== Status conteneurs Docker ==="
cd /opt/docavocat
docker compose ps

echo ""
echo "=== Initialisation terminée ==="
echo "  Slot actif  : blue (port 8080)"
echo "  Slot inactif: green (port 8081, arrêté)"
echo ""
echo "Note : Lors du prochain deploy CI, la bascule se fera automatiquement."
echo "Pour voir le slot actif à tout moment :"
echo "  cat $UPSTREAM_FILE"
