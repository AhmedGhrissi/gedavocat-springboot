# ============================================================================
# Script PowerShell de déploiement complet GedAvoCat
# Date: 2026-03-06
# ============================================================================

Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "DEPLOIEMENT COMPLET GEDAVOCAT" -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$DB_NAME = "gedavocat"
$DB_USER = "root"
$DB_PASSWORD = Read-Host "Mot de passe MySQL (root)" -AsSecureString
$DB_PASS_PLAIN = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($DB_PASSWORD))

Write-Host ""
Write-Host "[1/3] Importation du dump principal..." -ForegroundColor Yellow
$output1 = cmd /c "mysql -u $DB_USER -p$DB_PASS_PLAIN $DB_NAME < dump_gedavocat_prod_20260305_backup.sql 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "    ✗ ERREUR lors de l'importation du dump principal!" -ForegroundColor Red
    Write-Host $output1 -ForegroundColor Red
    exit 1
}
Write-Host "    ✓ OK - Dump principal importé" -ForegroundColor Green

Write-Host ""
Write-Host "[2/3] Ajout des utilisateurs admin et client..." -ForegroundColor Yellow
$output2 = cmd /c "mysql -u $DB_USER -p$DB_PASS_PLAIN $DB_NAME < add_admin_client_users.sql 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "    ✗ ERREUR lors de l'ajout des utilisateurs!" -ForegroundColor Red
    Write-Host $output2 -ForegroundColor Red
    exit 1
}
Write-Host "    ✓ OK - Utilisateurs ajoutés" -ForegroundColor Green

Write-Host ""
Write-Host "[3/3] Vérification..." -ForegroundColor Yellow
$verification = "SELECT id, email, CONCAT(first_name, ' ', last_name) AS nom, role FROM users WHERE id IN ('admin-super-001', 'client-demo-002');"
mysql -u $DB_USER -p$DB_PASS_PLAIN $DB_NAME -e $verification

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "DEPLOIEMENT TERMINÉ AVEC SUCCÈS!" -ForegroundColor Green
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Utilisateurs créés:" -ForegroundColor White
Write-Host ""
Write-Host "  1. Super Admin" -ForegroundColor Yellow
Write-Host "     Email: superadmin@gedavocat.fr" -ForegroundColor White
Write-Host "     Mot de passe: Test1234!" -ForegroundColor White
Write-Host "     Rôle: ADMIN" -ForegroundColor White
Write-Host ""
Write-Host "  2. Client Demo" -ForegroundColor Yellow
Write-Host "     Email: client.demo@gedavocat.fr" -ForegroundColor White
Write-Host "     Mot de passe: Test1234!" -ForegroundColor White
Write-Host "     Rôle: CLIENT" -ForegroundColor White
Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan

# Pause
Read-Host "Appuyez sur Entrée pour continuer..."
