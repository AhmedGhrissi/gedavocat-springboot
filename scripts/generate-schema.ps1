# ===================================================================
# Script PowerShell : Génération du schéma SQL pour initialisation BDD
# ===================================================================

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Génération du schéma SQL depuis les entités JPA" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Créer le dossier init s'il n'existe pas
$initDir = "docker\init"
if (-not (Test-Path $initDir)) {
    New-Item -ItemType Directory -Path $initDir | Out-Null
    Write-Host "✓ Dossier $initDir créé" -ForegroundColor Green
}

# Configuration temporaire pour générer le schéma
$tempConfig = @"
spring.datasource.url=jdbc:h2:mem:schema_gen
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create
spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create
spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target=docker/init/01-schema-generated.sql
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
"@

Write-Host "[1/3] Création du fichier de configuration temporaire..." -ForegroundColor Yellow
$tempFile = "src\main\resources\application-schema-gen.properties"
$tempConfig | Out-File -FilePath $tempFile -Encoding UTF8

Write-Host "[2/3] Démarrage de l'application pour générer le schéma..." -ForegroundColor Yellow
Write-Host "      (Cela peut prendre 30-60 secondes...)" -ForegroundColor Gray
Write-Host ""

# Lancer Spring Boot avec le profil de génération
$env:SPRING_PROFILES_ACTIVE = "schema-gen"
mvn spring-boot:run -Dspring-boot.run.profiles=schema-gen -Dspring-boot.run.jvmArguments="-Dspring.main.web-application-type=none" 2>&1 | Out-Null

# Nettoyer
Remove-Item $tempFile -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = ""

Write-Host ""
Write-Host "[3/3] Vérification du schéma généré..." -ForegroundColor Yellow

if (Test-Path "docker\init\01-schema-generated.sql") {
    Write-Host "✓ Schéma généré avec succès !" -ForegroundColor Green
    Write-Host ""
    Write-Host "Fichier : docker\init\01-schema-generated.sql" -ForegroundColor Cyan
    Write-Host ""
    
    # Afficher un aperçu
    Write-Host "───── Aperçu (premières lignes) ─────" -ForegroundColor Gray
    Get-Content "docker\init\01-schema-generated.sql" -TotalCount 20
    Write-Host "───────────────────────────────────────" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Pour utiliser ce schéma avec Docker :" -ForegroundColor Yellow
    Write-Host "  1. Décommentez la ligne dans docker-compose.yml :" -ForegroundColor White
    Write-Host "     - ./init/01-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro" -ForegroundColor Gray
    Write-Host "  2. Renommez le fichier généré en 01-schema.sql" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "✗ Échec de la génération" -ForegroundColor Red
    Write-Host ""
    Write-Host "Essayez la méthode manuelle :" -ForegroundColor Yellow
    Write-Host "  1. Lancez : mvn spring-boot:run -Dspring-boot.run.profiles=dev" -ForegroundColor White
    Write-Host "  2. Une fois démarré, les tables seront créées automatiquement" -ForegroundColor White
    Write-Host "  3. Exportez le schéma SQL avec : mysqldump" -ForegroundColor White
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
