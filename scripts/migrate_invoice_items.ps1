# ========================================
# Script PowerShell pour migrer le schéma invoice_items
# Date: 2026-03-03
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Migration du schéma invoice_items" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration MySQL
$MYSQL_HOST = "localhost"
$MYSQL_PORT = "3306"
$MYSQL_USER = "root"
$MYSQL_PASSWORD_PLAIN = "root"
$MYSQL_DB = "gedavocat"

Write-Host "Connexion à MySQL..." -ForegroundColor Yellow
Write-Host "Hôte: $MYSQL_HOST"
Write-Host "Base de données: $MYSQL_DB"
Write-Host ""

# Chemin du script SQL
$SQL_SCRIPT = "$PSScriptRoot\migrate_invoice_items_schema.sql"

if (-Not (Test-Path $SQL_SCRIPT)) {
    Write-Host "ERREUR: Le fichier $SQL_SCRIPT n'existe pas!" -ForegroundColor Red
    exit 1
}

Write-Host "Exécution du script SQL: $SQL_SCRIPT" -ForegroundColor Yellow
Write-Host ""
Write-Host "Ce script va:" -ForegroundColor Yellow
Write-Host "  1. Ajouter les nouvelles colonnes (unit_price_ht, tva_rate, etc.)" -ForegroundColor White
Write-Host "  2. Migrer les données existantes si présentes" -ForegroundColor White
Write-Host "  3. Conserver les anciennes colonnes (unit_price, total_price)" -ForegroundColor White
Write-Host ""

$confirmation = Read-Host "Continuer? (O/N)"
if ($confirmation -ne 'O' -and $confirmation -ne 'o') {
    Write-Host "Opération annulée." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
$migrationSuccess = $false

# Méthode 1: MySQL command line
Write-Host "Méthode 1: Recherche de mysql.exe..." -ForegroundColor Cyan
$mysqlPath = Get-Command mysql -ErrorAction SilentlyContinue

if ($mysqlPath) {
    Write-Host "✓ mysql.exe trouvé: $($mysqlPath.Source)" -ForegroundColor Green
    Write-Host "Exécution du script..." -ForegroundColor Yellow
    
    try {
        Get-Content $SQL_SCRIPT | & mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p"$MYSQL_PASSWORD_PLAIN" $MYSQL_DB 2>&1
        if ($LASTEXITCODE -eq 0) {
            $migrationSuccess = $true
        }
    } catch {
        Write-Host "Erreur: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "✗ mysql.exe non trouvé" -ForegroundColor Yellow
}

# Méthode 2: Docker
if (-not $migrationSuccess) {
    Write-Host ""
    Write-Host "Méthode 2: Recherche de Docker MySQL..." -ForegroundColor Cyan
    
    try {
        $dockerMysql = docker ps --filter "name=mysql" --format "{{.Names}}" 2>$null | Select-Object -First 1
        
        if ($dockerMysql) {
            Write-Host "✓ Conteneur MySQL trouvé: $dockerMysql" -ForegroundColor Green
            Write-Host "Exécution du script via Docker..." -ForegroundColor Yellow
            
            Get-Content $SQL_SCRIPT | docker exec -i $dockerMysql mysql -u $MYSQL_USER -p"$MYSQL_PASSWORD_PLAIN" $MYSQL_DB 2>&1
            if ($LASTEXITCODE -eq 0) {
                $migrationSuccess = $true
            }
        } else {
            Write-Host "✗ Conteneur MySQL non trouvé" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "✗ Docker non disponible" -ForegroundColor Yellow
    }
}

if ($migrationSuccess) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "✓ Migration réussie!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Prochaines étapes:" -ForegroundColor Cyan
    Write-Host "  1. Redémarrer l'application Spring Boot" -ForegroundColor White
    Write-Host "  2. Vérifier que les erreurs ont disparu" -ForegroundColor White
    Write-Host "  3. Tester la création de factures" -ForegroundColor White
    exit 0
}

# Si échec
Write-Host ""
Write-Host "========================================" -ForegroundColor Red
Write-Host "⚠ Migration automatique impossible" -ForegroundColor Red
Write-Host "========================================" -ForegroundColor Red
Write-Host ""
Write-Host "Solutions manuelles:" -ForegroundColor Yellow
Write-Host ""
Write-Host "Option 1 - MySQL Workbench:" -ForegroundColor Cyan
Write-Host "  1. Ouvrir MySQL Workbench" -ForegroundColor White
Write-Host "  2. Connecter à localhost:3306" -ForegroundColor White
Write-Host "  3. Ouvrir et exécuter: $SQL_SCRIPT" -ForegroundColor White
Write-Host ""
Write-Host "Option 2 - Via l'application:" -ForegroundColor Cyan
Write-Host "  POST http://localhost:8092/api/admin/migration/invoice-items-schema" -ForegroundColor White
Write-Host ""
Write-Host "Fichier de migration:" -ForegroundColor Yellow
Write-Host "  $SQL_SCRIPT" -ForegroundColor White
Write-Host ""

$openFolder = Read-Host "Ouvrir le dossier? (O/N)"
if ($openFolder -eq 'O' -or $openFolder -eq 'o') {
    explorer $PSScriptRoot
}

exit 1
