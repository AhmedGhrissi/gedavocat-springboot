#!/bin/bash

# ===================================================================
# Script de démarrage GED Avocat pour O2Switch
# ===================================================================

# Variables (à adapter selon votre environnement)
APP_DIR="$HOME/gedavocat"
JAR_FILE="$APP_DIR/app.jar"
LOG_FILE="$APP_DIR/logs/gedavocat.log"
PID_FILE="$APP_DIR/app.pid"
CONFIG_FILE="$APP_DIR/application.properties"

# Couleurs pour l'affichage
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "====================================================================="
echo "   GED Avocat - Script de démarrage"
echo "====================================================================="

# Vérifier si l'application tourne déjà
if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${YELLOW}L'application est déjà en cours d'exécution (PID: $PID)${NC}"
        exit 1
    else
        echo -e "${YELLOW}Fichier PID trouvé mais processus non actif, nettoyage...${NC}"
        rm $PID_FILE
    fi
fi

# Vérifier que le fichier JAR existe
if [ ! -f $JAR_FILE ]; then
    echo -e "${RED}Erreur: Fichier JAR non trouvé: $JAR_FILE${NC}"
    exit 1
fi

# Vérifier que le fichier de configuration existe
if [ ! -f $CONFIG_FILE ]; then
    echo -e "${RED}Erreur: Fichier de configuration non trouvé: $CONFIG_FILE${NC}"
    exit 1
fi

# Créer le répertoire de logs s'il n'existe pas
mkdir -p $(dirname $LOG_FILE)

# Afficher les informations
echo "Répertoire application: $APP_DIR"
echo "Fichier JAR: $JAR_FILE"
echo "Configuration: $CONFIG_FILE"
echo "Fichier de log: $LOG_FILE"
echo ""

# Démarrer l'application
echo -e "${GREEN}Démarrage de l'application...${NC}"

nohup java -jar $JAR_FILE \
  --spring.config.location=file:$CONFIG_FILE \
  >> $LOG_FILE 2>&1 &

# Sauvegarder le PID
APP_PID=$!
echo $APP_PID > $PID_FILE

# Attendre un peu pour vérifier que l'application a démarré
sleep 3

# Vérifier si le processus tourne toujours
if ps -p $APP_PID > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Application démarrée avec succès !${NC}"
    echo "  PID: $APP_PID"
    echo "  Logs: tail -f $LOG_FILE"
    echo ""
    echo "Pour arrêter l'application: ./stop.sh"
else
    echo -e "${RED}✗ Erreur lors du démarrage de l'application${NC}"
    echo "Consultez les logs: tail -f $LOG_FILE"
    rm $PID_FILE
    exit 1
fi

echo "====================================================================="
