@echo off
rem ==============================================================================
rem SmartOPD - One-Click Launch Script for Windows
rem ==============================================================================
rem This script checks prerequisites, builds the self-contained executable jar
rem if missing or modified, starts the server from backend/, waits for health,
rem opens the default browser, and displays local and LAN phone tracking URLs.
rem ==============================================================================

setlocal enabledelayedexpansion

rem Ensure working directory is repo root regardless of launch location
cd /d "%~dp0"

rem Step 1: Check java is installed and available on PATH
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not found on PATH.
    echo Please install JDK 17 or newer and ensure 'java' is accessible in Command Prompt.
    echo Download JDK: https://adoptium.net/
    pause
    exit /b 1
)

rem Step 2: Check mvn is installed and available on PATH
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Maven is not installed or not found on PATH.
    echo Please install Apache Maven 3.8+ and add its bin folder to PATH.
    echo Download Maven: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

rem Step 3: Determine if build is needed (-rebuild flag, missing jar, or newer source files)
set NEEDS_BUILD=0
if /i "%1"=="-rebuild" set NEEDS_BUILD=1
if /i "%1"=="/rebuild" set NEEDS_BUILD=1
if not exist "backend\target\smartopd.jar" set NEEDS_BUILD=1

if %NEEDS_BUILD% equ 1 goto RUN_BUILD

powershell -NoProfile -Command "if (-not (Test-Path 'backend\target\smartopd.jar')) { exit 1 }; $t = (Get-Item 'backend\target\smartopd.jar').LastWriteTime; if (Get-ChildItem -Path backend\src, frontend -Recurse -File | Where-Object { $_.LastWriteTime -gt $t }) { exit 1 } else { exit 0 }" >nul 2>nul
if errorlevel 1 set NEEDS_BUILD=1

:RUN_BUILD
rem Step 4: Build self-contained JAR if needed (skipping tests for fast one-click start)
if %NEEDS_BUILD% equ 0 (
    echo [SmartOPD] Up-to-date smartopd.jar found. Skipping rebuild.
    goto LAUNCH_SERVER
)

echo [SmartOPD] Building self-contained executable jar - smartopd.jar...
pushd backend
call mvn clean package -DskipTests
popd
if not exist "backend\target\smartopd.jar" (
    echo [ERROR] Build failed: backend\target\smartopd.jar was not generated.
    pause
    exit /b 1
)
echo [SmartOPD] Build successful!

:LAUNCH_SERVER
rem Step 5: Detect LAN IPv4 address for mobile access
set LAN_IP=
powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object InterfaceAlias -notmatch 'Loopback|vEthernet|Virtual' | Where-Object IPAddress -notlike '169.254*' | Where-Object IPAddress -notlike '127*').IPAddress | Select-Object -First 1" > "%temp%\smartopd_lan_ip.txt" 2>nul
if exist "%temp%\smartopd_lan_ip.txt" (
    set /p LAN_IP=<"%temp%\smartopd_lan_ip.txt"
    del "%temp%\smartopd_lan_ip.txt" 2>nul
)

rem Step 6: Start the SmartOPD JAR from backend/ folder (so SQLite DB lands in backend/data/smartopd.db)
echo [SmartOPD] Starting SmartOPD server from backend folder...
pushd backend
start "SmartOPD Server" java -jar target\smartopd.jar
popd

rem Step 7: Poll health endpoint (http://localhost:8080/api/public/board) up to 40 seconds
echo [SmartOPD] Waiting for server to initialize on http://localhost:8080...
set WAITED=0

:POLL_LOOP
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/public/board' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>nul
if %errorlevel% equ 0 goto SERVER_READY

ping 127.0.0.1 -n 2 >nul
set /a WAITED+=1
if %WAITED% geq 40 (
    echo [ERROR] Server did not become healthy within 40 seconds.
    echo Please check the "SmartOPD Server" console window for details or port 8080 conflicts.
    pause
    exit /b 1
)
goto POLL_LOOP

:SERVER_READY
echo [SmartOPD] Server is up and ready!
echo.
echo ================================================================================
echo   SmartOPD Application Ready
echo ================================================================================
echo   Local Web URL:  http://localhost:8080/login.html
if defined LAN_IP (
    echo   LAN Mobile:     http://%LAN_IP%:8080/login.html
    echo   Mobile Tracker: http://%LAN_IP%:8080/patient-tracker.html?guest=true
)
echo   Display Board:  http://localhost:8080/display-board.html
echo.
echo   First Start:    On first start open the app and create the Front Desk account
echo                   on the setup page, then add departments and doctors on the Doctors
echo                   page, then create doctor logins on the Staff page.
echo.
echo   To stop the server: Close or press Ctrl+C in the "SmartOPD Server" window.
echo ================================================================================
echo.

rem Step 8: Open default browser to login page
echo [SmartOPD] Opening login page in default browser...
start http://localhost:8080/login.html

endlocal
