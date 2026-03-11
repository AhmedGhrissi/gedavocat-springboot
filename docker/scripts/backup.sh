#!/bin/bash
# ============================================================
# DocAvocat — Script de backup automatique MySQL
#
# - Dump complet toutes les 4h (--single-transaction, cohérent)
# - Compression gzip
# - Rétention 30 jours (nettoyage automatique)
# - Conçu pour être étendu vers Scaleway Object Storage (S3)
#
# Variables d'environnement requises :
#   MYSQL_ROOT_PASSWORD — mot de passe root MySQL
# ============================================================

set -euo pipefail

BACKUP_DIR="/backups"
DB_HOST="mysql"
DB_NAME="gedavocat"
RETENTION_DAYS=30
INTERVAL_SECONDS=14400  # 4h

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] [BACKUP] $*"
}

run_backup() {
  local TIMESTAMP
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  local FILENAME="${DB_NAME}_${TIMESTAMP}.sql.gz"
  local FILEPATH="${BACKUP_DIR}/${FILENAME}"

  log "Démarrage backup → ${FILENAME}"

  # Dump + compression atomique (fichier tmp pour éviter un backup corrompu)
  local TMP_FILEPATH="${FILEPATH}.tmp"

  if mysqldump \
      -h "${DB_HOST}" \
      -u root \
      -p"${MYSQL_ROOT_PASSWORD}" \
      --single-transaction \
      --routines \
      --triggers \
      --events \
      --set-gtid-purged=OFF \
      "${DB_NAME}" 2>/dev/null | gzip > "${TMP_FILEPATH}"; then

    mv "${TMP_FILEPATH}" "${FILEPATH}"

    local SIZE
    SIZE=$(du -sh "${FILEPATH}" | cut -f1)
    log "Backup OK → ${FILENAME} (${SIZE})"

    # Nettoyage des backups plus vieux que RETENTION_DAYS jours
    local DELETED
    DELETED=$(find "${BACKUP_DIR}" -name "*.sql.gz" -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)
    if [ "${DELETED}" -gt 0 ]; then
      log "Nettoyage → ${DELETED} backup(s) supprimé(s) (>${RETENTION_DAYS}j)"
    fi

    # Résumé des backups disponibles
    local TOTAL
    TOTAL=$(find "${BACKUP_DIR}" -name "*.sql.gz" | wc -l)
    log "Backups disponibles : ${TOTAL} fichier(s)"

    # ──────────────────────────────────────────────────────
    # TODO PHASE 2 : Upload vers Scaleway Object Storage
    # Décommenter quand les credentials S3 seront disponibles :
    #
    # aws s3 cp "${FILEPATH}" "s3://${S3_BUCKET}/mysql/${FILENAME}" \
    #   --endpoint-url "${S3_ENDPOINT}" \
    #   --storage-class STANDARD
    # log "Upload S3 OK → s3://${S3_BUCKET}/mysql/${FILENAME}"
    # ──────────────────────────────────────────────────────

  else
    log "ERREUR : Backup échoué → nettoyage du fichier temporaire"
    rm -f "${TMP_FILEPATH}"
    return 1
  fi
}

# ── Démarrage ───────────────────────────────────────────────
log "Service backup démarré"
log "Intervalle : ${INTERVAL_SECONDS}s (4h) | Rétention : ${RETENTION_DAYS} jours"
log "Stockage   : ${BACKUP_DIR}"

# Attendre que MySQL soit prêt
until mysqladmin ping -h "${DB_HOST}" -u root -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; do
  log "En attente de MySQL..."
  sleep 5
done
log "MySQL disponible"

# Premier backup immédiat au démarrage du container
run_backup || log "AVERTISSEMENT : Premier backup échoué — retry dans ${INTERVAL_SECONDS}s"

# Boucle principale
while true; do
  log "Prochain backup dans 4h..."
  sleep "${INTERVAL_SECONDS}"
  run_backup || log "AVERTISSEMENT : Backup échoué — retry dans ${INTERVAL_SECONDS}s"
done
