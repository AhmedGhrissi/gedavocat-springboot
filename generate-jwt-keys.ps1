# ========================================
# JWT RS256 Key Generation (PowerShell)
# ========================================
# Ce script génère une paire de clés RSA (2048 bits)
# pour la signature des JWT avec l'algorithme RS256

$KEYS_DIR = "config\keys"
$PRIVATE_KEY = "$KEYS_DIR\private_key.pem"
$PUBLIC_KEY = "$KEYS_DIR\public_key.pem"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  JWT RS256 Key Generation" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Créer le répertoire si nécessaire
if (-Not (Test-Path $KEYS_DIR)) {
    New-Item -ItemType Directory -Path $KEYS_DIR | Out-Null
    Write-Host "✓ Created directory: $KEYS_DIR" -ForegroundColor Green
}

# Vérifier si les clés existent déjà
if ((Test-Path $PRIVATE_KEY) -or (Test-Path $PUBLIC_KEY)) {
    Write-Host "⚠️  Keys already exist!" -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "Do you want to overwrite them? (y/N)"
    if ($response -ne 'y' -and $response -ne 'Y') {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
}

# Générer la clé privée RSA (2048 bits)
Write-Host ""
Write-Host "Generating RSA private key (2048 bits)..." -ForegroundColor Cyan
try {
    & openssl genpkey -algorithm RSA -out $PRIVATE_KEY -pkeyopt rsa_keygen_bits:2048
    Write-Host "✓ Private key generated: $PRIVATE_KEY" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to generate private key" -ForegroundColor Red
    Write-Host "Make sure OpenSSL is installed: https://slproweb.com/products/Win32OpenSSL.html" -ForegroundColor Yellow
    exit 1
}

# Extraire la clé publique depuis la clé privée
Write-Host ""
Write-Host "Extracting public key..." -ForegroundColor Cyan
try {
    & openssl rsa -pubout -in $PRIVATE_KEY -out $PUBLIC_KEY
    Write-Host "✓ Public key generated: $PUBLIC_KEY" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to generate public key" -ForegroundColor Red
    exit 1
}

# Afficher les clés
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Keys generated successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Private Key:" -ForegroundColor Yellow
Get-Content $PRIVATE_KEY | Write-Host
Write-Host ""
Write-Host "Public Key:" -ForegroundColor Yellow
Get-Content $PUBLIC_KEY | Write-Host
Write-Host ""
Write-Host "⚠️  IMPORTANT:" -ForegroundColor Red
Write-Host "   - Keep private_key.pem SECRET and SECURE" -ForegroundColor Yellow
Write-Host "   - Add config/keys/ to .gitignore" -ForegroundColor Yellow
Write-Host "   - Backup your keys in a secure location" -ForegroundColor Yellow
Write-Host ""
Write-Host "To use in production:" -ForegroundColor Cyan
Write-Host "   1. Copy these keys to your production server"
Write-Host "   2. Set appropriate file permissions"
Write-Host "   3. Configure paths in application.properties:"
Write-Host "      jwt.keys.private-key-path=config/keys/private_key.pem"
Write-Host "      jwt.keys.public-key-path=config/keys/public_key.pem"
Write-Host ""
