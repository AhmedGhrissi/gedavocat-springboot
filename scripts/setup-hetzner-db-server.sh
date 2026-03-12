#!/bin/bash
# ============================================================
# setup-hetzner-db-server.sh — Installation MySQL dédié sur Hetzner
#
# A exécuter sur la VM Hetzner DB (Ubuntu 24.04 LTS recommandé).
# Cette VM est reliée au VPS App par un vSwitch privé Hetzner.
#
# Architecture cible :
#   VPS App (10.0.0.1) ──vSwitch privé──▶ VM DB (10.0.0.2:3306)
#   VM DB réplique vers StorageBox via backup-encrypted.sh
#
# Prérequis :
#   1. Créer un vSwitch dans Hetzner Cloud Console
#   2. Attacher les 2 serveurs au vSwitch
#   3. Configurer les IPs privées (ex: 10.0.0.1 app, 10.0.0.2 db)
#   4. Lancer ce script en root sur la VM DB
#
# Usage :
#   scp scripts/setup-hetzner-db-server.sh root@<DB_SERVER_IP>:/tmp/
#   ssh root@<DB_SERVER_IP> "bash /tmp/setup-hetzner-db-server.sh"
#
# Variables à adapter :
#   APP_VSWITCH_IP  — IP privée du VPS App sur le vSwitch
#   DB_PASSWORD     — Mot de passe MySQL pour l'user gedavocat
#   REPL_PASSWORD   — Mot de passe pour l'user de réplication
# ============================================================
set -euo pipefail

# ── Configuration ────────────────────────────────────────────
APP_VSWITCH_IP="${APP_VSWITCH_IP:-10.0.0.1}"
DB_IP="${DB_IP:-10.0.0.2}"
DB_PASSWORD="${DB_PASSWORD:?ERREUR: DB_PASSWORD requis}"
REPL_PASSWORD="${REPL_PASSWORD:?ERREUR: REPL_PASSWORD requis}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?ERREUR: MYSQL_ROOT_PASSWORD requis}"

echo "=== Installation MySQL 8.0 dédié — Hetzner DB Server ==="
echo "  App vSwitch IP : $APP_VSWITCH_IP"
echo "  DB vSwitch IP  : $DB_IP"

# ── 1. Installation MySQL 8.0 ────────────────────────────────
echo ""
echo "=== [1/7] Installation MySQL 8.0 ==="
export DEBIAN_FRONTEND=noninteractive
apt update -q
apt install -y mysql-server ufw fail2ban

# ── 2. Configuration MySQL sécurisée ─────────────────────────
echo ""
echo "=== [2/7] Configuration MySQL sécurisée ==="
cat > /etc/mysql/mysql.conf.d/docavocat.cnf << EOF
[mysqld]
# ── Réseau : écoute uniquement sur le vSwitch privé ──────────
bind-address            = $DB_IP
# Port standard MySQL
port                    = 3306

# ── TDE (Transparent Data Encryption) AES-256 ────────────────
early-plugin-load       = keyring_file.so
keyring_file_data       = /var/lib/mysql-keyring/keyring
default_table_encryption = ON
table_encryption_privilege_check = ON

# ── Sécurité ─────────────────────────────────────────────────
require_secure_transport = ON
local_infile            = OFF
skip-symbolic-links
skip-name-resolve

# ── Performance (adapté pour CAX11 : 4 vCPU, 8GB RAM) ────────
innodb_buffer_pool_size = 2G
innodb_buffer_pool_instances = 2
innodb_log_file_size    = 512M
max_connections         = 300
thread_cache_size       = 16
table_open_cache        = 4000

# ── Charset ──────────────────────────────────────────────────
character-set-server    = utf8mb4
collation-server        = utf8mb4_unicode_ci
default-time-zone       = Europe/Paris

# ── Binlog pour réplication (source → replica) ────────────────
server-id               = 1
log_bin                 = /var/log/mysql/mysql-bin.log
binlog_format           = ROW
expire_logs_days        = 7
max_binlog_size         = 100M
binlog_do_db            = gedavocat

# ── Slow query log ────────────────────────────────────────────
slow_query_log          = ON
slow_query_log_file     = /var/log/mysql/slow.log
long_query_time         = 2

