#!/bin/bash

# ===================================================================
# Script de sauvegarde GED Avocat pour O2Switch
# ===================================================================

# Configuration
APP_DIR="$HOME/gedavocat"
BACKUP_DIR="$HOME/backups/gedavocat"
DATE=$(date +%Y%m%d_%H%M%S)

# À CONFIGURER: Informations MySQL
DB_USER="VOTRE_UTILISATEUR_MYSQL"
DB_PASSWORD="VOTRE_MOT_DE_PASSE_MYSQL"
DB_NAME="VOTRE_BASE_DE_DONNEES"

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "====================================================================="
echo "   GED Avocat - Sauvegarde automatique"
echo "====================================================================="

# Créer le répertoire de backup s'il n'existe pas
mkdir -p $BACKUP_DIR

echo -e "${GREEN}Date de sauvegarde: $(date)${NC}"
echo "Répertoire de sauvegarde: $BACKUP_DIR"
echo ""

# 1. Sauvegarde de la base de données
echo -e "${YELLOW}[1/3] Sauvegarde de la base de données MySQL...${NC}"
mysqldump -u $DB_USER -p"$DB_PASSWORD" $DB_NAME > $BACKUP_DIR/db_$DATE.sql

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Base de données sauvegardée: db_$DATE.sql${NC}"
    # Compresser le dump SQL
    gzip $BACKUP_DIR/db_$DATE.sql
    echo -e "${GREEN}✓ Fichier compressé: db_$DATE.sql.gz${NC}"
else
    echo -e "${RED}✗ Erreur lors de la sauvegarde de la base de données${NC}"
fi

# 2. Sauvegarde des fichiers uploadés
echo ""
echo -e "${YELLOW}[2/3] Sauvegarde des fichiers uploadés...${NC}"
if [ -d "$HOME/uploads" ]; then
    tar -czf $BACKUP_DIR/files_$DATE.tar.gz -C $HOME uploads
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Fichiers sauvegardés: files_$DATE.tar.gz${NC}"
    else
        echo -e "${RED}✗ Erreur lors de la sauvegarde des fichiers${NC}"
    fi
else
    echo -e "${YELLOW}  Répertoire uploads non trouvé, ignoré${NC}"
fi

# 3. Sauvegarde de la configuration
echo ""
echo -e "${YELLOW}[3/3] Sauvegarde de la configuration...${NC}"
if [ -f "$APP_DIR/application.properties" ]; then
    cp $APP_DIR/application.properties $BACKUP_DIR/config_$DATE.properties
    echo -e "${GREEN}✓ Configuration sauvegardée: config_$DATE.properties${NC}"
else
    echo -e "${YELLOW}  Fichier de configuration non trouvé${NC}"
fi

# 4. Nettoyer les anciennes sauvegardes (> 30 jours)
echo ""
echo -e "${YELLOW}Nettoyage des anciennes sauvegardes (> 30 jours)...${NC}"
find $BACKUP_DIR -type f -mtime +30 -delete
DELETED_COUNT=$(find $BACKUP_DIR -type f -mtime +30 | wc -l)
echo -e "${GREEN}✓ Nettoyage terminé ($DELETED_COUNT fichiers supprimés)${NC}"

# 5. Afficher le résumé
echo ""
echo "====================================================================="
echo "   Résumé de la sauvegarde"
echo "====================================================================="
echo "Emplacement: $BACKUP_DIR"
echo ""
ls -lh $BACKUP_DIR/*_$DATE.*
echo ""
echo -e "${GREEN}✓ Sauvegarde terminée avec succès !${NC}"
echo "====================================================================="
