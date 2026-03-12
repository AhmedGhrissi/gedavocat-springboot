#!/bin/bash
# ============================================================
# update-cloudflare-ips.sh — Mise à jour automatique des IPs Cloudflare
#
# Télécharge les plages IP officielles depuis Cloudflare et régénère
# /etc/nginx/conf.d/cloudflare-ips.conf puis recharge nginx.
#
# Usage : lancer en cron hebdomadaire sur le VPS Hetzner
#   0 4 * * 1 /opt/docavocat/scripts/update-cloudflare-ips.sh >> /opt/docavocat/logs/cloudflare-ips.log 2>&1
#
# Première installation : copier ce script sur le serveur puis :
#   chmod +x /opt/docavocat/scripts/update-cloudflare-ips.sh
#   /opt/docavocat/scripts/update-cloudflare-ips.sh
# ============================================================
set -euo pipefail

OUTPUT="/etc/nginx/conf.d/cloudflare-ips.conf"
TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")

echo "[$TIMESTAMP] Mise à jour des IPs Cloudflare..."

# Télécharger les plages IP officielles
IPV4=$(curl -sf https://www.cloudflare.com/ips-v4 || echo "")
IPV6=$(curl -sf https://www.cloudflare.com/ips-v6 || echo "")

if [ -z "$IPV4" ] || [ -z "$IPV6" ]; then
    echo "[$TIMESTAMP] ERREUR: Impossible de joindre cloudflare.com — liste existante conservée"
    exit 1
fi

# Générer le fichier nginx
cat > "$OUTPUT" << NGINX_CONF
# ============================================================
# cloudflare-ips.conf — IPs officielles Cloudflare
# Généré automatiquement le $TIMESTAMP
# NE PAS EDITER MANUELLEMENT — voir update-cloudflare-ips.sh
# ============================================================

real_ip_header     CF-Connecting-IP;
real_ip_recursive  on;

# IPv4 Cloudflare
$(echo "$IPV4" | while read -r ip; do echo "set_real_ip_from $ip;"; done)

# IPv6 Cloudflare
$(echo "$IPV6" | while read -r ip; do echo "set_real_ip_from $ip;"; done)
NGINX_CONF

echo "[$TIMESTAMP] Fichier généré : $OUTPUT"

# Tester et recharger nginx
if nginx -t 2>/dev/null; then
    nginx -s reload
    echo "[$TIMESTAMP] nginx rechargé avec succès"
else
    echo "[$TIMESTAMP] ERREUR nginx -t — rollback vers l'ancienne liste"
    git -C /opt/docavocat checkout "$OUTPUT" 2>/dev/null || true
    exit 1
fi

echo "[$TIMESTAMP] Mise à jour terminée"
