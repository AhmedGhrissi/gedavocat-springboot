# ===================================================================
# Script de démarrage pour PRÉSENTATION / DÉMO
# ===================================================================

Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   DocAvocat - Démarrage pour Présentation            ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Variables
$dockerPath = ".\docker"
$checkDelay = 5

# ── Étape 1 : Vérifier Docker ──────────────────────────────────────
Write-Host "[1/4] Vérification Docker..." -ForegroundColor Yellow
try {
    docker version | Out-Null
    Write-Host "      ✓ Docker est démarré" -ForegroundColor Green
} catch {
    Write-Host "      ✗ Docker n'est pas démarré" -ForegroundColor Red
    Write-Host ""
    Write-Host "      → Ouvrez Docker Desktop et relancez ce script" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Appuyez sur Entrée pour quitter"
    exit 1
}

Write-Host ""

# ── Étape 2 : Démarrer MySQL ───────────────────────────────────────
Write-Host "[2/4] Démarrage MySQL..." -ForegroundColor Yellow
Push-Location $dockerPath
docker-compose up -d mysql 2>&1 | Out-Null
Pop-Location

if ($LASTEXITCODE -eq 0) {
    Write-Host "      ✓ MySQL démarré" -ForegroundColor Green
} else {
    Write-Host "      ✗ Erreur au démarrage MySQL" -ForegroundColor Red
    exit 1
}

# Attendre que MySQL soit healthy
Write-Host "      ⏳ Attente que MySQL soit prêt..." -ForegroundColor Yellow
Start-Sleep -Seconds $checkDelay

$maxAttempts = 12
$attempt = 0
$isHealthy = $false

while ($attempt -lt $maxAttempts -and -not $isHealthy) {
    $health = docker inspect docavocat-mysql --format='{{.State.Health.Status}}' 2>$null
    if ($health -eq "healthy") {
        $isHealthy = $true
        Write-Host "      ✓ MySQL est prêt !" -ForegroundColor Green
    } else {
        $attempt++
        Write-Host "      ⏳ Tentative $attempt/$maxAttempts..." -ForegroundColor Gray
        Start-Sleep -Seconds 5
    }
}

if (-not $isHealthy) {
    Write-Host "      ⚠ MySQL prend du temps à démarrer, mais continuons..." -ForegroundColor Yellow
}

Write-Host ""

# ── Étape 3 : Compiler l'application ───────────────────────────────
Write-Host "[3/4] Compilation de l'application..." -ForegroundColor Yellow
mvn clean package -DskipTests -q

if ($LASTEXITCODE -eq 0) {
    Write-Host "      ✓ Application compilée" -ForegroundColor Green
} else {
    Write-Host "      ✗ Erreur de compilation" -ForegroundColor Red
    Write-Host ""
    Write-Host "Voir les détails avec: mvn clean package -DskipTests" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# ── Étape 4 : Informations de démarrage ────────────────────────────
Write-Host "[4/4] Prêt pour la présentation !" -ForegroundColor Green
Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║          COMMANDES POUR VOTRE PRÉSENTATION            ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "  Pour démarrer l'application :" -ForegroundColor White
Write-Host "  mvn spring-boot:run -Dspring-boot.run.profiles=dev" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Ou avec le JAR :" -ForegroundColor White
Write-Host "  java -jar target/gedavocat-app-1.0.0.jar --spring.profiles.active=dev" -ForegroundColor Cyan
Write-Host ""
Write-Host "  URL de l'application :" -ForegroundColor White
Write-Host "  http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
Write-Host "║                   INFORMATIONS                         ║" -ForegroundColor Yellow
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Base de données : MySQL (Docker)" -ForegroundColor Gray
Write-Host "  Port            : 3307" -ForegroundColor Gray
Write-Host "  Database        : doc_avocat" -ForegroundColor Gray
Write-Host "  Username        : doc_avocat" -ForegroundColor Gray
Write-Host ""
Write-Host "  Les emails ne seront pas envoyés (mode dev)" -ForegroundColor Gray
Write-Host "  Les paiements sont en mode test" -ForegroundColor Gray
Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Magenta
Write-Host "║              COMMANDES UTILES                          ║" -ForegroundColor Magenta
Write-Host "╚════════════════════════════════════════════════════════╝" -ForegroundColor Magenta
Write-Host ""
Write-Host "  Voir les logs MySQL       : docker-compose -f docker/docker-compose.yml logs -f mysql" -ForegroundColor Gray
Write-Host "  Arrêter MySQL             : docker-compose -f docker/docker-compose.yml stop mysql" -ForegroundColor Gray
Write-Host "  Redémarrer MySQL          : docker-compose -f docker/docker-compose.yml restart mysql" -ForegroundColor Gray
Write-Host "  État de MySQL             : .\check-mysql.ps1" -ForegroundColor Gray
Write-Host ""
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor White
Write-Host ""
Write-Host "Prêt pour votre présentation ! 🎯" -ForegroundColor Green
Write-Host ""
