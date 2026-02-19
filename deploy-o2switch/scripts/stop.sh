#!/bin/bash

# ===================================================================
# Script d'arrêt GED Avocat pour O2Switch
# ===================================================================

# Variables
APP_DIR="$HOME/gedavocat"
PID_FILE="$APP_DIR/app.pid"

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "====================================================================="
echo "   GED Avocat - Script d'arrêt"
echo "====================================================================="

# Vérifier si le fichier PID existe
if [ ! -f $PID_FILE ]; then
    echo -e "${YELLOW}Fichier PID non trouvé, l'application n'est peut-être pas en cours d'exécution${NC}"
    exit 1
fi

# Lire le PID
PID=$(cat $PID_FILE)

# Vérifier si le processus tourne
if ! ps -p $PID > /dev/null 2>&1; then
    echo -e "${YELLOW}Le processus avec PID $PID n'est pas en cours d'exécution${NC}"
    rm $PID_FILE
    exit 1
fi

# Arrêter le processus
echo -e "${GREEN}Arrêt de l'application (PID: $PID)...${NC}"
kill $PID

# Attendre que le processus s'arrête
TIMEOUT=30
COUNTER=0

while ps -p $PID > /dev/null 2>&1 && [ $COUNTER -lt $TIMEOUT ]; do
    sleep 1
    COUNTER=$((COUNTER + 1))
    echo -n "."
done

echo ""

# Vérifier si le processus s'est arrêté
if ps -p $PID > /dev/null 2>&1; then
    echo -e "${RED}Le processus ne s'est pas arrêté gracieusement, arrêt forcé...${NC}"
    kill -9 $PID
    sleep 1
fi

# Supprimer le fichier PID
rm $PID_FILE

echo -e "${GREEN}✓ Application arrêtée avec succès${NC}"
echo "====================================================================="
