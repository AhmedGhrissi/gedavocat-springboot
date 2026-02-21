#!/bin/bash
# ============================================================
# DocAvocat  Script de sauvegarde (serveur Docker)
#
# Sauvegarde :
#   1. Base de donnees MySQL (via conteneur Docker)
#   2. Fichiers uploades (documents, signatures, factures)
#
# Usage :
#   bash scripts/backup.sh
#
# Planification automatique (crontab sur le serveur) :
#   0 3 * * * bash /opt/gedavocat/scripts/backup.sh >> /opt/gedavocat/logs/backup.log 2>&1
# ============================================================
set -euo pipefail

#  Configuration 
REMOTE_DIR="/opt/gedavocat"
BACKUP_DIR="$REMOTE_DIR/backups"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30

#  Variables MySQL (lues depuis .env) 
if [ -f "$REMOTE_DIR/.env" ]; then
    source <(grep -E '^(MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD)=' "$REMOTE_DIR/.env")
fi
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

#  Couleurs 
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date +%T)] OK $*${NC}"; }
warn() { echo -e "${YELLOW}[$(date +%T)] WARN $*${NC}"; }
err()  { echo -e "${RED}[$(date +%T)] ERR $*${NC}"; }

echo "============================================================"
echo "  DocAvocat - Sauvegarde $(date '+%d/%m/%Y %H:%M:%S')"
echo "============================================================"

mkdir -p "$BACKUP_DIR"

#  1. Base de donnees 
echo ""
echo "[1/3] Sauvegarde MySQL via conteneur Docker..."

DB_FILE="$BACKUP_DIR/db_$DATE.sql.gz"

if docker exec docavocat-mysql mysqldump \
    -u gedavocat -p"$MYSQL_PASSWORD" \
    --single-transaction --routines --triggers \
    gedavocat 2>/dev/null | gzip > "$DB_FILE"; then
    SIZE=$(du -sh "$DB_FILE" | cut -f1)
    log "Base de donnees : $DB_FILE ($SIZE)"
else
    err "Echec de la sauvegarde MySQL"
fi

#  2. Fichiers uploades 
echo ""
echo "[2/3] Sauvegarde des fichiers uploades..."

UPLOADS_DIR="$REMOTE_DIR/uploads"
FILES_FILE="$BACKUP_DIR/uploads_$DATE.tar.gz"

if [ -d "$UPLOADS_DIR" ] && [ "$(ls -A "$UPLOADS_DIR" 2>/dev/null)" ]; then
    if tar -czf "$FILES_FILE" -C "$REMOTE_DIR" uploads 2>/dev/null; then
        SIZE=$(du -sh "$FILES_FILE" | cut -f1)
        log "Fichiers uploades : $FILES_FILE ($SIZE)"
    else
        err "Echec de la sauvegarde des fichiers"
    fi
else
    warn "Repertoire uploads vide ou inexistant, ignore."
fi

#  3. Nettoyage des anciennes sauvegardes 
echo ""
echo "[3/3] Nettoyage des sauvegardes > ${RETENTION_DAYS} jours..."

DELETED=$(find "$BACKUP_DIR" -type f \( -name "db_*.sql.gz" -o -name "uploads_*.tar.gz" \) -mtime +"$RETENTION_DAYS" -print -delete | wc -l)
log "$DELETED fichier(s) supprime(s)."

#  Resume 
echo ""
echo "============================================================"
log "Sauvegarde terminee."
echo "  Repertoire : $BACKUP_DIR"
ls -lh "$BACKUP_DIR"/*.gz 2>/dev/null || echo "  (aucun fichier)"
echo "============================================================"
