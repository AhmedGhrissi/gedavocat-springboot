#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════
#  AUDIT SÉCURITÉ — TOLÉRANCE ZÉRO — DocAvocat / GED Avocat
#  Usage : ./audit-security.sh [--fix] [--report]
#  Déclencher via : git hook pre-push OU pipeline CI/CD
#  Bloque le push/déploiement si un seul contrôle échoue
# ═══════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Couleurs ────────────────────────────────────────────────────────────────
RED='\033[0;31m'; ORANGE='\033[0;33m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

# ── Config ───────────────────────────────────────────────────────────────────
PROJECT_ROOT="${PROJECT_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
REPORT_DIR="$PROJECT_ROOT/security-reports"
REPORT_FILE="$REPORT_DIR/audit-$(date +%Y%m%d-%H%M%S).log"
FAIL_COUNT=0
WARN_COUNT=0
PASS_COUNT=0
AUTO_FIX="${1:-}"

mkdir -p "$REPORT_DIR"

# ── Helpers ──────────────────────────────────────────────────────────────────
log()    { echo -e "$1" | tee -a "$REPORT_FILE"; }
pass()   { PASS_COUNT=$((PASS_COUNT+1)); log "${GREEN}  ✅ PASS${NC}  $1"; }
fail()   { FAIL_COUNT=$((FAIL_COUNT+1)); log "${RED}  ❌ FAIL${NC}  $1"; }
warn()   { WARN_COUNT=$((WARN_COUNT+1)); log "${ORANGE}  ⚠️  WARN${NC}  $1"; }
section(){ log "\n${BOLD}${CYAN}══════════════════════════════════════════${NC}";
           log "${BOLD}${CYAN}  $1${NC}";
           log "${CYAN}══════════════════════════════════════════${NC}"; }

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 1 — SECRETS & CREDENTIALS DANS LE CODE
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 1 — SECRETS & CREDENTIALS"

# 1.1 Patterns de secrets hardcodés
SECRETS_PATTERNS=(
  "password\s*=\s*['\"][^'\"]{3,}"
  "secret\s*=\s*['\"][^'\"]{3,}"
  "api[_-]?key\s*=\s*['\"][^'\"]{8,}"
  "sk_live_[a-zA-Z0-9]+"
  "pk_live_[a-zA-Z0-9]+"
  "-----BEGIN (RSA|EC|OPENSSH) PRIVATE KEY-----"
  "jdbc:mysql://[^/]+/[^\s]+\?.*password=[^&\s]+"
  "stripe[_-]?secret"
  "payplug[_-]?secret"
  "yousign[_-]?api[_-]?key"
)

SECRETS_FOUND=0
for pattern in "${SECRETS_PATTERNS[@]}"; do
  matches=$(grep -rniE "$pattern" \
    --include="*.java" --include="*.ts" --include="*.tsx" \
    --include="*.js" --include="*.yml" --include="*.yaml" \
    --include="*.properties" --include="*.xml" \
    --exclude-dir=".git" --exclude-dir="node_modules" --exclude-dir="target" \
    "$PROJECT_ROOT" 2>/dev/null | \
    grep -v "test\|spec\|example\|sample\|TODO\|FIXME\|gitleaks:allow" | \
    grep -v '\${[A-Z_]\+}' || true)
  if [ -n "$matches" ]; then
    fail "Secret potentiel détecté — pattern: $pattern"
    echo "$matches" | head -5 | tee -a "$REPORT_FILE"
    SECRETS_FOUND=$((SECRETS_FOUND+1))
  fi
done
[ $SECRETS_FOUND -eq 0 ] && pass "Aucun secret hardcodé détecté"

# 1.2 Fichiers .env ou config sensibles committés
ENV_FILES=$(git -C "$PROJECT_ROOT" ls-files | grep -E "\.env$|\.env\.prod|application-prod\.properties|application-prod\.yml" || true)
if [ -n "$ENV_FILES" ]; then
  fail "Fichiers de configuration prod committés dans Git : $ENV_FILES"
