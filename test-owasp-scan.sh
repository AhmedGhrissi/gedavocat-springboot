#!/bin/bash
# Test rapide OWASP Dependency Check avec clé NVD API
# Usage : ./test-owasp-scan.sh

set -e

echo "============================================="
echo "  OWASP Dependency Check - Test Rapide"
echo "============================================="
echo ""

# Charger la clé depuis .env.local
if [ -f ".env.local" ]; then
    echo "[OK] Fichier .env.local trouvé"
    export $(grep -v '^#' .env.local | grep NVD_API_KEY | xargs)
    if [ -n "$NVD_API_KEY" ]; then
        echo "[OK] NVD_API_KEY chargée : ${NVD_API_KEY:0:8}..."
    fi
else
    echo "[WARN] Fichier .env.local introuvable - scan sera lent (30 min)"
fi

# Vérifier Maven
if ! command -v mvn &> /dev/null; then
    echo "[ERROR] Maven non installé - installer depuis https://maven.apache.org"
    exit 1
fi

echo ""
echo "Lancement du scan OWASP Dependency Check..."
echo "Temps estimé : 2-3 minutes (avec clé API) ou 30+ minutes (sans clé)"
echo ""

# Lancer le scan
START=$(date +%s)
mvn dependency-check:check -DskipTests -Dformat=HTML -DoutputDirectory=target/security-reports

END=$(date +%s)
DURATION=$((END - START))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo ""
echo "============================================="
echo "Scan terminé en ${MINUTES} min ${SECONDS} sec"
echo "Rapport : target/security-reports/dependency-check-report.html"
echo "============================================="

# Proposer d'ouvrir le rapport
if [ -f "target/security-reports/dependency-check-report.html" ]; then
    echo ""
    read -p "Ouvrir le rapport dans le navigateur ? (o/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Oo]$ ]]; then
        if command -v xdg-open &> /dev/null; then
            xdg-open target/security-reports/dependency-check-report.html
        elif command -v open &> /dev/null; then
            open target/security-reports/dependency-check-report.html
        else
            echo "Impossible d'ouvrir automatiquement. Ouvrir manuellement : target/security-reports/dependency-check-report.html"
        fi
    fi
fi
