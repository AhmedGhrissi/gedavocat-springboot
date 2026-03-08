#!/bin/bash
# ===================================================================
# Script d'import du dump SQL sur serveur distant (Linux)
# Usage : ./import-dump-remote.sh docavocat.fr root
# ===================================================================

SERVER=${1:-"docavocat.fr"}
USER=${2:-"root"}
DATABASE=${3:-"doc_avocat"}
DUMP_FILE="docker/init/01-complete-dump.sql"

echo "════════════════════════════════════════════════════════"
echo "  Import dump SQL sur serveur distant"
echo "════════════════════════════════════════════════════════"
echo ""

# Vérifier que le fichier dump existe
if [ ! -f "$DUMP_FILE" ]; then
    echo "✗ Fichier dump introuvable : $DUMP_FILE"
    echo ""
    echo "Générez d'abord le dump avec :"
    echo "  sed 's/gedavocat/doc_avocat/g' Dump20260309.sql > $DUMP_FILE"
    exit 1
fi

echo "Configuration :"
echo "  Serveur      : $SERVER"
echo "  Utilisateur  : $USER"
echo "  Base         : $DATABASE"
echo "  Dump         : $DUMP_FILE"
echo ""

# Statistiques
LINE_COUNT=$(wc -l < "$DUMP_FILE")
TABLE_COUNT=$(grep -c "CREATE TABLE" "$DUMP_FILE")
echo "Dump à importer :"
echo "  • $LINE_COUNT lignes SQL"
echo "  • $TABLE_COUNT tables"
echo ""

# Confirmation
echo "⚠️  ATTENTION : Cette opération va écraser les données existantes !"
echo ""
read -p "Continuer ? (oui/non) : " confirmation

if [ "$confirmation" != "oui" ]; then
    echo "Annulé."
    exit 0
fi

echo ""
echo "[1/3] Transfert du dump vers le serveur..."

# Transférer via SCP
scp "$DUMP_FILE" "${USER}@${SERVER}:/tmp/doc_avocat_dump.sql"

if [ $? -ne 0 ]; then
    echo "✗ Erreur lors du transfert"
    exit 1
fi

echo "✓ Dump transféré"
echo ""

echo "[2/3] Import du dump dans MySQL..."
echo "      (Entrez le mot de passe MySQL quand demandé)"
echo ""

# Importer le dump
ssh "${USER}@${SERVER}" "docker exec -i docavocat-mysql mysql -u root -p $DATABASE < /tmp/doc_avocat_dump.sql"

if [ $? -eq 0 ]; then
    echo "✓ Dump importé avec succès"
else
    echo "✗ Erreur lors de l'import"
    exit 1
fi

echo ""
echo "[3/3] Nettoyage..."

# Supprimer le fichier temporaire
ssh "${USER}@${SERVER}" "rm /tmp/doc_avocat_dump.sql"

echo "✓ Fichier temporaire supprimé"
echo ""
echo "════════════════════════════════════════════════════════"
echo "  Import terminé avec succès !"
echo "════════════════════════════════════════════════════════"
echo ""
echo "La base de données $DATABASE sur $SERVER contient maintenant :"
echo "  • $TABLE_COUNT tables"
echo "  • Toutes les données du dump"
echo ""
echo "Prochaine étape : Redémarrez l'application"
echo "  ssh ${USER}@${SERVER}"
echo "  docker-compose restart app"
echo ""
