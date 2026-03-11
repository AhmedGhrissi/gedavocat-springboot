#!/usr/bin/env bash
# ============================================================
# DocAvocat — Installation Fail2ban
# Protège contre les attaques brute-force au niveau OS.
# Complète le rate limiting applicatif (RateLimitingFilter).
#
# Usage: sudo ./scripts/install-fail2ban.sh
# ============================================================
set -euo pipefail

log() { echo "[FAIL2BAN] $*"; }

# Vérification root
[ "$(id -u)" -eq 0 ] || { echo "Erreur: exécuter avec sudo"; exit 1; }

log "=== Installation fail2ban ==="
apt-get update -qq
apt-get install -yqq fail2ban

log "=== Configuration jail DocAvocat ==="

# ── Jail SSH (hardened) ──
cat > /etc/fail2ban/jail.d/ssh-hardened.conf << 'EOF'
[sshd]
enabled  = true
port     = ssh
filter   = sshd
logpath  = /var/log/auth.log
maxretry = 3
bantime  = 3600
findtime = 600
EOF

# ── Jail Nginx (requêtes suspectes) ──
cat > /etc/fail2ban/jail.d/nginx-badbots.conf << 'EOF'
[nginx-badbots]
enabled  = true
port     = http,https
filter   = nginx-badbots
logpath  = /var/log/nginx/access.log
maxretry = 2
bantime  = 86400
findtime = 3600
EOF

# ── Jail Nginx (tentatives d'accès interdit) ──
cat > /etc/fail2ban/jail.d/nginx-forbidden.conf << 'EOF'
[nginx-forbidden]
enabled  = true
port     = http,https
filter   = nginx-forbidden
logpath  = /var/log/nginx/error.log
maxretry = 5
bantime  = 3600
findtime = 600
EOF

# ── Filtre custom : login failures DocAvocat (via Docker logs) ──
cat > /etc/fail2ban/filter.d/docavocat-auth.conf << 'EOF'
# Détection des échecs de connexion dans les logs Docker du conteneur app
[Definition]
failregex = ^.*Authentication failure for .* from <HOST>.*$
            ^.*Failed login attempt for .* from <HOST>.*$
            ^.*SEC-LOCKOUT.*IP=<HOST>.*$
            ^.*RateLimitingFilter.*blocked.*<HOST>.*$
ignoreregex =
EOF

cat > /etc/fail2ban/jail.d/docavocat-auth.conf << 'EOF'
[docavocat-auth]
enabled  = true
port     = http,https
filter   = docavocat-auth
# Logs Docker du conteneur app
logpath  = /var/lib/docker/containers/*docavocat-app*/*-json.log
maxretry = 10
bantime  = 1800
findtime = 600
EOF

# ── Filtre Nginx : scan de vulnérabilités ──
cat > /etc/fail2ban/filter.d/nginx-scanner.conf << 'EOF'
# Détection des scans automatisés (.env, .git, wp-admin, phpMyAdmin, etc.)
[Definition]
failregex = ^<HOST> -.*"(GET|POST|HEAD).*(\.env|\.git|wp-admin|wp-login|phpMyAdmin|phpmyadmin|\.php|cgi-bin|admin\.php|shell|eval|/actuator).*" (403|404|444)
ignoreregex =
EOF

cat > /etc/fail2ban/jail.d/nginx-scanner.conf << 'EOF'
[nginx-scanner]
enabled  = true
port     = http,https
filter   = nginx-scanner
logpath  = /var/log/nginx/access.log
maxretry = 3
bantime  = 86400
findtime = 3600
EOF

log "=== Activation et démarrage ==="
systemctl enable fail2ban
systemctl restart fail2ban

log "=== Status ==="
fail2ban-client status
echo ""
fail2ban-client status sshd 2>/dev/null || true
fail2ban-client status nginx-scanner 2>/dev/null || true
fail2ban-client status docavocat-auth 2>/dev/null || true

log "=== Installation terminée ==="
log "Commandes utiles :"
log "  fail2ban-client status              — voir les jails actifs"
log "  fail2ban-client status sshd         — voir les bans SSH"
log "  fail2ban-client set sshd unbanip IP — débannir une IP"
log "  fail2ban-client reload              — recharger la config"
