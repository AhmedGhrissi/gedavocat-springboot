#!/usr/bin/env bash
# =============================================================================
# migrate-to-minio.sh — Migration des fichiers legacy vers MinIO
#
# Ce script migre les documents stockés localement (paths commençant par /)
# vers le bucket MinIO docavocat-documents et met à jour la base de données.
#
# Pré-requis :
#   - mc (MinIO Client) installé sur la machine
#   - mysql-client installé
#   - Accès en lecture à /opt/docavocat/uploads
#   - Variables d'environnement définies (ou arguments)
#
# Usage :
#   export MINIO_ROOT_USER=gedavocat-admin
#   export MINIO_ROOT_PASSWORD=<password>
#   export MYSQL_PASSWORD=<password>
#   bash docker/scripts/migrate-to-minio.sh
#
# Pour un dry-run (aucune modification) :
#   DRY_RUN=true bash docker/scripts/migrate-to-minio.sh
# =============================================================================
set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://10.0.0.3:9000}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:?Variable MINIO_ROOT_USER requise}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:?Variable MINIO_ROOT_PASSWORD requise}"
BUCKET="${BUCKET:-docavocat-documents}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3307}"
DB_USER="${DB_USER:-gedavocat}"
DB_PASSWORD="${MYSQL_PASSWORD:?Variable MYSQL_PASSWORD requise}"
DB_NAME="${DB_NAME:-gedavocat}"

DRY_RUN="${DRY_RUN:-false}"

# ── Couleurs ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO ]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN ]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── Dry-run avertissement ────────────────────────────────────────────────────
if [[ "$DRY_RUN" == "true" ]]; then
    warn "=== MODE DRY-RUN : aucune modification ne sera effectuée ==="
fi

# ── Vérifier les prérequis ───────────────────────────────────────────────────
if ! command -v mc &>/dev/null; then
    err "mc (MinIO Client) non trouvé. Installez-le : https://min.io/docs/minio/linux/reference/minio-mc.html"
    exit 1
fi
if ! command -v mysql &>/dev/null; then
    err "mysql client non trouvé. Installez-le : apt-get install -y mysql-client"
    exit 1
fi

# ── Configurer mc ────────────────────────────────────────────────────────────
log "Configuration mc → ${MINIO_ENDPOINT}"
mc alias set docavocat "${MINIO_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" --quiet

# ── Vérifier l'accès MinIO ───────────────────────────────────────────────────
if ! mc ls "docavocat/${BUCKET}" &>/dev/null; then
    err "Impossible d'accéder au bucket ${BUCKET} sur ${MINIO_ENDPOINT}"
    exit 1
fi
log "Bucket ${BUCKET} accessible"

# ── Requête DB : documents avec path legacy (commence par /) ─────────────────
MYSQL_CMD="mysql -h ${DB_HOST} -P ${DB_PORT} -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} --batch --skip-column-names"

log "Interrogation de la base de données..."
DOCS=$($MYSQL_CMD -e "SELECT id, path FROM documents WHERE path LIKE '/%' AND deleted_at IS NULL;")

if [[ -z "$DOCS" ]]; then
    log "Aucun document legacy trouvé. Migration terminée."
    exit 0
fi

TOTAL=$(echo "$DOCS" | wc -l)
log "Nombre de documents à migrer : ${TOTAL}"

# ── Migration ────────────────────────────────────────────────────────────────
SUCCESS=0
SKIP=0
ERROR=0

while IFS=$'\t' read -r DOC_ID DOC_PATH; do
    [[ -z "$DOC_ID" ]] && continue

    # Dériver la clé MinIO depuis le nom de fichier
    FILENAME=$(basename "$DOC_PATH")
    MINIO_KEY="${FILENAME}"

    # Vérifier que le fichier source existe
    if [[ ! -f "$DOC_PATH" ]]; then
        warn "[${DOC_ID}] Fichier source introuvable : ${DOC_PATH} (ignoré)"
        ((ERROR++)) || true
        continue
    fi

    # Vérifier si déjà dans MinIO
    if mc stat "docavocat/${BUCKET}/${MINIO_KEY}" &>/dev/null; then
        warn "[${DOC_ID}] Déjà dans MinIO : ${MINIO_KEY} (mise à jour DB uniquement)"
    else
        if [[ "$DRY_RUN" == "true" ]]; then
            log "[DRY-RUN] Uploadrait : ${DOC_PATH} → ${BUCKET}/${MINIO_KEY}"
        else
            log "[${DOC_ID}] Upload : ${DOC_PATH} → ${BUCKET}/${MINIO_KEY}"
            if ! mc cp "${DOC_PATH}" "docavocat/${BUCKET}/${MINIO_KEY}" --quiet; then
                err "[${DOC_ID}] Échec upload : ${DOC_PATH}"
                ((ERROR++)) || true
                continue
            fi
        fi
    fi

    # Mis à jour de la base de données
    if [[ "$DRY_RUN" == "true" ]]; then
        log "[DRY-RUN] Mettrait à jour DB : id=${DOC_ID} path=${DOC_PATH} → ${MINIO_KEY}"
    else
        $MYSQL_CMD -e "UPDATE documents SET path='${MINIO_KEY}' WHERE id='${DOC_ID}';"
        log "[${DOC_ID}] DB mis à jour : ${MINIO_KEY}"
    fi

    ((SUCCESS++)) || true
done <<< "$DOCS"

# ── Rapport ───────────────────────────────────────────────────────────────────
echo ""
log "══════════════════════════════════════"
log " Migration terminée"
log "  Succès  : ${SUCCESS}/${TOTAL}"
[[ $SKIP   -gt 0 ]] && warn "  Ignorés : ${SKIP}"
[[ $ERROR  -gt 0 ]] && err  "  Erreurs : ${ERROR}"
log "══════════════════════════════════════"

if [[ $ERROR -gt 0 ]]; then
    exit 1
fi
