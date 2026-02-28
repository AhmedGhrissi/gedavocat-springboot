@echo off
REM ===================================================================
REM Script de demarrage en mode LOCAL pour tests
REM ===================================================================

echo ============================================
echo  GED Avocat - Demarrage en mode LOCAL
echo ============================================
echo.

REM Verifier que MySQL est demarré
echo [1/3] Verification de MySQL...
mysql -u root -proot -e "SELECT 1" >nul 2>&1
if errorlevel 1 (
    echo [ERREUR] MySQL n'est pas demarré ou les identifiants sont incorrects
    echo Verifiez que MySQL tourne sur localhost:3306
    echo Username: root / Password: root
    pause
    exit /b 1
)
echo [OK] MySQL est accessible

REM Créer la base de données si elle n'existe pas
echo.
echo [2/3] Creation/verification de la base de donnees...
mysql -u root -proot -e "CREATE DATABASE IF NOT EXISTS gedavocat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>nul
if errorlevel 1 (
    echo [ERREUR] Impossible de creer la base de donnees
    pause
    exit /b 1
)
echo [OK] Base de donnees 'gedavocat' prete

REM Démarrer l'application avec le profil local
echo.
echo [3/3] Demarrage de l'application...
echo.
echo ============================================
echo  Mode: LOCAL (developpement)
echo  Port: 8081
echo  URL: http://localhost:8081
echo  Profile: local
echo ============================================
echo.
echo L'application va demarrer avec:
echo   - Logs en mode DEBUG
echo   - Cache Thymeleaf desactive
echo   - Stacktraces complets
echo   - Hot reload des templates
echo.
echo Appuyez sur Ctrl+C pour arreter l'application
echo.

REM Lancer Maven avec le profil local
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

pause
