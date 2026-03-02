# ============================================================
# DocAvocat - Production Deployment Script
# ============================================================

param(
    [switch]$SkipBuild,
    [switch]$SkipTests,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

# Couleurs
function Write-Success { Write-Host $args -ForegroundColor Green }
function Write-Info { Write-Host $args -ForegroundColor Cyan }
function Write-Warning { Write-Host $args -ForegroundColor Yellow }
function Write-Error { Write-Host $args -ForegroundColor Red }

Write-Host "=======================================================" -ForegroundColor Blue
Write-Host "  DocAvocat - Production Deployment" -ForegroundColor Blue
Write-Host "=======================================================" -ForegroundColor Blue
Write-Host ""

# -- Verification prerequis --------------------------------------
Write-Info "[1/6] Verification des prerequis..."

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "X Docker n'est pas installe"
    exit 1
}

if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Error "X Docker Compose n'est pas installe"
    exit 1
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "X Maven n'est pas installe"
    exit 1
}

Write-Success "✓ Docker, Docker Compose et Maven installes"

# -- Verification fichier .env -----------------------------------
Write-Info "`n[2/6] Verification configuration..."

$envFile = "docker/.env"
if (-not (Test-Path $envFile)) {
    Write-Warning "~ Fichier .env manquant"
    Write-Info "Creation depuis .env.example..."
    
    if (-not (Test-Path "docker/.env.example")) {
        Write-Error "X Fichier .env.example introuvable"
        exit 1
    }
    
    Copy-Item "docker/.env.example" $envFile
    
    Write-Warning ""
    Write-Warning "====================================================="
    Write-Warning "  ACTION REQUISE: Configurer docker/.env"
    Write-Warning "====================================================="
    Write-Warning ""
    Write-Warning "Editer docker/.env et remplir:"
    Write-Warning "  - MYSQL_ROOT_PASSWORD"
    Write-Warning "  - MYSQL_PASSWORD"
    Write-Warning "  - JWT_SECRET (generer avec: openssl rand -base64 64)"
    Write-Warning "  - MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD"
    Write-Warning "  - YOUSIGN_API_KEY"
    Write-Warning ""
    
    if (-not $Force) {
        Write-Error "X Configuration requise avant deploiement"
        Write-Info "Relancer avec -Force pour ignorer (NON RECOMMANDE)"
        exit 1
    }
}

Write-Success "✓ Fichier .env trouve"

# Verifier variables critiques
$envContent = Get-Content $envFile -Raw
$missingVars = @()

if ($envContent -match "CHANGE_ME") {
    Write-Warning "~ Variables non configurees detectees dans .env"
    if (-not $Force) {
        Write-Error "X Veuillez configurer toutes les variables dans docker/.env"
        exit 1
    }
}

# -- Build Maven -------------------------------------------------
if (-not $SkipBuild) {
    Write-Info "`n[3/6] Build Maven..."
    
    $mvnArgs = "clean", "package"
    if ($SkipTests) {
        $mvnArgs += "-DskipTests"
    }
    
    & mvn @mvnArgs
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "X Build Maven echoue"
        exit 1
    }
    
    Write-Success "✓ JAR genere: target/gedavocat-app-1.0.0.jar"
} else {
    Write-Info "`n[3/6] Build Maven ignore (-SkipBuild)"
}

# -- Build Docker image ------------------------------------------
Write-Info "`n[4/6] Build image Docker..."

if (-not (Test-Path "docker/Dockerfile")) {
    Write-Error "X Dockerfile introuvable dans docker/"
    exit 1
}

Push-Location docker
docker build -t docavocat-app:latest -f Dockerfile ..

if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Error "X Build Docker echoue"
    exit 1
}
Pop-Location

Write-Success "✓ Image Docker construite: docavocat-app:latest"

# -- Arret services existants ------------------------------------
Write-Info "`n[5/6] Gestion des services..."

Push-Location docker

$runningContainers = docker ps -q --filter "name=docavocat"
if ($runningContainers) {
    Write-Warning "~ Arret des conteneurs existants..."
    docker-compose down
    Start-Sleep -Seconds 3
}

# -- Demarrage docker-compose ------------------------------------
Write-Info "Demarrage des services..."

docker-compose up -d

if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Error "X Demarrage docker-compose echoue"
    exit 1
}

Pop-Location

Write-Success "✓ Services demarres"

# -- Verification sante ------------------------------------------
Write-Info "`n[6/6] Verification sante de l'application..."

$maxAttempts = 30
$attempt = 0
$healthUrl = "http://localhost:8080/actuator/health"

Write-Info "Attente du demarrage de l'application (max 60s)...`n"

while ($attempt -lt $maxAttempts) {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        
        if ($response.StatusCode -eq 200) {
            Write-Success "`n✓ Application demarree et operationnelle!"
            break
        }
    } catch {
        # Continuer a attendre
    }
    
    $attempt++
    Write-Host "." -NoNewline
    Start-Sleep -Seconds 2
}

if ($attempt -ge $maxAttempts) {
    Write-Error "`nX L'application n'a pas demarre dans les temps"
    Write-Info "Verifier les logs: docker logs docavocat-app"
    exit 1
}

# -- Resume ------------------------------------------------------
Write-Host ""
Write-Host "=======================================================" -ForegroundColor Green
Write-Host "  ✓ Deployment Production Successful!" -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green
Write-Host ""
Write-Success "Services actifs:"
Write-Host "  - Application:     http://localhost:8080"
Write-Host "  - MySQL:           localhost:3307"
Write-Host "  - Prometheus:      http://localhost:9090"
Write-Host "  - Grafana:         http://localhost:3000 (admin/GRAFANA_PASSWORD)"
Write-Host ""
Write-Info "Commandes utiles:"
Write-Host "  - Logs:            docker logs -f docavocat-app"
Write-Host "  - Status:          docker ps"
Write-Host "  - Restart:         docker-compose -f docker/docker-compose.yml restart app"
Write-Host "  - Arret:           docker-compose -f docker/docker-compose.yml down"
Write-Host ""
Write-Success 'Deploiement termine!'


