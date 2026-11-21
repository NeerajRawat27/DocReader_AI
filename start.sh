#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

echo "=========================================="
echo " Starting DocReader.ai (Backend & Frontend)"
echo "=========================================="

# Load local .env if present (git-ignored)
if [ -f "$DIR/.env" ]; then
    set -a
    . "$DIR/.env"
    set +a
fi

# Java 17 binary
JAVA_BIN="/usr/lib/jvm/java-17-openjdk-amd64/bin/java"
if [ ! -f "$JAVA_BIN" ]; then
    JAVA_BIN="java"
fi

# Kill any lingering process on 8080 and 4200
fuser -k 8080/tcp 2>/dev/null || true
fuser -k 4200/tcp 2>/dev/null || true

JAR_FILE="$DIR/backend/target/docureader-ai-backend-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Building backend fat JAR..."
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -f "$DIR/backend/pom.xml" package -DskipTests
fi

echo "[1/2] Starting Spring Boot Backend on port 8080..."
nohup "$JAVA_BIN" -jar "$JAR_FILE" > "$DIR/backend.log" 2>&1 &
BACKEND_PID=$!
disown $BACKEND_PID 2>/dev/null || true
echo "Backend PID: $BACKEND_PID"

echo "Waiting for backend to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health | grep -q "UP"; then
        echo "✓ Backend is UP on http://localhost:8080"
        break
    fi
    sleep 1
done

echo "[2/2] Starting Angular Dev Server on port 4200..."
cd "$DIR/frontend"
nohup npm start > "$DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
disown $FRONTEND_PID 2>/dev/null || true
echo "Frontend PID: $FRONTEND_PID"

echo "Waiting for frontend to be ready..."
for i in {1..35}; do
    if curl -sI http://localhost:4200 >/dev/null 2>&1; then
        echo "✓ Frontend is ready on http://localhost:4200"
        break
    fi
    sleep 1
done

echo "=========================================="
echo " DocReader.ai is running!"
echo " Web UI:     http://localhost:4200/#demo"
echo " API Health: http://localhost:8080/api/health"
echo " Logs:       backend.log & frontend.log"
echo "=========================================="

if [ "$1" = "--wait" ] || [ "$1" = "-w" ]; then
    echo "Holding processes active (Ctrl+C or ./stop.sh to terminate)..."
    while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
        sleep 2
    done
fi
