#!/bin/bash

# ========================================
# Génération des clés RSA pour JWT RS256
# ========================================
# Ce script génère une paire de clés RSA (2048 bits)
# pour la signature des JWT avec l'algorithme RS256

KEYS_DIR="config/keys"
PRIVATE_KEY="$KEYS_DIR/private_key.pem"
PUBLIC_KEY="$KEYS_DIR/public_key.pem"

echo "========================================="
echo "  JWT RS256 Key Generation"
echo "========================================="
echo ""

# Créer le répertoire si nécessaire
if [ ! -d "$KEYS_DIR" ]; then
    mkdir -p "$KEYS_DIR"
    echo "✓ Created directory: $KEYS_DIR"
fi

# Vérifier si les clés existent déjà
if [ -f "$PRIVATE_KEY" ] || [ -f "$PUBLIC_KEY" ]; then
    echo "⚠️  Keys already exist!"
    echo ""
    read -p "Do you want to overwrite them? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
fi

# Générer la clé privée RSA (2048 bits)
echo ""
echo "Generating RSA private key (2048 bits)..."
openssl genpkey -algorithm RSA -out "$PRIVATE_KEY" -pkeyopt rsa_keygen_bits:2048
if [ $? -eq 0 ]; then
    echo "✓ Private key generated: $PRIVATE_KEY"
else
    echo "❌ Failed to generate private key"
    exit 1
fi

# Extraire la clé publique depuis la clé privée
echo ""
echo "Extracting public key..."
openssl rsa -pubout -in "$PRIVATE_KEY" -out "$PUBLIC_KEY"
if [ $? -eq 0 ]; then
    echo "✓ Public key generated: $PUBLIC_KEY"
else
    echo "❌ Failed to generate public key"
    exit 1
fi

# Afficher les clés
echo ""
echo "========================================="
echo "  Keys generated successfully!"
echo "========================================="
echo ""
echo "Private Key:"
cat "$PRIVATE_KEY"
echo ""
echo "Public Key:"
cat "$PUBLIC_KEY"
echo ""
echo "⚠️  IMPORTANT:"
echo "   - Keep private_key.pem SECRET and SECURE"
echo "   - Add config/keys/ to .gitignore"
echo "   - Backup your keys in a secure location"
echo ""
echo "To use in production:"
echo "   1. Copy these keys to your production server"
echo "   2. Set appropriate file permissions (600 for private key)"
echo "   3. Configure paths in application.properties:"
echo "      jwt.keys.private-key-path=config/keys/private_key.pem"
echo "      jwt.keys.public-key-path=config/keys/public_key.pem"
echo ""
