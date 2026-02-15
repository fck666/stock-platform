#!/bin/bash

# Get the directory where the script is located
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$BASE_DIR"

echo "=========================================="
echo "   Stock Platform One-Click Launcher"
echo "=========================================="

# 0. Check & Start Docker Desktop
echo "[0/3] Checking Docker status..."
if ! docker info > /dev/null 2>&1; then
    echo "🐳 Docker is not running. Attempting to start Docker Desktop..."
    open -a Docker
    if [ $? -ne 0 ]; then
        echo "❌ Failed to launch Docker Desktop. Please start it manually."
        exit 1
    fi
    echo "⏳ Waiting for Docker to start (this may take a minute)..."
    while ! docker info > /dev/null 2>&1; do
        sleep 2
        printf "."
    done
    echo ""
    echo "✅ Docker started successfully!"
else
    echo "✅ Docker is already running."
fi

# 1. Start Database (Test Environment)
echo "[1/3] Starting Database (Docker - Test Env)..."
if command -v docker >/dev/null 2>&1; then
    docker compose -f docker/postgresql/compose.test.yml up -d
    if [ $? -eq 0 ]; then
        echo "✅ Database (Test) started successfully on port 5433."
    else
        echo "❌ Failed to start database. Please check Docker Desktop is running."
        exit 1
    fi
else
    echo "❌ Docker not found. Please install Docker Desktop."
    exit 1
fi

# 2. Start Backend
echo "[2/3] Starting Backend (Spring Boot)..."
# Ensure mvnw is executable
chmod +x "$BASE_DIR/backend-api/mvnw"
# Open a new Terminal window for Backend
osascript -e "tell application \"Terminal\" to do script \"cd \\\"$BASE_DIR/backend-api\\\" && ./mvnw spring-boot:run\""
echo "✅ Backend launching in new terminal..."

# 3. Start Frontend
echo "[3/3] Starting Frontend (Vue)..."
# Open a new Terminal window for Frontend
osascript -e "tell application \"Terminal\" to do script \"cd \\\"$BASE_DIR/frontend-web\\\" && npm run dev\""
echo "✅ Frontend launching in new terminal..."

echo "=========================================="
echo "🚀 All services launched!"
echo "   - Backend will be at: http://localhost:8080"
echo "   - Frontend will be at: http://localhost:5173"
echo "=========================================="
