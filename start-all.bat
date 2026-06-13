@echo off
chcp 65001 >nul
set "ROOT=e:\数据\CampusMicroMart"
cd /d "%ROOT%"

echo ==========================================
echo   CampusMicroMart - Build & Start
echo ==========================================
echo.

REM Step 1: Build all modules except gateway
echo [1/3] Building services...
call mvn clean package -DskipTests -pl campus-common,user-service,product-service,order-service,payment-service -q
if %ERRORLEVEL% neq 0 (
    echo        BUILD FAILED! Check errors above.
    pause
    exit /b 1
)
echo        Build OK.

REM Step 2: Build gateway separately
echo [2/3] Building gateway...
call mvn clean package -DskipTests -pl campus-gateway -q
if %ERRORLEVEL% neq 0 (
    echo        Gateway build failed (may be locked). Trying without clean...
    call mvn package -DskipTests -pl campus-gateway -q
)
echo        Gateway build OK.

REM Step 3: Kill any existing Java processes
echo [3/3] Stopping old processes...
taskkill /F /IM java.exe 2>nul
timeout /t 3 /nobreak >nul
echo        Done.

REM Step 4: Start all services
echo.
echo ==========================================
echo   Starting services...
echo ==========================================

start "user-svc:8084" cmd /c "cd /d %ROOT%\user-service\target && java -jar user-service-1.0.0.jar > %ROOT%\logs\user-service.log 2>&1"
echo   user-service (8084) - started

start "product-svc:8081" cmd /c "cd /d %ROOT%\product-service\target && java -jar product-service-1.0.0.jar > %ROOT%\logs\product-service.log 2>&1"
echo   product-service (8081) - started

timeout /t 8 /nobreak >nul

start "order-svc:8082" cmd /c "cd /d %ROOT%\order-service\target && java -jar order-service-1.0.0.jar > %ROOT%\logs\order-service.log 2>&1"
echo   order-service (8082) - started

start "payment-svc:8083" cmd /c "cd /d %ROOT%\payment-service\target && java -jar payment-service-1.0.0.jar > %ROOT%\logs\payment-service.log 2>&1"
echo   payment-service (8083) - started

timeout /t 15 /nobreak >nul

start "gateway:8080" cmd /c "cd /d %ROOT%\campus-gateway\target && java -jar campus-gateway-1.0.0.jar > %ROOT%\logs\gateway.log 2>&1"
echo   gateway (8080) - started

echo.
echo ==========================================
echo   All done! URLs:
echo     http://localhost:8080  (gateway)
echo     http://localhost:8081  (product)
echo     http://localhost:8082  (order)
echo     http://localhost:8083  (payment)
echo     http://localhost:8084  (user)
echo ==========================================
pause
