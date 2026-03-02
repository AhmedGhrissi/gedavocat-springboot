@echo off
REM ============================================================
REM DocAvocat - Production Deployment
REM ============================================================

echo ====================================================
echo   DocAvocat - Production Deployment
echo ====================================================
echo.

REM Check .env file
if not exist "docker\.env" (
    echo [WARNING] docker\.env not found
    echo Creating from .env.example...
    copy docker\.env.example docker\.env
    echo.
    echo ====================================================
    echo   ACTION REQUIRED: Configure docker\.env
    echo ====================================================
    echo.
    echo Edit docker\.env and configure:
    echo   - MYSQL_ROOT_PASSWORD
    echo   - MYSQL_PASSWORD
    echo   - JWT_SECRET
    echo   - MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD
    echo   - YOUSIGN_API_KEY
    echo.
    pause
    exit /b 1
)

echo [1/4] Building Maven JAR...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Maven build failed
    pause
    exit /b 1
)
echo [OK] JAR created

echo.
echo [2/4] Building Docker image...
cd docker
docker build -t docavocat-app:latest -f Dockerfile ..
if errorlevel 1 (
    cd ..
    echo [ERROR] Docker build failed
    pause
    exit /b 1
)
cd ..
echo [OK] Docker image built

echo.
echo [3/4] Starting services...
cd docker
docker-compose down
docker-compose up -d
if errorlevel 1 (
    cd ..
    echo [ERROR] docker-compose failed
    pause
    exit /b 1
)
cd ..
echo [OK] Services started

echo.
echo [4/4] Waiting for application to start...
timeout /t 30 /nobreak >nul


powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
if errorlevel 1 (
    echo [WARNING] Application may not be ready yet
    echo Check logs: docker logs docavocat-app
) else (
    echo [OK] Application is running
)

echo.
echo ====================================================
echo   Deployment Complete!
echo ====================================================
echo.
echo Services:
echo   - Application:  http://localhost:8080
echo   - MySQL:        localhost:3307
echo   - Prometheus:   http://localhost:9090
echo   - Grafana:      http://localhost:3000
echo.
echo Commands:
echo   - Logs:         docker logs -f docavocat-app
echo   - Status:       docker ps
echo   - Restart:      cd docker ^&^& docker-compose restart app
echo   - Stop:         cd docker ^&^& docker-compose down
echo.
pause
