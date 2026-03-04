# ========================================
# Script PowerShell pour corriger le schéma de la base de données
# Date: 2026-03-03
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Correction du schéma de base de données" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration MySQL (à adapter selon votre environnement)
$MYSQL_HOST = "localhost"
$MYSQL_PORT = "3306"
$MYSQL_USER = "gedavocat"
$MYSQL_PASSWORD = Read-Host "Mot de passe MySQL" -AsSecureString
$MYSQL_DB = "gedavocat"

# Convertir le SecureString en texte clair pour MySQL
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($MYSQL_PASSWORD)
$PlainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)

Write-Host "Connexion à MySQL..." -ForegroundColor Yellow
Write-Host "Hôte: $MYSQL_HOST"
Write-Host "Base de données: $MYSQL_DB"
Write-Host ""

# Chemin du script SQL
$SQL_SCRIPT = "$PSScriptRoot\fix_database_schema.sql"

if (-Not (Test-Path $SQL_SCRIPT)) {
    Write-Host "ERREUR: Le fichier $SQL_SCRIPT n'existe pas!" -ForegroundColor Red
    exit 1
}

Write-Host "Exécution du script SQL: $SQL_SCRIPT" -ForegroundColor Yellow
Write-Host ""

# Confirm avant d'exécuter
$confirmation = Read-Host "ATTENTION: Ce script va supprimer les données des tables invoices et invoice_items. Continuer? (O/N)"
if ($confirmation -ne 'O' -and $confirmation -ne 'o') {
    Write-Host "Opération annulée." -ForegroundColor Yellow
    exit 0
}

try {
    # Exécuter le script SQL avec MySQL client
    $mysqlCmd = "mysql -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER -p$PlainPassword $MYSQL_DB < `"$SQL_SCRIPT`""
    
    # Alternative : utiliser Get-Content pour lire le script et l'envoyer à MySQL
    $sqlContent = Get-Content $SQL_SCRIPT -Raw
    $sqlContent | & mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p"$PlainPassword" $MYSQL_DB
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "Migration terminée avec succès!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Vous pouvez maintenant redémarrer l'application Spring Boot." -ForegroundColor Cyan
    
} catch {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "ERREUR lors de l'exécution du script SQL" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Write-Host "Vérifiez que MySQL client est installé et accessible dans le PATH" -ForegroundColor Yellow
    Write-Host "Vous pouvez aussi exécuter manuellement le script SQL:" -ForegroundColor Yellow
    Write-Host "mysql -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER -p $MYSQL_DB < $SQL_SCRIPT" -ForegroundColor Cyan
    exit 1
}
