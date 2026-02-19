#!/bin/bash

# ===================================================================
# Script de build pour déploiement GED Avocat
# ===================================================================

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "====================================================================="
echo -e "${BLUE}   GED Avocat - Build pour déploiement O2Switch${NC}"
echo "====================================================================="

# 1. Vérifier que Maven est installé
echo -e "\n${YELLOW}[1/5] Vérification de Maven...${NC}"
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗ Maven n'est pas installé${NC}"
    echo "Installez Maven: https://maven.apache.org/install.html"
    exit 1
fi
echo -e "${GREEN}✓ Maven trouvé: $(mvn --version | head -n 1)${NC}"

# 2. Nettoyer le projet
echo -e "\n${YELLOW}[2/5] Nettoyage du projet...${NC}"
mvn clean
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Erreur lors du nettoyage${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Nettoyage terminé${NC}"

# 3. Compiler et packager
echo -e "\n${YELLOW}[3/5] Compilation et packaging (cela peut prendre quelques minutes)...${NC}"
mvn package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Erreur lors de la compilation${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Compilation réussie${NC}"

# 4. Créer le dossier de déploiement
echo -e "\n${YELLOW}[4/5] Création du package de déploiement...${NC}"

DEPLOY_DIR="deploy-o2switch"
rm -rf $DEPLOY_DIR
mkdir -p $DEPLOY_DIR
mkdir -p $DEPLOY_DIR/scripts

# Copier le JAR
cp target/gedavocat-app-1.0.0.jar $DEPLOY_DIR/app.jar

# Copier la configuration
cp application-o2switch.properties $DEPLOY_DIR/application.properties

# Copier les scripts
cp scripts/*.sh $DEPLOY_DIR/scripts/
chmod +x $DEPLOY_DIR/scripts/*.sh

# Copier .htaccess
cp .htaccess $DEPLOY_DIR/

# Copier le script SQL
cp database-init.sql $DEPLOY_DIR/

# Copier la documentation
cp DEPLOIEMENT-O2SWITCH.md $DEPLOY_DIR/
cp README.md $DEPLOY_DIR/
cp QUICKSTART.md $DEPLOY_DIR/

echo -e "${GREEN}✓ Package créé dans: $DEPLOY_DIR/${NC}"

# 5. Créer l'archive ZIP
echo -e "\n${YELLOW}[5/5] Création de l'archive ZIP...${NC}"
ZIP_NAME="gedavocat-o2switch-$(date +%Y%m%d-%H%M%S).zip"
zip -r $ZIP_NAME $DEPLOY_DIR/
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Erreur lors de la création du ZIP${NC}"
    exit 1
fi

# Afficher le résumé
echo ""
echo "====================================================================="
echo -e "${GREEN}   ✓ Build terminé avec succès !${NC}"
echo "====================================================================="
echo ""
echo "Contenu du package de déploiement:"
ls -lh $DEPLOY_DIR/
echo ""
echo -e "${BLUE}Archive créée:${NC} $ZIP_NAME"
echo -e "Taille: $(du -h $ZIP_NAME | cut -f1)"
echo ""
echo "====================================================================="
echo "   Prochaines étapes:"
echo "====================================================================="
echo "1. Uploadez le fichier $ZIP_NAME sur votre serveur O2Switch"
echo "2. Décompressez-le: unzip $ZIP_NAME"
echo "3. Configurez application.properties avec vos identifiants MySQL"
echo "4. Suivez les instructions dans DEPLOIEMENT-O2SWITCH.md"
echo ""
echo -e "${GREEN}Bon déploiement ! 🚀${NC}"
echo "====================================================================="
