# Migration script for invoice_items table
# Run this to update the database schema

$MYSQL_HOST = "localhost"
$MYSQL_PORT = "3306"
$MYSQL_USER = "root"
$MYSQL_PASSWORD = "root"
$MYSQL_DB = "gedavocat"
$SQL_SCRIPT = "$PSScriptRoot\migrate_invoice_items_schema.sql"

Write-Host "========================================"
Write-Host "Migration du schema invoice_items"
Write-Host "========================================"
Write-Host ""

if (-Not (Test-Path $SQL_SCRIPT)) {
    Write-Host "ERROR: Script SQL not found: $SQL_SCRIPT"
    exit 1
}

$confirmation = Read-Host "Continue with migration? (Y/N)"
if ($confirmation -ne 'Y' -and $confirmation -ne 'y' -and $confirmation -ne 'O' -and $confirmation -ne 'o') {
    Write-Host "Cancelled"
    exit 0
}

$success = $false

# Try mysql command
$mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
if ($mysqlCmd) {
    Write-Host "Using MySQL command line..."
    Get-Content $SQL_SCRIPT | & mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p"$MYSQL_PASSWORD" $MYSQL_DB
    if ($LASTEXITCODE -eq 0) {
        $success = $true
    }
}

# Try Docker
if (-not $success) {
    try {
        $container = docker ps --filter "name=mysql" --format "{{.Names}}" 2>$null | Select-Object -First 1
        if ($container) {
            Write-Host "Using Docker container: $container"
            Get-Content $SQL_SCRIPT | docker exec -i $container mysql -u $MYSQL_USER -p"$MYSQL_PASSWORD" $MYSQL_DB
            if ($LASTEXITCODE -eq 0) {
                $success = $true
            }
        }
    } catch {}
}

if ($success) {
    Write-Host ""
    Write-Host "SUCCESS! Migration completed"
    Write-Host "Please restart the Spring Boot application"
    exit 0
} else {
    Write-Host ""
    Write-Host "FAILED: Could not run migration automatically"
    Write-Host ""
    Write-Host "Please use one of these options:"
    Write-Host "1. MySQL Workbench - connect to localhost:3306 and run: $SQL_SCRIPT"
    Write-Host "2. Use the web migration endpoint (see MIGRATION_GUIDE_INVOICE_ITEMS.md)"
    Write-Host ""
    exit 1
}
