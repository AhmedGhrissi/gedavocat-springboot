@echo off
REM Migration script for invoice_items table schema
REM Run this to update the database

echo ========================================
echo Migration du schema invoice_items
echo ========================================
echo.

set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_USER=root
set MYSQL_PASSWORD=root
set MYSQL_DB=gedavocat
set SQL_SCRIPT=%~dp0migrate_invoice_items_schema.sql

if not exist "%SQL_SCRIPT%" (
    echo ERROR: SQL script not found: %SQL_SCRIPT%
    pause
    exit /b 1
)

echo Ce script va migrer le schema invoice_items
echo.
set /p CONFIRM="Continuer? (O/N): "
if /i not "%CONFIRM%"=="O" (
    echo Operation annulee
    exit /b 0
)

echo.
echo Tentative de connexion MySQL...
echo.

REM Try mysql command
where mysql >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo MySQL command found, executing migration...
    mysql -h %MYSQL_HOST% -P %MYSQL_PORT% -u %MYSQL_USER% -p%MYSQL_PASSWORD% %MYSQL_DB% < "%SQL_SCRIPT%"
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ========================================
        echo SUCCESS! Migration completed
        echo ========================================
        echo.
        echo Please restart the Spring Boot application
        pause
        exit /b 0
    )
)

echo.
echo Could not execute migration automatically
echo.
echo Please use one of these options:
echo 1. MySQL Workbench - connect to localhost:3306 and run: %SQL_SCRIPT%
echo 2. Use the web migration endpoint POST http://localhost:8092/api/admin/migration/invoice-items-schema
echo 3. See MIGRATION_GUIDE_INVOICE_ITEMS.md for more options
echo.
pause
exit /b 1
