@echo off
chcp 936 >nul
setlocal enabledelayedexpansion
title 校园二手交易平台 - 停止中...
cd /d "%~dp0"

set HERE=%~dp0.
set HIDDEN=%~dp0_hidden.ps1

echo ================================================
echo   正在停止校园二手交易平台...
echo ================================================
echo.

if not exist "%HIDDEN%" (
    echo [错误] 缺少 _hidden.ps1，无法执行停止操作！
    pause
    exit /b 1
)

echo [1/4] 停止前端、网关与业务微服务...
powershell -NoProfile -ExecutionPolicy Bypass -File "%HIDDEN%" -Stop

echo [2/4] 停止 Redis...
"%~dp0redis\redis-cli.exe" -p 6379 SHUTDOWN 2>nul
timeout /t 1 /nobreak >nul

echo [3/4] 停止 MariaDB...
"%~dp0mariadb\bin\mysqladmin.exe" -u root -pcampus123 -h 127.0.0.1 shutdown 2>nul
timeout /t 2 /nobreak >nul

echo [4/4] 清理残留进程...
powershell -NoProfile -ExecutionPolicy Bypass -File "%HIDDEN%" -Stop -Force -Root "%HERE%"

echo.
echo ================================================
echo   端口状态：
echo ================================================
call :chk 3306 MariaDB
call :chk 6379 Redis
call :chk 8084 user-service
call :chk 8081 product-service
call :chk 8083 order-service
call :chk 8085 payment-service
call :chk 8080 campus-gateway
call :chk 80   frontend
echo.
echo   所有服务已停止，可以安全关闭此窗口
echo   数据保存在 .\data\ 目录，下次启动自动恢复
echo ================================================
echo.
pause
exit /b 0

:chk
netstat -ano | findstr ":%~1 " >nul 2>&1
if !errorlevel! equ 0 (
    echo    [仍在运行]  %~1  %~2
) else (
    echo    [已停止]    %~1  %~2
)
goto :eof
