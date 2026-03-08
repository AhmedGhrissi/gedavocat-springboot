#!/bin/bash
# ===================================================================
# Script de correction ENV_PROD — Ajout variables RGPD manquantes
# ===================================================================
set -e

SERVER_HOST="${SERVER_HOST:-docavocat.fr}"
REMOTE_DIR="/etc/docavocat"
ENV_FILE="$REMOTE_DIR/.env"

echo "========================================="
echo "FIX ENV PROD : Variables RGPD manquantes"
echo "========================================="

# Vérifier les variables DPO
echo "
Vérification des variables DPO dans .env..."
ssh root@$SERVER_HOST "
  if ! grep -q '^DPO_NAME=' $ENV_FILE 2>/dev/null; then
    echo '❌ DPO_NAME manquant — ajout avec valeur par défaut'
    echo 'DPO_NAME=À Configurer' >> $ENV_FILE
  else
    echo '✅ DPO_NAME présent'
  fi

  if ! grep -q '^DPO_EMAIL=' $ENV_FILE 2>/dev/null; then
    echo '❌ DPO_EMAIL manquant — ajout avec valeur par défaut'
    echo 'DPO_EMAIL=dpo@docavocat.fr' >> $ENV_FILE
  else
    echo '✅ DPO_EMAIL présent'
  fi

  if ! grep -q '^DPO_PHONE=' $ENV_FILE 2>/dev/null; then
    echo '⚠️ DPO_PHONE manquant — ajout optionnel vide'
    echo 'DPO_PHONE=' >> $ENV_FILE
  else
    echo '✅ DPO_PHONE présent'
  fi

  if ! grep -q '^DPO_ADDRESS=' $ENV_FILE 2>/dev/null; then
    echo '⚠️ DPO_ADDRESS manquant — ajout optionnel vide'
    echo 'DPO_ADDRESS=' >> $ENV_FILE
  else
    echo '✅ DPO_ADDRESS présent'
  fi

  if ! grep -q '^DPO_CERTIFICATION=' $ENV_FILE 2>/dev/null; then
    echo '⚠️ DPO_CERTIFICATION manquant — ajout optionnel vide'
    echo 'DPO_CERTIFICATION=' >> $ENV_FILE
  else
    echo '✅ DPO_CERTIFICATION présent'
  fi
"

echo "
========================================="
echo "Variables DPO fixées avec succès !"
echo "========================================="
echo "
⚠️  ACTION REQUISE :"
echo "Éditer /etc/docavocat/.env sur le serveur et remplir :"
echo "  - DPO_NAME (nom complet du DPO)"
echo "  - DPO_EMAIL (email contact DPO)"
echo "  - DPO_PHONE (téléphone optionnel)"
echo "  - DPO_ADDRESS (adresse cabinet RGPD)"
echo "  - DPO_CERTIFICATION (certification optionnelle)"
echo "
Puis redémarrer l'application :"
echo "  docker-compose -f /etc/docavocat/docker-compose.yml restart app"
