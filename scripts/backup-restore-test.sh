#!/bin/bash
# ============================================================
# DocAvocat — Test de restauration de backup
#
# Vérifie qu'un backup chiffré peut être déchiffré et restauré.
# À exécuter hebdomadairement (cron) pour valider l'intégrité.
#
# Usage:
#   ./scripts/backup-restore-test.sh [fichier.sql.gpg]
#
# Sans argument, teste le dernier backup disponible.
# ============================================================

set -euo pipefail

ENV_FILE="${ENV_FILE:-/opt/docavocat/.env}"
BACKUP_DIR="/opt/docavocat/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_PREFIX="[RESTORE-TEST $TIMESTAMP]"

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

if [ -z "${BACKUP_ENCRYPTION_KEY:-}" ]; then
    echo "$LOG_PREFIX ERREUR: BACKUP_ENCRYPTION_KEY non définie"
    exit 1
fi

# Trouver le fichier à tester
if [ -n "${1:-}" ]; then
    BACKUP_FILE="$1"
else
    BACKUP_FILE=$(find "$BACKUP_DIR" -name "db_*.sql.gpg" -type f | sort -r | head -1)
fi

if [ -z "$BACKUP_FILE" ] || [ ! -f "$BACKUP_FILE" ]; then
    echo "$LOG_PREFIX ERREUR: Aucun backup trouvé"
    exit 1
fi

echo "$LOG_PREFIX Test de restauration: $(basename "$BACKUP_FILE")"

# Déchiffrer dans un fichier temporaire
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

DECRYPTED="$TEMP_DIR/test-restore.sql"

echo "$LOG_PREFIX Déchiffrement..."
echo "${BACKUP_ENCRYPTION_KEY}" | gpg --batch --yes --passphrase-fd 0 \
    --decrypt --output "$DECRYPTED" "$BACKUP_FILE"

# Vérifier le contenu
LINES=$(wc -l < "$DECRYPTED")
SIZE=$(stat -c%s "$DECRYPTED")
HAS_CREATE=$(grep -c "CREATE TABLE" "$DECRYPTED" || true)
HAS_INSERT=$(grep -c "INSERT INTO" "$DECRYPTED" || true)

echo "$LOG_PREFIX Résultat:"
echo "  Taille: $SIZE octets"
echo "  Lignes: $LINES"
echo "  CREATE TABLE: $HAS_CREATE"
echo "  INSERT INTO: $HAS_INSERT"

if [ "$LINES" -gt 100 ] && [ "$HAS_CREATE" -gt 0 ]; then
    echo "$LOG_PREFIX ✓ Backup valide — déchiffrement et contenu OK"
    exit 0
else
    echo "$LOG_PREFIX ✗ Backup suspect — contenu insuffisant"
    exit 1
fi
