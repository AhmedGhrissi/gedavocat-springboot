#!/bin/bash
# ============================================================
# DocAvocat — Audit sécurité automatisé (SAST, secrets, RGPD)
# Usage : ./audit-security.sh
# ============================================================
set -euo pipefail

REPORT_DIR="security-reports"
mkdir -p "$REPORT_DIR"

# 1. Détection de secrets (git-secrets ou truffleHog)
if command -v trufflehog &>/dev/null; then
  trufflehog filesystem --directory . --json > "$REPORT_DIR/trufflehog.json" || true
fi

# 2. Analyse SAST (Semgrep)
if command -v semgrep &>/dev/null; then
  semgrep scan --config auto --config p/java --config p/owasp-top-ten --config p/secrets --json --output "$REPORT_DIR/semgrep-report.json" . || true
fi

# 3. Vérification RGPD (fichiers sensibles, mentions DPO, etc.)
grep -i -r 'dpo\|rgpd\|cnil\|retention\|effacement' . > "$REPORT_DIR/rgpd-grep.txt" || true

# 4. Résumé
ls -lh "$REPORT_DIR"
echo "\nAudit sécurité terminé. Rapports générés dans $REPORT_DIR."
