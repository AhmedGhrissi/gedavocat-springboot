#!/bin/bash
# Script de migration gedavocat -> doc_avocat
# À exécuter sur le serveur de production

set -e

echo "=== Migration base de données gedavocat -> doc_avocat ==="

cd /opt/docavocat/docker

# Récupérer les mots de passe depuis .env
export $(cat .env | grep MYSQL_ROOT_PASSWORD)
export $(cat .env | grep MYSQL_PASSWORD)

echo "1. Création de la nouvelle base de données doc_avocat..."
docker-compose exec -T mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} <<EOF
CREATE DATABASE IF NOT EXISTS \`doc_avocat\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'doc_avocat'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON \`doc_avocat\`.* TO 'doc_avocat'@'%';
FLUSH PRIVILEGES;
SHOW DATABASES;
EOF

echo ""
echo "2. Migration des données gedavocat -> doc_avocat..."
docker-compose exec -T mysql sh -c "mysqldump -u root -p\${MYSQL_ROOT_PASSWORD} --databases gedavocat | mysql -u root -p\${MYSQL_ROOT_PASSWORD} doc_avocat"

echo ""
echo "3. Vérification des tables migrées..."
docker-compose exec -T mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} -e "USE doc_avocat; SHOW TABLES;"

echo ""
echo "4. Pull de la nouvelle configuration..."
cd /opt/docavocat
git pull origin main

echo ""
echo "5. Redémarrage des conteneurs avec la nouvelle configuration..."
cd /opt/docavocat/docker
docker-compose down
docker-compose up -d

echo ""
echo "6. Attente du démarrage de l'application..."
sleep 10

echo ""
echo "7. Vérification des logs..."
docker-compose logs --tail=50 app

echo ""
echo "=== Migration terminée ==="
echo "Vérifiez que l'application fonctionne : https://docavocat.fr"
echo ""
echo "Si tout fonctionne, vous pouvez supprimer l'ancienne base :"
echo "docker-compose exec mysql mysql -u root -p -e 'DROP DATABASE gedavocat; DROP USER gedavocat@\"%\";'"
