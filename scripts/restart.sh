#!/bin/bash

# ===================================================================
# Script de redémarrage GED Avocat pour O2Switch
# ===================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "====================================================================="
echo "   GED Avocat - Redémarrage"
echo "====================================================================="

# Arrêter l'application
echo "Étape 1/2: Arrêt de l'application..."
$SCRIPT_DIR/stop.sh

# Attendre un peu
sleep 3

# Redémarrer l'application
echo ""
echo "Étape 2/2: Démarrage de l'application..."
$SCRIPT_DIR/start.sh

echo ""
echo "Redémarrage terminé !"
echo "====================================================================="
