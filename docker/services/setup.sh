#!/bin/bash
# ============================================================
# DocAvocat — Provisioning initial du serveur CX23 (services)
#
# À exécuter en root sur le nouveau serveur :
#   ssh root@188.245.158.191 'bash -s' < setup.sh
#
# Ce script :
#   1. Crée l'utilisateur deploy
#   2. Configure ufw (firewall)
#   3. Installe Docker + Docker Compose
#   4. Crée l'arborescence des données
# ============================================================

set -euo pipefail

log() { echo "[$(date '+%H:%M:%S')] $*"; }

# ── 1. Utilisateur deploy ──────────────────────────────────
log "Création de l'utilisateur deploy..."
if ! id -u deploy &>/dev/null; then
  adduser --disabled-password --gecos "" deploy
fi
usermod -aG sudo deploy

# Copier les clés SSH autorisées depuis root vers deploy
mkdir -p /home/deploy/.ssh
cp /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys

# ── 2. Sécurisation SSH ────────────────────────────────────
log "Sécurisation SSH..."
sed -i 's/^PermitRootLogin yes/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/^#PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/^PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
sed -i 's/^#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart sshd

# ── 3. Firewall ufw ────────────────────────────────────────
log "Configuration firewall..."
apt-get install -y ufw
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # HTTP (nginx si besoin)
ufw allow 443/tcp   # HTTPS
# MinIO API (uniquement depuis le réseau privé — à restreindre après config réseau privé Hetzner)
ufw allow 9000/tcp
ufw --force enable
log "Firewall activé"

# ── 4. Docker ──────────────────────────────────────────────
log "Installation Docker..."
apt-get update -qq
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
usermod -aG docker deploy
systemctl enable --now docker
log "Docker $(docker --version) installé"

# ── 5. Arborescence des données ────────────────────────────
log "Création de l'arborescence..."
mkdir -p /opt/services/{minio/data,redis/data,config}
chown -R deploy:deploy /opt/services
log "Arborescence /opt/services créée"

# ── 6. Répertoire de déploiement ───────────────────────────
mkdir -p /opt/docavocat-services
chown deploy:deploy /opt/docavocat-services

log "============================================"
log "Provisioning terminé !"
log "Connecte-toi maintenant avec : ssh deploy@188.245.158.191"
log "============================================"
