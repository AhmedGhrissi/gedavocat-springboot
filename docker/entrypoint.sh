#!/bin/sh
# ============================================================
# DocAvocat — Entrypoint
# Vérifie/crée les répertoires de données et corrige les droits
# avant de lancer l'application en tant qu'utilisateur docavocat.
# ============================================================

set -e

UPLOAD_DIR="${APP_UPLOAD_DIR:-/opt/docavocat/uploads/documents}"
SIGNATURE_DIR="${APP_SIGNATURE_DIR:-/opt/docavocat/uploads/signatures}"
INVOICE_DIR="/opt/docavocat/uploads/invoices"
TEMP_DIR="${APP_TEMP_DIR:-/opt/docavocat/temp}"
LOG_DIR="/opt/docavocat/logs"
BACKUP_DIR="/opt/docavocat/backups"
CRYPTO_KEYS_DIR="/app/config/keys"

# Créer les sous-répertoires si absents
for dir in "$UPLOAD_DIR" "$SIGNATURE_DIR" "$INVOICE_DIR" "$TEMP_DIR" "$LOG_DIR" "$BACKUP_DIR" "$CRYPTO_KEYS_DIR"; do
  mkdir -p "$dir" 2>/dev/null || true
done

# L'utilisateur docavocat est déjà défini par USER dans le Dockerfile
# Pas besoin de chown ni de gosu

# ── Attendre que MySQL soit joignable (DNS + port) ────────────
# Docker DNS peut mettre quelques secondes à propager les noms de service.
echo "Attente de la resolution DNS de mysql..."
WAIT_MAX=60
WAIT_COUNT=0
while [ $WAIT_COUNT -lt $WAIT_MAX ]; do
  if getent hosts mysql >/dev/null 2>&1; then
    echo "DNS mysql resolu apres ${WAIT_COUNT}s"
    break
  fi
  WAIT_COUNT=$((WAIT_COUNT + 2))
  sleep 2
done
if [ $WAIT_COUNT -ge $WAIT_MAX ]; then
  echo "ATTENTION: mysql non resolu apres ${WAIT_MAX}s — demarrage quand meme..."
fi

# Attendre que le port 3306 soit ouvert (max 30s supplementaires)
echo "Verification port mysql:3306..."
PORT_WAIT=0
while [ $PORT_WAIT -lt 30 ]; do
  if curl -s -o /dev/null --max-time 2 --connect-timeout 2 telnet://mysql:3306 2>/dev/null; then
    echo "Port mysql:3306 accessible apres ${PORT_WAIT}s"
    break
  fi
  PORT_WAIT=$((PORT_WAIT + 2))
  sleep 2
done

# Lancer l'application
exec java \
    -Xms256m -Xmx512m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -jar /app/app.jar "$@"
