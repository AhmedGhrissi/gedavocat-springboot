#!/bin/bash
# Script d'installation et configuration MySQL sécurisé (Ubuntu/Debian)
# Usage : à lancer en root sur la VM Hetzner fraîche

set -e

# 1. Installation
apt update
apt install -y mysql-server

# 2. Chiffrement at-rest (TDE)
cat <<EOF >> /etc/mysql/mysql.conf.d/mysqld.cnf
[mysqld]
early-plugin-load=keyring_file.so
keyring_file_data=/var/lib/mysql-keyring/keyring
default_table_encryption=ON
EOF

systemctl restart mysql

# 3. Sécurisation de l'installation
mysql_secure_installation <<EOF
n
Y
motdepassefort
motdepassefort
Y
Y
Y
Y
EOF

# 4. Création base et utilisateur
mysql -u root -pmotdepassefort <<SQL
CREATE DATABASE gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'gedavocat'@'%' IDENTIFIED BY 'motdepasseapp';
GRANT ALL PRIVILEGES ON gedavocat.* TO 'gedavocat'@'%';
FLUSH PRIVILEGES;
SQL

# 5. Affichage état chiffrement
mysql -u root -pmotdepassefort -e "SHOW VARIABLES LIKE 'default_table_encryption';"
echo "MySQL sécurisé et chiffré prêt ! Pense à changer les mots de passe dans ce script."
