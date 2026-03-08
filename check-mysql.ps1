# Script de vérification MySQL Docker
# Usage: .\check-mysql.ps1

Write-Host "=== Vérification MySQL Docker ===" -ForegroundColor Cyan
Write-Host ""

# 1. Vérifier si Docker est démarré
Write-Host "[1/5] Vérification Docker Desktop..." -ForegroundColor Yellow
try {
    docker version | Out-Null
    Write-Host "✓ Docker est démarré" -ForegroundColor Green
} catch {
    Write-Host "✗ Docker n'est pas démarré ou pas installé" -ForegroundColor Red
    Write-Host "    → Lancez Docker Desktop et réessayez" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 2. Vérifier si le conteneur existe
Write-Host "[2/5] Vérification du conteneur MySQL..." -ForegroundColor Yellow
$container = docker ps -a --filter "name=docavocat-mysql" --format "{{.Names}}"
if ($container -eq "docavocat-mysql") {
    Write-Host "✓ Conteneur docavocat-mysql trouvé" -ForegroundColor Green
    
    # Vérifier s'il tourne
    $status = docker ps --filter "name=docavocat-mysql" --format "{{.Status}}"
    if ($status) {
        Write-Host "  Status: $status" -ForegroundColor Green
    } else {
        Write-Host "✗ Le conteneur existe mais n'est pas démarré" -ForegroundColor Red
        Write-Host "    → Exécutez: docker-compose up -d mysql" -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "✗ Conteneur MySQL non trouvé" -ForegroundColor Red
    Write-Host "    → Exécutez: cd docker; docker-compose up -d mysql" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 3. Vérifier le healthcheck
Write-Host "[3/5] Vérification du healthcheck..." -ForegroundColor Yellow
Start-Sleep -Seconds 2
$health = docker inspect docavocat-mysql --format='{{.State.Health.Status}}' 2>$null
if ($health -eq "healthy") {
    Write-Host "✓ MySQL est healthy" -ForegroundColor Green
} elseif ($health -eq "starting") {
    Write-Host "⏳ MySQL est en cours de démarrage... Attendez 10-30 secondes" -ForegroundColor Yellow
} else {
    Write-Host "⚠ Status: $health (peut être normal si vient de démarrer)" -ForegroundColor Yellow
}

Write-Host ""

# 4. Tester la connexion
Write-Host "[4/5] Test de connexion à la base..." -ForegroundColor Yellow
$testQuery = docker exec docavocat-mysql mysql -u doc_avocat -p'DocAvocat2026!DevDB' -e "SELECT 1 AS test;" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Connexion réussie avec l'utilisateur 'doc_avocat'" -ForegroundColor Green
} else {
    Write-Host "✗ Impossible de se connecter" -ForegroundColor Red
    Write-Host "    Erreur: $testQuery" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 5. Lister les bases de données
Write-Host "[5/5] Bases de données disponibles:" -ForegroundColor Yellow
docker exec docavocat-mysql mysql -u doc_avocat -p'DocAvocat2026!DevDB' -e "SHOW DATABASES;" 2>$null

Write-Host ""
Write-Host "=== Résumé ===" -ForegroundColor Cyan
Write-Host "✓ MySQL fonctionne correctement" -ForegroundColor Green
Write-Host ""
Write-Host "Identifiants de connexion:" -ForegroundColor White
Write-Host "  Host:     localhost" -ForegroundColor Gray
Write-Host "  Port:     3307" -ForegroundColor Gray
Write-Host "  Database: doc_avocat" -ForegroundColor Gray
Write-Host "  Username: doc_avocat" -ForegroundColor Gray
Write-Host "  Password: DocAvocat2026!DevDB" -ForegroundColor Gray
Write-Host ""
Write-Host "URL JDBC: jdbc:mysql://localhost:3307/doc_avocat" -ForegroundColor Gray
Write-Host ""