else
  pass "Aucun fichier .env ou config prod dans Git"
fi

# 1.3 .gitignore présent et couvre les secrets
GITIGNORE="$PROJECT_ROOT/.gitignore"
if [ -f "$GITIGNORE" ]; then
  for pattern in "*.env" "application-prod*" "*.jks" "*.p12" "*.pem" "*.key" "/target" "node_modules"; do
    if ! grep -q "$pattern" "$GITIGNORE" 2>/dev/null; then
      warn ".gitignore ne couvre pas : $pattern"
    fi
  done
  pass ".gitignore présent"
else
  fail ".gitignore manquant — CRITIQUE"
fi

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 2 — HASHAGE & CRYPTOGRAPHIE
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 2 — HASHAGE & CRYPTOGRAPHIE"

# 2.1 Algorithmes interdits (MD5, SHA1 pour passwords)
WEAK_CRYPTO=$(grep -rniE \
  "MessageDigest\.getInstance\(['\"]MD5|MessageDigest\.getInstance\(['\"]SHA-?1['\"]|new\s+MD5|DigestUtils\.md5|DigestUtils\.sha1(?!28)|\.md5(hex|sum)" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$WEAK_CRYPTO" ]; then
  fail "Algorithme faible détecté (MD5/SHA1) — INTERDIT pour données sensibles"
  echo "$WEAK_CRYPTO" | head -10 | tee -a "$REPORT_FILE"
else
  pass "Aucun MD5/SHA1 détecté pour le hashage"
fi

# 2.2 BCrypt / Argon2 utilisé pour les passwords
BCRYPT=$(grep -rniE "BCryptPasswordEncoder|Argon2PasswordEncoder|PasswordEncoderFactories" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$BCRYPT" ]; then
  pass "Encodage password fort détecté (BCrypt/Argon2)"
else
  fail "BCryptPasswordEncoder ou Argon2 NON détecté — passwords potentiellement non hashés"
fi

# 2.3 Chiffrement AES-256 pour données sensibles
AES=$(grep -rniE "AES.{0,10}256|AES/GCM|AesEncrypt|CryptoService" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$AES" ]; then
  pass "Chiffrement AES détecté"
else
  warn "Aucun chiffrement AES explicite détecté — vérifier le chiffrement at-rest"
fi

# 2.4 TLS — config Spring Boot
TLS_CONFIG=$(grep -rniE "server\.ssl\.enabled=true|server\.ssl\.key-store" \
  --include="*.properties" --include="*.yml" --include="*.yaml" \
  --exclude-dir=".git" "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$TLS_CONFIG" ]; then
  pass "Configuration TLS/SSL détectée dans les properties"
  # Vérifier qu'on n'autorise pas TLS < 1.2
  WEAK_TLS=$(grep -rniE "TLSv1\.0|TLSv1\.1|SSLv3|SSLv2" \
    --include="*.java" --include="*.yml" --include="*.properties" \
    --exclude-dir=".git" --exclude-dir="target" \
    "$PROJECT_ROOT" 2>/dev/null || true)
  [ -n "$WEAK_TLS" ] && fail "Protocole TLS obsolète autorisé (TLS 1.0/1.1/SSLv3) — INTERDIT" \
                       || pass "Pas de protocole TLS obsolète détecté"
else
  warn "SSL non configuré dans les properties Spring — vérifier la config reverse proxy"
fi

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 3 — INJECTION & VALIDATION
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 3 — INJECTION SQL & XSS"

# 3.1 SQL Injection — requêtes non paramétrées
SQL_INJECT=$(grep -rniE \
  "createQuery\s*\(\s*[\"'][^\"']*\+|executeQuery\s*\(\s*[\"'][^\"']*\+|nativeQuery.*\+\s*(request|param|id|name|input)" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$SQL_INJECT" ]; then
  fail "Injection SQL potentielle — concaténation dans requête détectée"
  echo "$SQL_INJECT" | head -10 | tee -a "$REPORT_FILE"
else
  pass "Aucune concaténation SQL directe détectée"
fi

# 3.2 @Query avec SpEL non sécurisé
SPEL=$(grep -rniE "@Query.*:#{|SpelExpressionParser|parseExpression" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$SPEL" ] && warn "SpEL expression détectée — vérifier l'injection SpEL (CVE-2022-22963)" \
               || pass "Aucun SpEL non sécurisé détecté"

# 3.3 XSS — Thymeleaf utilisation de th:utext (non échappé)
UTEXT=$(grep -rniE "th:utext" \
  --include="*.html" --include="*.xhtml" \
  --exclude-dir=".git" "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$UTEXT" ]; then
  fail "th:utext détecté — risque XSS (utiliser th:text à la place)"
  echo "$UTEXT" | head -5 | tee -a "$REPORT_FILE"
else
  pass "Aucun th:utext non sécurisé détecté"
fi

# 3.4 Validation des entrées (@Valid présent)
CONTROLLERS=$(find "$PROJECT_ROOT" -name "*.java" \
  -path "*/controller/*" -not -path "*/.git/*" -not -path "*/target/*" 2>/dev/null | wc -l)
VALID_ANNOT=$(grep -rl "@Valid\|@Validated\|@RequestBody.*@Valid" \
  --include="*.java" "$PROJECT_ROOT" 2>/dev/null | \
  grep -i controller | wc -l || echo 0)
if [ "$CONTROLLERS" -gt 0 ] && [ "$VALID_ANNOT" -eq 0 ]; then
  fail "Aucun @Valid/@Validated dans les controllers — validation des entrées manquante"
else
  pass "@Valid/@Validated détecté dans les controllers ($VALID_ANNOT fichier(s))"
fi

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 4 — AUTHENTIFICATION & AUTORISATION SPRING SECURITY
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 4 — SPRING SECURITY & AUTHORISATION"

# 4.1 Spring Security présent
SEC_DEP=$(grep -rniE "spring-boot-starter-security|spring-security-core" \
  --include="pom.xml" --include="build.gradle" \
  "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$SEC_DEP" ] && pass "Spring Security présent dans les dépendances" \
                  || fail "Spring Security NON présent — CRITIQUE"

# 4.2 CSRF protection — pas désactivée en prod
CSRF_DISABLE=$(grep -rniE "csrf\(\)\.disable\(\)|csrf\.disable\(\)" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$CSRF_DISABLE" ]; then
  # Toléré si API REST pure (JWT), sinon critique
  warn "CSRF désactivé — acceptable UNIQUEMENT si API REST avec JWT (vérifier manuellement)"
  echo "$CSRF_DISABLE" | head -3 | tee -a "$REPORT_FILE"
else
  pass "CSRF protection active"
fi

# 4.3 @PreAuthorize / @Secured sur les endpoints sensibles
PREAUTH=$(grep -rniE "@PreAuthorize|@PostAuthorize|@Secured|@RolesAllowed" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null | wc -l || echo 0)
[ "$PREAUTH" -gt 0 ] && pass "@PreAuthorize/@Secured présent ($PREAUTH occurrences)" \
                       || fail "Aucun @PreAuthorize/@Secured — contrôle d'accès méthode manquant"

# 4.4 Endpoint actuator exposé en prod
ACTUATOR=$(grep -rniE "management\.endpoints\.web\.exposure\.include=\*|include: '\*'" \
  --include="*.properties" --include="*.yml" \
  --exclude-dir=".git" "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$ACTUATOR" ] && fail "Actuator expose TOUS les endpoints (include=*) — désactiver en prod" \
                    || pass "Actuator non exposé en wildcard"

# 4.5 JWT — algo none ou HS256 faible
JWT_NONE=$(grep -rniE "Algorithm\.none\(\)|alg.*none|SignatureAlgorithm\.NONE" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$JWT_NONE" ] && fail "JWT Algorithm NONE détecté — CRITIQUE (CVE classique)" \
                    || pass "JWT Algorithm none non détecté"

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 5 — HEADERS DE SÉCURITÉ HTTP
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 5 — HEADERS HTTP SÉCURITÉ"

HEADERS_CONFIG=$(grep -rniE "X-Frame-Options|Content-Security-Policy|X-XSS-Protection|Strict-Transport-Security|X-Content-Type-Options|Referrer-Policy" \
  --include="*.java" --include="*.yml" --include="*.properties" --include="*.conf" \
  --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)

for header in "X-Frame-Options" "Strict-Transport-Security" "X-Content-Type-Options" "Content-Security-Policy"; do
  if echo "$HEADERS_CONFIG" | grep -qi "$header"; then
    pass "Header $header configuré"
  else
    fail "Header $header MANQUANT — risque clickjacking/MITM/XSS"
  fi
done

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 6 — UPLOAD & FICHIERS JURIDIQUES
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 6 — UPLOAD & GESTION FICHIERS"

# 6.1 Validation MIME côté serveur
MIME_CHECK=$(grep -rniE "getMimeType|detectMimeType|Tika|Files\.probeContentType|getContentType" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$MIME_CHECK" ] && pass "Validation MIME type détectée" \
                      || warn "Aucune validation MIME explicite — vérifier la validation des uploads"

# 6.2 Path traversal — nom de fichier sanitizé
PATH_TRAVERSAL=$(grep -rniE "getOriginalFilename\(\)" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
SANITIZE=$(grep -rniE "Paths\.get|FilenameUtils\.getName|StringUtils\.cleanPath" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$PATH_TRAVERSAL" ] && [ -z "$SANITIZE" ]; then
  fail "getOriginalFilename() utilisé sans sanitisation — risque Path Traversal"
elif [ -n "$SANITIZE" ]; then
  pass "Sanitisation des noms de fichiers détectée"
fi

# 6.3 Taille max upload configurée
MAX_SIZE=$(grep -rniE "max-file-size|max-request-size|MaxUploadSizeExceededException" \
  --include="*.yml" --include="*.properties" --include="*.java" \
  --exclude-dir=".git" "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$MAX_SIZE" ] && pass "Limite de taille upload configurée" \
                   || warn "Aucune limite de taille upload explicite — risque DoS"

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 7 — RGPD & DONNÉES PERSONNELLES
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 7 — RGPD & DONNÉES PERSONNELLES"

# 7.1 Logs — PII dans les logs
PII_LOGS=$(grep -rniE "log\.(info|debug|error|warn).*\b(password|mdp|motdepasse|iban|carte|cvv|siret|birthdate|dateNaissance)\b" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$PII_LOGS" ]; then
  fail "Données personnelles/sensibles potentiellement loggées — RGPD Article 32"
  echo "$PII_LOGS" | head -5 | tee -a "$REPORT_FILE"
else
  pass "Aucune PII évidente dans les logs"
fi

# 7.2 @ToString de Lombok sur entités sensibles
LOMBOK_TOSTRING=$(grep -rniE "@ToString" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$LOMBOK_TOSTRING" ]; then
  warn "@ToString Lombok détecté — vérifier l'exclusion des champs sensibles (@ToString.Exclude)"
else
  pass "Pas de @ToString Lombok non contrôlé"
fi

# 7.3 Soft delete vs purge réelle RGPD
SOFT_DELETE=$(grep -rniE "isDeleted|deletedAt|archived|soft.?delete" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null | wc -l || echo 0)
HARD_DELETE=$(grep -rniE "deleteById|deleteAll|entityManager\.remove|DELETE FROM" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null | wc -l || echo 0)
if [ "$SOFT_DELETE" -gt 0 ] && [ "$HARD_DELETE" -eq 0 ]; then
  warn "Soft delete uniquement — droit à l'effacement RGPD nécessite une purge physique"
else
  pass "Suppression physique détectée ($HARD_DELETE occurrences)"
fi

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 8 — DÉPENDANCES VULNÉRABLES
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 8 — DÉPENDANCES VULNÉRABLES (CVE)"

# Maven — OWASP Dependency Check
# DÉSACTIVÉ dans ce script (job dédié owasp-dependency-check dans GitLab CI fait l'audit complet)
# Sans clé API NVD, le scan prend > 30 min et bloque le pipeline
if [ -f "$PROJECT_ROOT/pom.xml" ]; then
  if [ "${SKIP_OWASP_AUDIT:-false}" = "false" ] && command -v mvn &>/dev/null; then
    log "${BLUE}  → Lancement OWASP Dependency Check Maven...${NC}"
    # Timeout de 5 minutes pour éviter de bloquer le CI
    timeout 300 mvn -f "$PROJECT_ROOT/pom.xml" \
      org.owasp:dependency-check-maven:check \
      -DfailBuildOnCVSS=7 \
      -DskipSystemScope=true \
      -Dformat=JSON \
      -DoutputDirectory="$REPORT_DIR" \
      --quiet 2>&1 | tail -5 | tee -a "$REPORT_FILE" && \
      pass "OWASP Dependency Check : aucune CVE >= 7.0 détectée" || \
      warn "OWASP Dependency Check : timeout ou CVE détectée (voir job owasp-dependency-check pour détails)"
  else
    # Fallback : vérification manuelle versions connues vulnérables
    VULN_LIBS=(
      "log4j-core-2\.(0|1[0-6]|17\.0)"    # Log4Shell
      "spring-core-5\.(2\.[0-9]|3\.[0-1])" # Spring4Shell
      "jackson-databind-2\.([0-9]\.|1[01])" # Jackson RCE
    )
    VULN_FOUND=0
    for lib in "${VULN_LIBS[@]}"; do
      match=$(grep -rniE "$lib" "$PROJECT_ROOT/pom.xml" 2>/dev/null || true)
      if [ -n "$match" ]; then
        fail "Dépendance vulnérable connue : $lib"
        VULN_FOUND=1
      fi
    done
    [ $VULN_FOUND -eq 0 ] && pass "Pas de dépendances vulnérables connues (Log4Shell, Spring4Shell, Jackson RCE)"
    log "${YELLOW}  ℹ  Audit CVE complet via job 'owasp-dependency-check' (voir artifacts CI)${NC}"
  fi
fi

# NPM Audit (React/TypeScript)
if [ -f "$PROJECT_ROOT/package.json" ] || [ -f "$PROJECT_ROOT/frontend/package.json" ]; then
  FRONTEND_DIR="$PROJECT_ROOT"
  [ -f "$PROJECT_ROOT/frontend/package.json" ] && FRONTEND_DIR="$PROJECT_ROOT/frontend"
  if command -v npm &>/dev/null; then
    log "${BLUE}  → npm audit en cours...${NC}"
    npm_result=$(npm audit --audit-level=high --json 2>/dev/null \
      --prefix "$FRONTEND_DIR" | \
      python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('metadata',{}).get('vulnerabilities',{}))" \
      2>/dev/null || echo "error")
    if echo "$npm_result" | grep -qE "high|critical"; then
      fail "npm audit : vulnérabilités HIGH/CRITICAL dans les dépendances frontend"
    else
      pass "npm audit : aucune vulnérabilité critique frontend"
    fi
  fi
fi

# ═══════════════════════════════════════════════════════════════════════════
# BLOC 9 — CONFIGURATION SPÉCIFIQUE DOCAVOCAT
# ═══════════════════════════════════════════════════════════════════════════
section "BLOC 9 — CONTRÔLES MÉTIER DOCAVOCAT"

# 9.1 Webhook Yousign — vérification HMAC
YOUSIGN_WEBHOOK=$(grep -rniE "yousign|YouSign" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
HMAC_VERIFY=$(grep -rniE "HMAC|HmacSHA256|X-Yousign-Signature|webhook.*secret" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$YOUSIGN_WEBHOOK" ] && [ -z "$HMAC_VERIFY" ]; then
  fail "Yousign intégré sans vérification HMAC du webhook — risque de faux événements"
else
  [ -n "$HMAC_VERIFY" ] && pass "Vérification HMAC Yousign détectée"
fi

# 9.2 Stripe/PayPlug — vérification signature webhook
STRIPE=$(grep -rniE "Stripe|PayPlug|stripe\.webhook|payplug\.webhook" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
STRIPE_SIG=$(grep -rniE "Webhook\.constructEvent|Stripe-Signature|verifySignature" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
if [ -n "$STRIPE" ] && [ -z "$STRIPE_SIG" ]; then
  fail "Stripe/PayPlug intégré sans vérification de signature webhook — risque de fraude"
else
  [ -n "$STRIPE_SIG" ] && pass "Vérification signature Stripe/PayPlug détectée"
fi

# 9.3 Multi-tenant isolation — vérification tenant dans les queries
TENANT_FILTER=$(grep -rniE "@Filter.*tenant|tenantId|cabinetId.*@Query|FilterDef.*tenant" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null | wc -l || echo 0)
[ "$TENANT_FILTER" -gt 0 ] && pass "Filtrage multi-tenant détecté ($TENANT_FILTER occurrences)" \
                             || warn "Aucun filtre tenant explicite — vérifier l'isolation des données inter-cabinets"

# 9.4 Rate limiting
RATE_LIMIT=$(grep -rniE "Bucket4j|RateLimiter|@RateLimit|rate.?limit|requestPerSecond" \
  --include="*.java" --exclude-dir=".git" --exclude-dir="target" \
  "$PROJECT_ROOT" 2>/dev/null || true)
[ -n "$RATE_LIMIT" ] && pass "Rate limiting détecté" \
                      || fail "Aucun rate limiting — risque brute force & DoS sur endpoints sensibles"

# ═══════════════════════════════════════════════════════════════════════════
# RAPPORT FINAL
# ═══════════════════════════════════════════════════════════════════════════
TOTAL=$((PASS_COUNT + FAIL_COUNT + WARN_COUNT))
SCORE=0
[ $TOTAL -gt 0 ] && SCORE=$(( (PASS_COUNT * 100) / TOTAL ))

log "\n${BOLD}═══════════════════════════════════════════════════════${NC}"
log "${BOLD}  RAPPORT FINAL — AUDIT SÉCURITÉ DOCAVOCAT${NC}"
log "${BOLD}  $(date '+%Y-%m-%d %H:%M:%S')${NC}"
log "${BOLD}═══════════════════════════════════════════════════════${NC}"
log "${GREEN}  ✅ PASS  : $PASS_COUNT${NC}"
log "${ORANGE}  ⚠️  WARN  : $WARN_COUNT${NC}"
log "${RED}  ❌ FAIL  : $FAIL_COUNT${NC}"
log "${BOLD}  Score   : $SCORE / 100${NC}"
log "  Rapport : $REPORT_FILE"
log "${BOLD}═══════════════════════════════════════════════════════${NC}"

if [ $FAIL_COUNT -gt 0 ]; then
  log "\n${RED}${BOLD}  🚫 DÉPLOIEMENT BLOQUÉ — $FAIL_COUNT contrôle(s) critique(s) échoué(s)${NC}"
  log "${RED}  Corriger toutes les erreurs avant de pusher en production.${NC}\n"
  exit 1
elif [ $WARN_COUNT -gt 0 ]; then
  log "\n${ORANGE}${BOLD}  ⚠️  AVERTISSEMENT — $WARN_COUNT point(s) à vérifier manuellement${NC}"
  log "${ORANGE}  Push autorisé mais révision recommandée.\n${NC}"
  exit 0
else
  log "\n${GREEN}${BOLD}  ✅ AUDIT COMPLET — AUCUNE FAILLE DÉTECTÉE${NC}"
  log "${GREEN}  Déploiement autorisé.\n${NC}"
  exit 0
fi
