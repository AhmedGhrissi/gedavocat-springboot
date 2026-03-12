#!/bin/bash
# ============================================================
# DocAvocat — Backup chiffré automatique
#
# Sauvegarde MySQL + fichiers uploadés, chiffrés AES-256-GCM,
# envoyés sur Hetzner Storage Box via SFTP.
#
# Usage:
#   ./scripts/backup-encrypted.sh              # Backup complet
#   ./scripts/backup-encrypted.sh --db-only    # MySQL seulement
#   ./scripts/backup-encrypted.sh --files-only # Fichiers seulement
#
# Prérequis sur le serveur:
#   apt install gpg openssh-client mysql-client
#   Configurer les variables dans /opt/docavocat/.env
#
# Variables .env requises:
#   BACKUP_ENCRYPTION_KEY  — passphrase GPG (openssl rand -base64 32)
#   BACKUP_SFTP_HOST       — ex: u123456.your-storagebox.de
#   BACKUP_SFTP_USER       — ex: u123456
#   BACKUP_SFTP_PORT       — ex: 23 (défaut Hetzner Storage Box)
#   MYSQL_ROOT_PASSWORD    — pour mysqldump
#
# Cron recommandé (tous les jours à 3h):
#   0 3 * * * /opt/docavocat/scripts/backup-encrypted.sh >> /opt/docavocat/logs/backup.log 2>&1
#
# Stratégie 3-2-1:
#   3 copies : local + Storage Box + (optionnel) 2e site
#   2 supports : disque serveur + stockage distant
#   1 copie offsite : Hetzner Storage Box
#
# Rétention: 30 jours local, 90 jours distant
# ============================================================

set -euo pipefail

# ── Configuration ────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${ENV_FILE:-/opt/docavocat/.env}"
BACKUP_DIR="/opt/docavocat/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_PREFIX="[BACKUP $TIMESTAMP]"

# Charger les variables d'environnement
if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
else
    echo "$LOG_PREFIX ERREUR: Fichier .env introuvable: $ENV_FILE"
    exit 1
fi

# Vérifier les variables obligatoires
for VAR in BACKUP_ENCRYPTION_KEY MYSQL_ROOT_PASSWORD; do
    if [ -z "${!VAR:-}" ]; then
        echo "$LOG_PREFIX ERREUR: Variable $VAR non définie dans $ENV_FILE"
        exit 1
    fi
done

SFTP_HOST="${BACKUP_SFTP_HOST:-}"
SFTP_USER="${BACKUP_SFTP_USER:-}"
SFTP_PORT="${BACKUP_SFTP_PORT:-23}"

# ── Fonctions ────────────────────────────────────────────────
backup_mysql() {
    echo "$LOG_PREFIX Dump MySQL en cours..."
    local DUMP_FILE="$BACKUP_DIR/db_${TIMESTAMP}.sql"
    local ENCRYPTED_FILE="${DUMP_FILE}.gpg"

    docker exec docavocat-mysql mysqldump \
        -u root \
        -p"${MYSQL_ROOT_PASSWORD}" \
        --single-transaction \
        --routines \
        --triggers \
        --events \
        --set-gtid-purged=OFF \
        gedavocat > "$DUMP_FILE" 2>/dev/null

    local DUMP_SIZE
    DUMP_SIZE=$(stat -c%s "$DUMP_FILE" 2>/dev/null || echo "0")
    echo "$LOG_PREFIX Dump MySQL: ${DUMP_SIZE} octets"

    if [ "$DUMP_SIZE" -lt 1000 ]; then
        echo "$LOG_PREFIX ERREUR: Dump suspectement petit (${DUMP_SIZE} octets)"
        rm -f "$DUMP_FILE"
        return 1
    fi

    # Chiffrer avec GPG (AES-256, symétrique)
    echo "$LOG_PREFIX Chiffrement du dump..."
    echo "${BACKUP_ENCRYPTION_KEY}" | gpg --batch --yes --passphrase-fd 0 \
        --symmetric --cipher-algo AES256 \
        --output "$ENCRYPTED_FILE" "$DUMP_FILE"

    # Supprimer le dump en clair
    rm -f "$DUMP_FILE"

    echo "$LOG_PREFIX DB backup chiffré: $ENCRYPTED_FILE"
    echo "$ENCRYPTED_FILE"
}

