#!/bin/bash
# ============================================================
# DocAvocat — Installation des tâches cron pour le serveur Hetzner
# À exécuter UNE FOIS sur le serveur de production
# Usage : sudo bash scripts/install-cron.sh
# ============================================================

set -euo pipefail

CRON_FILE="/etc/cron.d/docavocat"
INSTALL_DIR="/opt/docavocat"
LOG_DIR="/opt/docavocat/logs"

# Créer le répertoire de logs si nécessaire
mkdir -p "${LOG_DIR}"

cat > "${CRON_FILE}" << 'EOF'
# ============================================================
# DocAvocat — Tâches planifiées (crontab)
# ============================================================
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
MAILTO=""

# ── Backup chiffré quotidien (03h00) ────────────────────────
# Dump MySQL + archive fichiers → GPG AES-256 → Hetzner Storage Box
0 3 * * * root /opt/docavocat/scripts/backup-encrypted.sh >> /opt/docavocat/logs/backup.log 2>&1

# ── Test de restauration hebdomadaire (dimanche 05h00) ──────
# Vérifie l'intégrité du dernier backup (déchiffrement + validation SQL)
0 5 * * 0 root /opt/docavocat/scripts/backup-restore-test.sh >> /opt/docavocat/logs/backup-test.log 2>&1

# ── Rotation des logs applicatifs (quotidien 02h00) ─────────
0 2 * * * root find /opt/docavocat/logs -name "*.log" -mtime +30 -delete

# ── Nettoyage des fichiers temporaires (quotidien 04h00) ────
0 4 * * * root find /opt/docavocat/temp -type f -mtime +1 -delete

# ── Renouvellement certificat Let's Encrypt (2x/jour) ───────
0 */12 * * * root certbot renew --quiet --deploy-hook "systemctl reload nginx"

# ── Purge des backups locaux > 30 jours (hebdo, lundi 06h00)─
0 6 * * 1 root find /opt/docavocat/backups -name "*.gpg" -mtime +30 -delete
EOF

chmod 644 "${CRON_FILE}"
echo "✅ Cron installé : ${CRON_FILE}"
echo ""
echo "Vérifier avec : cat ${CRON_FILE}"
echo "Logs backup   : tail -f ${LOG_DIR}/backup.log"
