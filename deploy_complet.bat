@echo off
REM ============================================================================
REM Script de déploiement complet GedAvoCat
REM Date: 2026-03-06
REM ============================================================================

echo ============================================================================
echo DEPLOIEMENT COMPLET GEDAVOCAT
echo ============================================================================
echo.

REM Configuration
set DB_NAME=gedavocat
set DB_USER=root

REM Demander le mot de passe
set /p DB_PASSWORD="Mot de passe MySQL (root): "

echo.
echo [1/3] Importation du dump principal...
mysql -u %DB_USER% -p%DB_PASSWORD% %DB_NAME% < dump_gedavocat_prod_20260305_backup.sql
if %ERRORLEVEL% NEQ 0 (
    echo ERREUR lors de l'importation du dump principal!
    pause
    exit /b 1
)
echo     OK - Dump principal importe

echo.
echo [2/3] Ajout des utilisateurs admin et client...
mysql -u %DB_USER% -p%DB_PASSWORD% %DB_NAME% < add_admin_client_users.sql
if %ERRORLEVEL% NEQ 0 (
    echo ERREUR lors de l'ajout des utilisateurs!
    pause
    exit /b 1
)
echo     OK - Utilisateurs ajoutes

echo.
echo [3/3] Verification...
mysql -u %DB_USER% -p%DB_PASSWORD% %DB_NAME% -e "SELECT id, email, CONCAT(first_name, ' ', last_name) AS nom, role FROM users WHERE id IN ('admin-super-001', 'client-demo-002');"

echo.
echo ============================================================================
echo DEPLOIEMENT TERMINE AVEC SUCCES!
echo ============================================================================
echo.
echo Utilisateurs crees:
echo.
echo   1. Super Admin
echo      Email: superadmin@gedavocat.fr
echo      Mot de passe: Test1234!
echo      Role: ADMIN
echo.
echo   2. Client Demo  
echo      Email: client.demo@gedavocat.fr
echo      Mot de passe: Test1234!
echo      Role: CLIENT
echo.
echo ============================================================================
pause
