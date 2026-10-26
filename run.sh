#!/usr/bin/env bash
# ==============================================================================
# SmartOPD - One-Click Launch Script for macOS and Linux
# ==============================================================================
# This script checks prerequisites, builds the self-contained executable jar
# if missing or modified, starts the server from backend/, waits for health,
# opens the default browser, and displays local and LAN phone tracking URLs.
# ==============================================================================
set -e

# Ensure working directory is repo root regardless of launch location
cd "$(dirname "$0")"

# Step 1: Check java is installed and available on PATH
if ! command -v java >/dev/null 2>&1; then
    echo "[ERROR] Java is not installed or not found on PATH." >&2
    echo "Please install JDK 17 or newer and ensure 'java' is accessible." >&2
    exit 1
fi

# Step 2: Check mvn is installed and available on PATH
if ! command -v mvn >/dev/null 2>&1; then
    echo "[ERROR] Maven (mvn) is not installed or not found on PATH." >&2
    echo "Please install Apache Maven 3.8+ and ensure 'mvn' is accessible." >&2
    exit 1
fi

# Step 3: Determine if build is needed (-rebuild flag, missing jar, or newer source files)
NEEDS_BUILD=0
if [ "$1" = "-rebuild" ] || [ ! -f "backend/target/smartopd.jar" ]; then
    NEEDS_BUILD=1
else
    JAR_FILE="backend/target/smartopd.jar"
    if find backend/src frontend -type f -newer "$JAR_FILE" 2>/dev/null | grep -q .; then
        NEEDS_BUILD=1
    fi
fi

# Step 4: Build self-contained JAR if needed (skipping tests for fast one-click start)
if [ $NEEDS_BUILD -eq 1 ]; then
    echo "[SmartOPD] Building self-contained executable jar (smartopd.jar)..."
    (cd backend && mvn clean package -DskipTests)
    if [ ! -f "backend/target/smartopd.jar" ]; then
        echo "[ERROR] Build failed: backend/target/smartopd.jar was not generated." >&2
        exit 1
    fi
    echo "[SmartOPD] Build successful!"
else
    echo "[SmartOPD] Up-to-date smartopd.jar found. Skipping rebuild."
fi

# Step 5: Detect local network IP for mobile phones
LAN_IP=""
if command -v hostname >/dev/null 2>&1 && hostname -I >/dev/null 2>&1; then
    LAN_IP=$(hostname -I | awk '{print $1}')
elif command -v ipconfig >/dev/null 2>&1; then
    LAN_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)
fi

# Step 6: Start server from backend/ directory so database lands in backend/data/
echo "[SmartOPD] Starting SmartOPD server from backend folder..."
(cd backend && exec java -jar target/smartopd.jar) &
SERVER_PID=$!
trap "kill $SERVER_PID 2>/dev/null || true" EXIT

# Step 7: Poll health endpoint up to 40 seconds
echo "[SmartOPD] Waiting for server health on http://localhost:8080/api/public/board..."
WAITED=0
while [ $WAITED -lt 40 ]; do
    if curl -s -f http://localhost:8080/api/public/board >/dev/null 2>&1; then
        break
    fi
    sleep 1
    WAITED=$((WAITED + 1))
done

if [ $WAITED -ge 40 ]; then
    echo "[ERROR] Server did not become healthy within 40 seconds." >&2
    exit 1
fi

echo "[SmartOPD] Server is up and ready!"
echo ""
echo "================================================================================"
echo "  SmartOPD Application Ready"
echo "================================================================================"
echo "  Local Web URL:  http://localhost:8080/login.html"
if [ -n "$LAN_IP" ]; then
    echo "  LAN Mobile:     http://${LAN_IP}:8080/login.html"
    echo "  Mobile Tracker: http://${LAN_IP}:8080/patient-tracker.html?guest=true"
fi
echo "  Display Board:  http://localhost:8080/display-board.html"
echo ""
echo "  First Start:    On first start open the app and create the Front Desk account"
echo "                  on the setup page, then add departments and doctors on the Doctors"
echo "                  page, then create doctor logins on the Staff page."
echo ""
echo "  To stop the server: Press Ctrl+C"
echo "================================================================================"
echo ""

# Step 8: Open default browser
if command -v open >/dev/null 2>&1; then
    open "http://localhost:8080/login.html"
elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "http://localhost:8080/login.html"
fi

wait $SERVER_PID
