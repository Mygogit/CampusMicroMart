@echo off
chcp 65001 >nul
title 校园二手交易平台 - 启动
cd /d "%~dp0"

:: 检查是否有 PowerShell
where powershell >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未找到 PowerShell，请使用 Windows 10 或更高版本
    pause
    exit /b 1
)

echo ================================================
echo   校园二手交易平台 v1.0.0
echo ================================================
echo.
echo   启动模式选择:
echo     [1] 独立模式 (standalone) - 双击即用，无需额外服务
echo     [2] 开发模式 (dev) - 需 Docker 中间件
echo     [3] 强制重编译 + 独立模式
echo     [4] 停止所有服务
echo     [5] 查看端口状态
echo.
set /p choice="   请选择 [1-5] (默认 1): "

if "%choice%"=="" set choice=1

if "%choice%"=="1" (
    powershell -ExecutionPolicy Bypass -File "%~dp0start.ps1" -Mode standalone
)
if "%choice%"=="2" (
    powershell -ExecutionPolicy Bypass -File "%~dp0start.ps1" -Mode dev
)
if "%choice%"=="3" (
    powershell -ExecutionPolicy Bypass -File "%~dp0start.ps1" -Mode standalone -ForceRebuild
)
if "%choice%"=="4" (
    powershell -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
)
if "%choice%"=="5" (
    echo.
    echo === 端口状态 ===
    netstat -ano | findstr ":3306 :6379 :8080 :8081 :8083 :8084 :8085 :80 " 2>nul
    echo.
    echo === Java 进程 ===
    tasklist /FI "IMAGENAME eq javaw.exe" 2>nul
    echo.
    pause
)

echo.
pause
