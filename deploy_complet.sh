#!/bin/bash
# ============================================================================
# Script de déploiement complet GedAvoCat
# Date: 2026-03-06
# ============================================================================

echo "============================================================================"
echo "DEPLOIEMENT COMPLET GEDAVOCAT"
echo "============================================================================"
echo ""

# Configuration
DB_NAME="gedavocat"
DB_USER="root"

# Demander le mot de passe
read -sp "Mot de passe MySQL (root): " DB_PASSWORD
echo ""

echo ""
echo "[1/3] Importation du dump principal..."
mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < dump_gedavocat_prod_20260305_backup.sql
if [ $? -ne 0 ]; then
    echo "ERREUR lors de l'importation du dump principal!"
    exit 1
fi
echo "    ✓ OK - Dump principal importé"

echo ""
echo "[2/3] Ajout des utilisateurs admin et client..."
mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < add_admin_client_users.sql
if [ $? -ne 0 ]; then
    echo "ERREUR lors de l'ajout des utilisateurs!"
    exit 1
fi
echo "    ✓ OK - Utilisateurs ajoutés"

echo ""
echo "[3/3] Vérification..."
mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT id, email, CONCAT(first_name, ' ', last_name) AS nom, role FROM users WHERE id IN ('admin-super-001', 'client-demo-002');"

echo ""
echo "============================================================================"
echo "DEPLOIEMENT TERMINÉ AVEC SUCCÈS!"
echo "============================================================================"
echo ""
echo "Utilisateurs créés:"
echo ""
echo "  1. Super Admin"
echo "     Email: superadmin@gedavocat.fr"
echo "     Mot de passe: Test1234!"
echo "     Rôle: ADMIN"
echo ""
echo "  2. Client Demo"
echo "     Email: client.demo@gedavocat.fr"
echo "     Mot de passe: Test1234!"
echo "     Rôle: CLIENT"
echo ""
echo "============================================================================"
