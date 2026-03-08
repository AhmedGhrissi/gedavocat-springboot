# ===================================================================
# Script : Export du schéma BDD pour initialisation Docker
# Usage : Après avoir démarré l'app en mode dev (qui crée les tables)
# ===================================================================

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Export du schéma MySQL pour Docker" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Créer le dossier init s'il n'existe pas
$initDir = "docker\init"
if (-not (Test-Path $initDir)) {
    New-Item -ItemType Directory -Path $initDir | Out-Null
    Write-Host "✓ Dossier $initDir créé" -ForegroundColor Green
}

# Chemin de sortie
$outputFile = "$initDir\01-schema.sql"

Write-Host "[1/2] Export du schéma depuis MySQL Docker..." -ForegroundColor Yellow

# Exporter le schéma (structure only, pas les données)
docker exec docavocat-mysql mysqldump `
    -u doc_avocat `
    -p'DocAvocat2026!DevDB' `
    --no-data `
    --skip-add-drop-table `
    --skip-comments `
    --skip-set-charset `
    doc_avocat > $outputFile 2>$null

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Schéma exporté avec succès !" -ForegroundColor Green
    Write-Host ""
    Write-Host "Fichier : $outputFile" -ForegroundColor Cyan
    
    # Statistiques
    $lineCount = (Get-Content $outputFile).Count
    $tableCount = (Select-String -Path $outputFile -Pattern "CREATE TABLE").Matches.Count
    
    Write-Host ""
    Write-Host "Statistiques :" -ForegroundColor White
    Write-Host "  • Lignes SQL  : $lineCount" -ForegroundColor Gray
    Write-Host "  • Tables      : $tableCount" -ForegroundColor Gray
    Write-Host ""
    
    # Lister les tables
    Write-Host "Tables exportées :" -ForegroundColor Yellow
    Select-String -Path $outputFile -Pattern "CREATE TABLE ``(\w+)``" | ForEach-Object {
        $tableName = $_.Matches[0].Groups[1].Value
        Write-Host "  • $tableName" -ForegroundColor Gray
    }
    
    Write-Host ""
    Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host "  Prochaines étapes :" -ForegroundColor Green
    Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
    Write-Host ""
    Write-Host "1. Décommentez dans docker-compose.yml :" -ForegroundColor White
    Write-Host "   - ./init/01-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "2. Pour réinitialiser complètement la BDD :" -ForegroundColor White
    Write-Host "   docker-compose down -v" -ForegroundColor Cyan
    Write-Host "   docker-compose up -d mysql" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "3. Le schéma sera automatiquement chargé au premier démarrage" -ForegroundColor White
    Write-Host ""
    
} else {
    Write-Host "✗ Erreur lors de l'export" -ForegroundColor Red
    Write-Host ""
    Write-Host "Vérifiez que :" -ForegroundColor Yellow
    Write-Host "  1. Docker Desktop est démarré" -ForegroundColor White
    Write-Host "  2. Le conteneur MySQL tourne : docker ps" -ForegroundColor White
    Write-Host "  3. La base doc_avocat existe et contient des tables" -ForegroundColor White
    Write-Host ""
    Write-Host "Pour créer les tables, lancez d'abord :" -ForegroundColor Yellow
    Write-Host "  mvn spring-boot:run -Dspring-boot.run.profiles=dev" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Puis relancez ce script." -ForegroundColor Yellow
}

Write-Host ""
