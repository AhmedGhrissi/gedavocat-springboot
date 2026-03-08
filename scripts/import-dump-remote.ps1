# ===================================================================
# Script d'import du dump SQL sur serveur distant
# Usage : .\import-dump-remote.ps1 -Server "docavocat.fr" -User "root"
# ===================================================================

param(
    [string]$Server = "docavocat.fr",
    [string]$User = "root",
    [string]$Database = "doc_avocat",
    [string]$DumpFile = "docker/init/01-complete-dump.sql"
)

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Import dump SQL sur serveur distant" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Vérifier que le fichier dump existe
if (-not (Test-Path $DumpFile)) {
    Write-Host "✗ Fichier dump introuvable : $DumpFile" -ForegroundColor Red
    Write-Host ""
    Write-Host "Générez d'abord le dump avec :" -ForegroundColor Yellow
    Write-Host "  (Get-Content Dump20260309.sql -Raw) -replace 'gedavocat', 'doc_avocat' | Set-Content $DumpFile" -ForegroundColor Cyan
    exit 1
}

Write-Host "Configuration :" -ForegroundColor White
Write-Host "  Serveur   : $Server" -ForegroundColor Gray
Write-Host "  Utilisateur : $User" -ForegroundColor Gray
Write-Host "  Base      : $Database" -ForegroundColor Gray
Write-Host "  Dump      : $DumpFile" -ForegroundColor Gray
Write-Host ""

# Statistiques du dump
$lineCount = (Get-Content $DumpFile).Count
$tableCount = (Select-String -Path $DumpFile -Pattern "CREATE TABLE").Matches.Count
Write-Host "Dump à importer :" -ForegroundColor White
Write-Host "  • $lineCount lignes SQL" -ForegroundColor Gray
Write-Host "  • $tableCount tables" -ForegroundColor Gray
Write-Host ""

# Demander confirmation
Write-Host "⚠️  ATTENTION : Cette opération va écraser les données existantes !" -ForegroundColor Yellow
Write-Host ""
$confirmation = Read-Host "Continuer ? (oui/non)"

if ($confirmation -ne "oui") {
    Write-Host "Annulé." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "[1/3] Transfert du dump vers le serveur..." -ForegroundColor Yellow

# Transférer le dump via SCP
scp $DumpFile "${User}@${Server}:/tmp/doc_avocat_dump.sql"

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Erreur lors du transfert" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Dump transféré" -ForegroundColor Green
Write-Host ""

Write-Host "[2/3] Import du dump dans MySQL..." -ForegroundColor Yellow
Write-Host "      (Entrez le mot de passe MySQL quand demandé)" -ForegroundColor Gray
Write-Host ""

# Se connecter au serveur et importer le dump
ssh "${User}@${Server}" "docker exec -i docavocat-mysql mysql -u root -p $Database < /tmp/doc_avocat_dump.sql"

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Dump importé avec succès" -ForegroundColor Green
} else {
    Write-Host "✗ Erreur lors de l'import" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[3/3] Nettoyage..." -ForegroundColor Yellow

# Supprimer le fichier temporaire
ssh "${User}@${Server}" "rm /tmp/doc_avocat_dump.sql"

Write-Host "✓ Fichier temporaire supprimé" -ForegroundColor Green
Write-Host ""
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  Import terminé avec succès !" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "La base de données $Database sur $Server contient maintenant :" -ForegroundColor White
Write-Host "  • $tableCount tables" -ForegroundColor Gray
Write-Host "  • Toutes les données du dump" -ForegroundColor Gray
Write-Host ""
Write-Host "Prochaine étape : Redémarrez l'application" -ForegroundColor Yellow
Write-Host "  ssh ${User}@${Server}" -ForegroundColor Cyan
Write-Host "  docker-compose restart app" -ForegroundColor Cyan
Write-Host ""
