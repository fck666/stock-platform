@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo    Stock Platform One-Click Launcher
echo ==========================================

REM 0. Check & Start Docker Desktop
echo [0/3] Checking Docker status...
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo 🐳 Docker is not running. Attempting to start Docker Desktop...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if %errorlevel% neq 0 (
        echo ❌ Failed to launch Docker Desktop. Please start it manually or check installation path.
        pause
        exit /b 1
    )
    echo ⏳ Waiting for Docker to start (this may take a minute)...
    :wait_docker
    timeout /t 2 /nobreak >nul
    docker info >nul 2>&1
    if %errorlevel% neq 0 (
        goto wait_docker
    )
    echo ✅ Docker started successfully!
) else (
    echo ✅ Docker is already running.
)

REM 1. Start Database (Test Environment)
echo [1/3] Starting Database (Docker - Test Env)...
docker compose -f docker/postgresql/compose.test.yml up -d
if %errorlevel% neq 0 (
    echo ❌ Failed to start database. Please check Docker Desktop is running.
    pause
    exit /b %errorlevel%
)
echo ✅ Database (Test) started successfully on port 5433.

REM 2. Start Backend
echo [2/3] Starting Backend (Spring Boot)...
start "StockPlatform Backend" cmd /c "cd backend-api && mvnw.cmd spring-boot:run"
echo ✅ Backend launching in new window...

REM 3. Start Frontend
echo [3/3] Starting Frontend (Vue)...
start "StockPlatform Frontend" cmd /c "cd frontend-web && npm run dev"
echo ✅ Frontend launching in new window...

echo ==========================================
echo 🚀 All services launched!
echo    - Backend will be at: http://localhost:8080
echo    - Frontend will be at: http://localhost:5173
echo ==========================================
pause
