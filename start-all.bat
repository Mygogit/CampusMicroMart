@echo off
chcp 65001 >nul
set "ROOT=%~dp0"
cd /d "%ROOT%"

REM ------------------------------------------------------------------
REM Build and start the local standalone stack, SILENTLY.
REM
REM This file used to spawn five real console windows, one per service:
REM     start "user-svc:8084" cmd /c "cd /d %ROOT%\user-service\target && java -jar ..."
REM and it killed EVERY java.exe on the machine (taskkill /F /IM java.exe),
REM which also took down unrelated Java apps.
REM
REM It now delegates to start-background-wmi.ps1, the canonical local
REM launcher, which:
REM   * creates each process via Win32_Process.Create -> no console window
REM     at all (not even a hidden one)
REM   * remaps product-service to 8082 and gateway to 8090, so the stack
REM     can coexist with the SQB containers that hold 8081/8080
REM   * is idempotent and never touches foreign Java processes
REM
REM Original file kept as: start-all.bat.bak-20260923
REM ------------------------------------------------------------------

echo ==========================================
echo   CampusMicroMart - Build and Start
echo   silent mode: no console window will pop up
echo ==========================================
echo.

echo [1/2] Building services...
call mvn clean package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo        BUILD FAILED! Check errors above.
    pause
    exit /b 1
)
echo        Build OK.
echo.

echo [2/2] Starting services silently...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%start-background-wmi.ps1"
echo.
pause
