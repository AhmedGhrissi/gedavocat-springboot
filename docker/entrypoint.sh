#!/bin/sh
# ============================================================
# DocAvocat — Entrypoint
# Vérifie/crée les répertoires de données et corrige les droits
# avant de lancer l'application en tant qu'utilisateur docavocat.
# ============================================================

set -e

UPLOAD_DIR="${APP_UPLOAD_DIR:-/opt/gedavocat/uploads/documents}"
SIGNATURE_DIR="${APP_SIGNATURE_DIR:-/opt/gedavocat/uploads/signatures}"
INVOICE_DIR="/opt/gedavocat/uploads/invoices"
TEMP_DIR="${APP_TEMP_DIR:-/opt/gedavocat/temp}"
LOG_DIR="/opt/gedavocat/logs"
BACKUP_DIR="/opt/gedavocat/backups"

# Créer les sous-répertoires si absents
for dir in "$UPLOAD_DIR" "$SIGNATURE_DIR" "$INVOICE_DIR" "$TEMP_DIR" "$LOG_DIR" "$BACKUP_DIR"; do
  mkdir -p "$dir" 2>/dev/null || true
done

# L'utilisateur docavocat est déjà défini par USER dans le Dockerfile
# Pas besoin de chown ni de gosu

# Lancer l'application
exec java \
    -Xms256m -Xmx512m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -jar /app/app.jar "$@"
