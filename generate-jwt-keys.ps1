# JWT RS256 Key Generation
# PowerShell Script

$KEYS_DIR = "config\keys"
$PRIVATE_KEY = "$KEYS_DIR\private_key.pem"
$PUBLIC_KEY = "$KEYS_DIR\public_key.pem"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  JWT RS256 Key Generation" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Create directory if needed
if (-Not (Test-Path $KEYS_DIR)) {
    New-Item -ItemType Directory -Path $KEYS_DIR | Out-Null
    Write-Host "[OK] Created directory: $KEYS_DIR" -ForegroundColor Green
}

# Check if keys already exist
if ((Test-Path $PRIVATE_KEY) -or (Test-Path $PUBLIC_KEY)) {
    Write-Host "[WARNING] Keys already exist!" -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "Do you want to overwrite them? (y/N)"
    if ($response -ne 'y' -and $response -ne 'Y') {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
}

# Generate RSA private key
Write-Host ""
Write-Host "Generating RSA private key..." -ForegroundColor Cyan
try {
    $null = & openssl genpkey -algorithm RSA -out $PRIVATE_KEY -pkeyopt rsa_keygen_bits:2048 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Private key generated: $PRIVATE_KEY" -ForegroundColor Green
    } else {
        throw "OpenSSL failed"
    }
} catch {
    Write-Host "[ERROR] Failed to generate private key" -ForegroundColor Red
    Write-Host "Install OpenSSL from: https://slproweb.com/products/Win32OpenSSL.html" -ForegroundColor Yellow
    exit 1
}

# Extract public key
Write-Host ""
Write-Host "Extracting public key..." -ForegroundColor Cyan
try {
    $null = & openssl rsa -pubout -in $PRIVATE_KEY -out $PUBLIC_KEY 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Public key generated: $PUBLIC_KEY" -ForegroundColor Green
    } else {
        throw "OpenSSL failed"
    }
} catch {
    Write-Host "[ERROR] Failed to generate public key" -ForegroundColor Red
    exit 1
}

# Display success
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Success! Keys generated" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Private Key:" -ForegroundColor Yellow
Get-Content $PRIVATE_KEY | Write-Host
Write-Host ""
Write-Host "Public Key:" -ForegroundColor Yellow
Get-Content $PUBLIC_KEY | Write-Host
Write-Host ""
Write-Host "IMPORTANT:" -ForegroundColor Red
Write-Host "  - Keep private_key.pem SECRET" -ForegroundColor Yellow
Write-Host "  - Backup keys in a secure location" -ForegroundColor Yellow
Write-Host "  - Keys are in .gitignore" -ForegroundColor Yellow
Write-Host ""