# ── Sauvegardes sûres ─────────────────────────────────────────
innodb_flush_log_at_trx_commit = 1
sync_binlog             = 1
EOF

# Créer le répertoire keyring
mkdir -p /var/lib/mysql-keyring
chown mysql:mysql /var/lib/mysql-keyring
chmod 750 /var/lib/mysql-keyring

systemctl restart mysql
echo "MySQL redémarré avec TDE activé"

# ── 3. Sécurisation root MySQL ───────────────────────────────
echo ""
echo "=== [3/7] Sécurisation root MySQL ==="
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '${MYSQL_ROOT_PASSWORD}';"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "DELETE FROM mysql.user WHERE User='';"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost', '127.0.0.1', '::1');"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "DROP DATABASE IF EXISTS test;"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"

# ── 4. Créer la base et l'utilisateur applicatif ─────────────
echo ""
echo "=== [4/7] Création base gedavocat + user applicatif ==="
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" << SQL
CREATE DATABASE IF NOT EXISTS gedavocat
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- User applicatif : accessible uniquement depuis le VPS App via vSwitch
CREATE USER IF NOT EXISTS 'gedavocat'@'${APP_VSWITCH_IP}'
  IDENTIFIED WITH caching_sha2_password BY '${DB_PASSWORD}'
  REQUIRE SSL;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON gedavocat.*
  TO 'gedavocat'@'${APP_VSWITCH_IP}';

-- User de réplication (pour replica éventuel)
CREATE USER IF NOT EXISTS 'replicator'@'%'
  IDENTIFIED WITH caching_sha2_password BY '${REPL_PASSWORD}'
  REQUIRE SSL;

GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'%';

FLUSH PRIVILEGES;
SQL
echo "Base gedavocat créée, user applicatif configuré"

# ── 5. Chiffrement de la base (TDE) ─────────────────────────
echo ""
echo "=== [5/7] Activation TDE sur la base gedavocat ==="
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "ALTER DATABASE gedavocat ENCRYPTION='Y';"
echo "Base chiffrée AES-256 (TDE)"

# ── 6. Firewall : uniquement vSwitch + SSH ───────────────────
echo ""
echo "=== [6/7] Configuration firewall (ufw) ==="
ufw --force reset
ufw default deny incoming
ufw default allow outgoing

# SSH depuis n'importe où (à restreindre à votre IP admin si possible)
ufw allow 22/tcp comment "SSH admin"

# MySQL uniquement depuis le VPS App via vSwitch
ufw allow from "${APP_VSWITCH_IP}" to any port 3306 comment "MySQL vSwitch App"

# Activer
ufw --force enable
ufw status verbose
echo "Firewall configuré : MySQL accessible uniquement depuis ${APP_VSWITCH_IP}"

# ── 7. Fail2ban pour protéger SSH ─────────────────────────────
echo ""
echo "=== [7/7] Configuration fail2ban ==="
cat > /etc/fail2ban/jail.local << EOF
[DEFAULT]
bantime  = 3600
findtime = 600
maxretry = 5

[sshd]
enabled = true
port    = 22
logpath = /var/log/auth.log
EOF
systemctl enable --now fail2ban

# ── Résumé ───────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  Hetzner DB Server configuré avec succès"
echo "============================================================"
echo ""
echo "Prochaines étapes :"
echo ""
echo "1. Dans .env sur le VPS App, définir :"
echo "   DB_HOST=${DB_IP}"
echo "   DB_PORT=3306"
echo "   DB_USERNAME=gedavocat"
echo ""
echo "2. Importer le dump initial depuis le VPS App :"
echo "   mysqldump -u root -p gedavocat | mysql -h ${DB_IP} -u gedavocat -p gedavocat"
echo ""
echo "3. Redémarrer l'app avec :"
echo "   docker compose up -d --no-deps app"
echo ""
echo "4. Configurer le backup sur cette VM :"
echo "   cp scripts/backup-encrypted.sh /opt/backup.sh"
echo "   echo '0 3 * * * /opt/backup.sh' | crontab -"
echo ""
echo "5. Vérifier la réplication (si replica configuré) :"
echo "   mysql -u root -p -e 'SHOW MASTER STATUS\\G'"
echo "============================================================"
