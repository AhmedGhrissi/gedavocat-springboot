#!/usr/bin/env bash
# ============================================================
# DocAvocat — Rotation des secrets
# Usage: ./scripts/rotate-secrets.sh [jwt|mfa|grafana|all]
#
# Rotation automatique des secrets sensibles.
# Redémarre uniquement les services concernés.
#
# Planification recommandée (crontab) :
#   0 2 1 */3 * /opt/docavocat/scripts/rotate-secrets.sh all >> /var/log/docavocat-rotation.log 2>&1
# ============================================================
set -euo pipefail

ENV_FILE="${ENV_FILE:-/opt/docavocat/.env}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/docavocat}"
BACKUP_DIR="/opt/docavocat/backups/secrets"
LOG_PREFIX="[ROTATE]"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $LOG_PREFIX $*"; }
die() { log "ERREUR: $*"; exit 1; }

# Vérifications
[ -f "$ENV_FILE" ] || die "Fichier .env introuvable: $ENV_FILE"
command -v openssl >/dev/null || die "openssl requis"

# Backup du .env avant modification
mkdir -p "$BACKUP_DIR"
cp "$ENV_FILE" "$BACKUP_DIR/.env.$(date '+%Y%m%d_%H%M%S')"

# Rotation d'une variable dans le .env
rotate_var() {
    local var_name="$1"
    local new_value="$2"
    if grep -q "^${var_name}=" "$ENV_FILE"; then
        sed -i "s|^${var_name}=.*|${var_name}=${new_value}|" "$ENV_FILE"
    else
        echo "${var_name}=${new_value}" >> "$ENV_FILE"
    fi
    log "$var_name rotated"
}

rotate_jwt() {
    log "--- Rotation JWT_SECRET ---"
    NEW_JWT=$(openssl rand -base64 64 | tr -d '\n')
    rotate_var "JWT_SECRET" "$NEW_JWT"
    log "JWT_SECRET rotaté (64 bytes base64)"
    log "NOTE: les sessions JWT existantes seront invalidées au prochain restart"
}

rotate_mfa() {
    log "--- Rotation MFA_ENCRYPTION_KEY ---"
    log "ATTENTION: la rotation MFA invalide les secrets TOTP existants en BDD"
    log "Les utilisateurs MFA devront reconfigurer leur 2FA"
    NEW_MFA=$(openssl rand -base64 32 | tr -d '\n')
    rotate_var "MFA_ENCRYPTION_KEY" "$NEW_MFA"
    log "MFA_ENCRYPTION_KEY rotaté (32 bytes = AES-256)"
}

rotate_grafana() {
    log "--- Rotation GRAFANA_SECRET_KEY ---"
    NEW_GF=$(openssl rand -base64 32 | tr -d '\n')
    rotate_var "GRAFANA_SECRET_KEY" "$NEW_GF"
    log "GRAFANA_SECRET_KEY rotaté"
}

restart_services() {
    log "--- Redémarrage des services ---"
    cd "$COMPOSE_DIR"
    case "$1" in
        jwt|mfa)
            docker compose restart app
            log "Service app redémarré"
            ;;
        grafana)
            docker compose restart grafana
            log "Service grafana redémarré"
            ;;
        all)
            docker compose restart app grafana
            log "Services app + grafana redémarrés"
            ;;
    esac
    log "Attente healthcheck (30s)..."
    sleep 30
    docker compose ps --format "table {{.Name}}\t{{.Status}}" | grep -E "(app|grafana)"
}

# Nettoyage vieux backups (>180 jours)
cleanup_backups() {
    find "$BACKUP_DIR" -name ".env.*" -mtime +180 -delete 2>/dev/null || true
}

# ── Main ──
TARGET="${1:-all}"
log "=== Début rotation secrets: $TARGET ==="

case "$TARGET" in
    jwt)
        rotate_jwt
        restart_services jwt
        ;;
    mfa)
        echo ""
        log "⚠  ROTATION MFA: tous les utilisateurs avec 2FA activé"
        log "   devront reconfigurer leur application TOTP."
        read -r -p "   Continuer ? (oui/non) : " confirm
        [ "$confirm" = "oui" ] || die "Annulé par l'utilisateur"
        rotate_mfa
        restart_services mfa
        ;;
    grafana)
        rotate_grafana
        restart_services grafana
        ;;
    all)
        rotate_jwt
        rotate_grafana
        # MFA rotation exclue du "all" car destructrice — doit être manuelle
        log "NOTE: MFA_ENCRYPTION_KEY non rotaté (destructif). Utiliser: $0 mfa"
        restart_services all
        ;;
    *)
        echo "Usage: $0 [jwt|mfa|grafana|all]"
        echo "  jwt     — Rotation JWT_SECRET (invalide sessions actives)"
        echo "  mfa     — Rotation MFA_ENCRYPTION_KEY (DESTRUCTIF: invalide tous les TOTP)"
        echo "  grafana — Rotation GRAFANA_SECRET_KEY"
        echo "  all     — jwt + grafana (MFA exclu car destructif)"
        exit 1
        ;;
esac

cleanup_backups
log "=== Rotation terminée ==="
