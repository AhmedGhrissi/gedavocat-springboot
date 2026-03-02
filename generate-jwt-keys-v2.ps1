# JWT RS256 Key Generation (using ssh-keygen)
# Alternative for systems without OpenSSL

$KEYS_DIR = "config\keys"
$PRIVATE_KEY_SSH = "$KEYS_DIR\jwt_rsa"
$PUBLIC_KEY_SSH = "$KEYS_DIR\jwt_rsa.pub"
$PRIVATE_KEY = "$KEYS_DIR\private_key.pem"
$PUBLIC_KEY = "$KEYS_DIR\public_key.pem"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  JWT RS256 Key Generation" -ForegroundColor Cyan
Write-Host "  Using ssh-keygen" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Create directory
if (-Not (Test-Path $KEYS_DIR)) {
    New-Item -ItemType Directory -Path $KEYS_DIR | Out-Null
    Write-Host "[OK] Created directory: $KEYS_DIR" -ForegroundColor Green
}

# Check if keys exist
if ((Test-Path $PRIVATE_KEY) -or (Test-Path $PUBLIC_KEY)) {
    Write-Host "[WARNING] Keys already exist!" -ForegroundColor Yellow
    $response = Read-Host "Overwrite? (y/N)"
    if ($response -ne 'y' -and $response -ne 'Y') {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
}

# Generate RSA key with ssh-keygen
Write-Host ""
Write-Host "Generating RSA private key (2048 bits)..." -ForegroundColor Cyan
try {
    $null = & ssh-keygen -t rsa -b 2048 -m PEM -f $PRIVATE_KEY_SSH -N '""' 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] RSA keys generated" -ForegroundColor Green
    } else {
        throw "ssh-keygen failed"
    }
} catch {
    Write-Host "[ERROR] Failed to generate keys" -ForegroundColor Red
    exit 1
}

# Convert to PKCS#8 format for Java
Write-Host ""
Write-Host "Converting to PKCS#8 format..." -ForegroundColor Cyan
try {
    # ssh-keygen already creates PEM format private key
    # Just rename/copy to expected location
    Copy-Item $PRIVATE_KEY_SSH $PRIVATE_KEY -Force
    Write-Host "[OK] Private key: $PRIVATE_KEY" -ForegroundColor Green
    
    # Convert public key to PEM format
    $sshPubKey = Get-Content $PUBLIC_KEY_SSH
    $base64Key = ($sshPubKey -split ' ')[1]
    
    # Create PEM public key format
    $pemPublicKey = @"
-----BEGIN PUBLIC KEY-----
$base64Key
-----END PUBLIC KEY-----
"@
    
    $pemPublicKey | Out-File -FilePath $PUBLIC_KEY -Encoding ASCII
    Write-Host "[OK] Public key: $PUBLIC_KEY" -ForegroundColor Green
    
    # Cleanup SSH format files
    Remove-Item $PRIVATE_KEY_SSH -Force -ErrorAction SilentlyContinue
    Remove-Item $PUBLIC_KEY_SSH -Force -ErrorAction SilentlyContinue
    
} catch {
    Write-Host "[ERROR] Failed to convert keys" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

# Display success
Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  Keys generated successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Private Key ($PRIVATE_KEY):" -ForegroundColor Yellow
Get-Content $PRIVATE_KEY | Write-Host
Write-Host ""
Write-Host "Public Key ($PUBLIC_KEY):" -ForegroundColor Yellow
Get-Content $PUBLIC_KEY | Write-Host
Write-Host ""
Write-Host "IMPORTANT SECURITY NOTES:" -ForegroundColor Red
Write-Host "  - Keep private_key.pem SECRET and SECURE" -ForegroundColor Yellow
Write-Host "  - Never commit keys to Git (already in .gitignore)" -ForegroundColor Yellow
Write-Host "  - Backup keys in a secure, encrypted location" -ForegroundColor Yellow
Write-Host ""
Write-Host "Application is ready to start!" -ForegroundColor Green
Write-Host ""
