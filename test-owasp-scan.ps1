# Test rapide OWASP Dependency Check avec clé NVD API
# Usage : .\test-owasp-scan.ps1

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  OWASP Dependency Check - Test Rapide" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Charger la clé depuis .env.local
if (Test-Path ".env.local") {
    Write-Host "[OK] Fichier .env.local trouvé" -ForegroundColor Green
    Get-Content ".env.local" | ForEach-Object {
        if ($_ -match '^NVD_API_KEY=(.+)$') {
            $env:NVD_API_KEY = $matches[1]
            Write-Host "[OK] NVD_API_KEY chargée : $($matches[1].Substring(0,8))..." -ForegroundColor Green
        }
    }
} else {
    Write-Host "[WARN] Fichier .env.local introuvable - scan sera lent (30 min)" -ForegroundColor Yellow
}

# Vérifier Maven
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Maven non installé - installer depuis https://maven.apache.org" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Lancement du scan OWASP Dependency Check..." -ForegroundColor Cyan
Write-Host "Temps estimé : 2-3 minutes (avec clé API) ou 30+ minutes (sans clé)" -ForegroundColor Yellow
Write-Host ""

# Lancer le scan
$startTime = Get-Date
mvn dependency-check:check -DskipTests -Dformat=HTML -DoutputDirectory=target/security-reports

$duration = (Get-Date) - $startTime
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Scan terminé en $($duration.Minutes) min $($duration.Seconds) sec" -ForegroundColor Green
Write-Host "Rapport : target/security-reports/dependency-check-report.html" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Ouvrir le rapport automatiquement
if (Test-Path "target/security-reports/dependency-check-report.html") {
    Write-Host ""
    $response = Read-Host "Ouvrir le rapport dans le navigateur ? (O/N)"
    if ($response -eq "O" -or $response -eq "o") {
        Start-Process "target/security-reports/dependency-check-report.html"
    }
}
