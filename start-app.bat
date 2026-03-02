@echo off
echo ========================================
echo   GEDAVOCAT - Lancement Application
echo ========================================
echo.

echo [1/3] Arret des processus Java en cours...
taskkill /F /IM java.exe 2>nul
timeout /t 2 /nobreak >nul

echo.
echo [2/3] Nettoyage du port 8092...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8081') do taskkill /F /PID %%a 2>nul
timeout /t 1 /nobreak >nul

echo.
echo [3/3] Demarrage de l'application...
echo.
echo ========================================
echo   L'application demarre sur le port 8092
echo   Acces: http://localhost:8092
echo ========================================
echo.

cd /d "%~dp0"
call mvn spring-boot:run -Dspring-boot.run.profiles=h2

pause
