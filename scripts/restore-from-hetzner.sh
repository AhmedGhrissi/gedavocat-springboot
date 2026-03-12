#!/bin/bash
# ============================================================
# DocAvocat — Restauration automatisée depuis Hetzner Storage Box
# Usage : ./restore-from-hetzner.sh <backup_file.sql.gpg>
# Prérequis : gpg, mysql-client, openssh-client
# Variables requises : BACKUP_ENCRYPTION_KEY, BACKUP_SFTP_HOST, BACKUP_SFTP_USER, BACKUP_SFTP_PORT, MYSQL_ROOT_PASSWORD
# ============================================================
set -euo pipefail

BACKUP_FILE="$1"
RESTORE_DIR="/tmp/restore"
mkdir -p "$RESTORE_DIR"

# Charger les variables d'environnement
if [ -f /opt/docavocat/.env ]; then
    set -a
    source /opt/docavocat/.env
    set +a
else
    echo "ERREUR: /opt/docavocat/.env introuvable"
    exit 1
fi

# Télécharger le backup depuis Hetzner si besoin
if [[ "$BACKUP_FILE" == sftp:* ]]; then
    SFTP_PATH="${BACKUP_FILE#sftp://}"
    sftp -P "${BACKUP_SFTP_PORT:-23}" -oBatchMode=yes -oStrictHostKeyChecking=accept-new \
        "${BACKUP_SFTP_USER}@${BACKUP_SFTP_HOST}" <<EOF
cd backups
get "${SFTP_PATH}"
bye
EOF
    BACKUP_FILE="$(basename "$SFTP_PATH")"
fi

# Déchiffrer le backup
DECRYPTED_SQL="$RESTORE_DIR/restore.sql"
echo "$BACKUP_ENCRYPTION_KEY" | gpg --batch --yes --passphrase-fd 0 -o "$DECRYPTED_SQL" -d "$BACKUP_FILE"

# Restauration MySQL
mysql -u root -p"$MYSQL_ROOT_PASSWORD" gedavocat < "$DECRYPTED_SQL"
echo "Restauration terminée : $DECRYPTED_SQL importé dans la base 'gedavocat'"
