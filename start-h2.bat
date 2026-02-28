@echo off
REM ===================================================================
REM Script de demarrage avec H2 (Base de donnees en memoire)
REM Aucun MySQL requis !
REM ===================================================================

echo ============================================
echo  GED Avocat - Mode H2 (en memoire)
echo ============================================
echo.
echo [INFO] H2 Database - Aucun MySQL requis
echo [INFO] Base de donnees creee automatiquement
echo [INFO] Les donnees seront perdues a l'arret
echo.
echo ============================================
echo  Mode: H2 (base en memoire)
echo  Port: 8081
echo  URL: http://localhost:8081
echo  Console H2: http://localhost:8081/h2-console
echo ============================================
echo.
echo Demarrage de l'application...
echo.

REM Se déplacer dans le répertoire du projet
cd /d %~dp0

REM Lancer Maven avec le profil h2
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2

pause