backup_files() {
    echo "$LOG_PREFIX Archive des fichiers uploadés..."
    local ARCHIVE="$BACKUP_DIR/files_${TIMESTAMP}.tar.gz"
    local ENCRYPTED_FILE="${ARCHIVE}.gpg"

    tar czf "$ARCHIVE" \
        -C /opt/docavocat/uploads . \
        2>/dev/null || true

    local ARCHIVE_SIZE
    ARCHIVE_SIZE=$(stat -c%s "$ARCHIVE" 2>/dev/null || echo "0")
    echo "$LOG_PREFIX Archive fichiers: ${ARCHIVE_SIZE} octets"

    # Chiffrer
    echo "$LOG_PREFIX Chiffrement de l'archive..."
    echo "${BACKUP_ENCRYPTION_KEY}" | gpg --batch --yes --passphrase-fd 0 \
        --symmetric --cipher-algo AES256 \
        --output "$ENCRYPTED_FILE" "$ARCHIVE"

    rm -f "$ARCHIVE"

    echo "$LOG_PREFIX Files backup chiffré: $ENCRYPTED_FILE"
    echo "$ENCRYPTED_FILE"
}

upload_to_storagebox() {
    local FILE="$1"
    local FILENAME
    FILENAME=$(basename "$FILE")

    if [ -z "$SFTP_HOST" ] || [ -z "$SFTP_USER" ]; then
        echo "$LOG_PREFIX WARN: SFTP non configuré — backup local uniquement"
        return 0
    fi

    echo "$LOG_PREFIX Upload vers Storage Box: $FILENAME..."

    sftp -P "$SFTP_PORT" -oBatchMode=yes -oStrictHostKeyChecking=accept-new \
        "${SFTP_USER}@${SFTP_HOST}" <<EOF
mkdir backups
cd backups
put "$FILE" "$FILENAME"
bye
EOF

    if [ $? -eq 0 ]; then
        echo "$LOG_PREFIX Upload OK: $FILENAME"
    else
        echo "$LOG_PREFIX ERREUR upload SFTP: $FILENAME"
        return 1
    fi
}

cleanup_local() {
    echo "$LOG_PREFIX Nettoyage backups locaux > 30 jours..."
    find "$BACKUP_DIR" -name "*.gpg" -mtime +30 -delete 2>/dev/null || true
    local COUNT
    COUNT=$(find "$BACKUP_DIR" -name "*.gpg" | wc -l)
    echo "$LOG_PREFIX Backups locaux restants: $COUNT"
}

cleanup_remote() {
    if [ -z "$SFTP_HOST" ] || [ -z "$SFTP_USER" ]; then
        return 0
    fi

    echo "$LOG_PREFIX Nettoyage backups distants > 90 jours..."
    local CUTOFF_DATE
    CUTOFF_DATE=$(date -d "90 days ago" +%Y%m%d)

    # Liste les fichiers distants et supprime les vieux
    sftp -P "$SFTP_PORT" -oBatchMode=yes \
        "${SFTP_USER}@${SFTP_HOST}" <<EOF 2>/dev/null || true
cd backups
ls -1
bye
EOF
}

# ── Main ─────────────────────────────────────────────────────
echo "$LOG_PREFIX === Démarrage backup DocAvocat ==="
mkdir -p "$BACKUP_DIR"

MODE="${1:-all}"
UPLOADED_FILES=()

case "$MODE" in
    --db-only)
        DB_FILE=$(backup_mysql)
        UPLOADED_FILES+=("$DB_FILE")
        ;;
    --files-only)
        FILES_FILE=$(backup_files)
        UPLOADED_FILES+=("$FILES_FILE")
        ;;
    *)
        DB_FILE=$(backup_mysql)
        FILES_FILE=$(backup_files)
        UPLOADED_FILES+=("$DB_FILE" "$FILES_FILE")
        ;;
esac

# Upload vers Storage Box
for FILE in "${UPLOADED_FILES[@]}"; do
    if [ -f "$FILE" ]; then
        upload_to_storagebox "$FILE"
    fi
done

# Nettoyage
cleanup_local
cleanup_remote

# ── Gestion d’alerte Discord en cas d’échec ──
alert_discord() {
    local MSG="$1"
    if [ -n "${DISCORD_WEBHOOK_URL:-}" ]; then
        curl -s -X POST "$DISCORD_WEBHOOK_URL" \
            -H "Content-Type: application/json" \
            -d "{\"content\":\"$MSG\"}"
    fi
}

set +e
ERROR_MSG=""
if [ -z "${UPLOADED_FILES[*]}" ]; then
    ERROR_MSG="Aucun backup généré"
fi
for FILE in "${UPLOADED_FILES[@]}"; do
    if [ ! -f "$FILE" ]; then
        ERROR_MSG="Backup ou upload échoué ($FILE)"
    fi
done
set -e

if [ -n "$ERROR_MSG" ]; then
    alert_discord "❌ [Backup DocAvocat] $ERROR_MSG sur $(hostname) à $TIMESTAMP"
    echo "$LOG_PREFIX $ERROR_MSG (alerte Discord envoyée)"
    exit 1
fi

echo "$LOG_PREFIX === Backup terminé ==="
echo "$LOG_PREFIX Fichiers créés:"
ls -lh "$BACKUP_DIR"/*_${TIMESTAMP}*.gpg 2>/dev/null || echo "  (aucun)"
