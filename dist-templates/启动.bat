@echo off
chcp 936 >nul
setlocal enabledelayedexpansion
title 校园二手交易平台 - 启动中...
cd /d "%~dp0"

set JAVA_HOME=%~dp0jdk
set PATH=%JAVA_HOME%\bin;%PATH%

set MARIADB_HOME=%~dp0mariadb
set REDIS_HOME=%~dp0redis
set DATA_DIR=%~dp0data
set LOG_DIR=%~dp0logs
set HERE=%~dp0.
set GATEWAY_CLASSES=app\gateway-classes

REM 所有进程均通过 _hidden.ps1 以 CreateNoWindow 方式拉起，不会出现额外的命令提示符窗口
set LAUNCH=powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0_hidden.ps1"

REM 清空外部注入的端口变量，避免覆盖 application.yml 中的 server.port
set SERVER__PORT=
set SERVER_PORT=

REM ============================================================
REM 维护注意：if / for 块内部的 echo 文本里不得出现半角圆括号。cmd 在读取复合
REM 语句时会先做括号配对扫描，块内任何半角右括号都会提前闭合该块，随后剩余的
REM 文本被当成新命令，报「此时不应有 xxx。」并使整个块失效。用 ^ 转义并不可靠。
REM 因此本文件在 echo 文本中统一使用全角括号。
REM ============================================================

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM === 检查依赖文件完整性 ===
if not exist "%MARIADB_HOME%\bin\mysqld.exe" (
    echo [错误] 缺少数据文件，安装包不完整！
    pause
    exit /b 1
)
if not exist "%REDIS_HOME%\redis-server.exe" (
    echo [错误] 缺少数据文件，安装包不完整！
    pause
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\javaw.exe" (
    echo [错误] 缺少数据文件，安装包不完整！
    pause
    exit /b 1
)
if not exist "%~dp0_hidden.ps1" (
    echo [错误] 缺少 _hidden.ps1，安装包不完整！
    pause
    exit /b 1
)

echo ================================================
echo   校园二手交易平台 v1.0.0 正在启动...
echo   内嵌 MariaDB ^| Redis ^| JDK ^| 5 微服务
echo   服务全部静默后台运行，不会弹出命令提示符窗口
echo   运行日志: %LOG_DIR%
echo ================================================
echo.

REM === 1. 初始化 MariaDB 数据目录（仅首次） ===
if not exist "%DATA_DIR%\mysql" (
    echo [1/8] 首次运行 - 初始化 MariaDB 数据目录...
    mkdir "%DATA_DIR%\mysql" 2>nul
    (
        echo [mysqld]
        echo port=3306
        echo basedir=%MARIADB_HOME%
        echo datadir=%DATA_DIR%\mysql
        echo character-set-server=utf8mb4
        echo collation-server=utf8mb4_unicode_ci
        echo default-storage-engine=InnoDB
        echo max_connections=100
        echo innodb_buffer_pool_size=128M
        echo skip-log-bin
        echo sql_mode=NO_ENGINE_SUBSTITUTION
        echo default_authentication_plugin=mysql_native_password
        echo.
        echo [client]
        echo default-character-set=utf8mb4
        echo port=3306
        echo user=root
        echo password=campus123
    ) > "%DATA_DIR%\mysql\my.ini"
    "%MARIADB_HOME%\bin\mysqld.exe" --defaults-file="%DATA_DIR%\mysql\my.ini" --initialize-insecure --console
    echo   MariaDB 数据目录初始化完成
)

REM === 2. 启动 MariaDB ===
netstat -ano | findstr ":3306 " >nul 2>&1
if !errorlevel! equ 0 (
    echo [2/8] 端口 3306 已被占用 - 跳过启动 MariaDB
) else (
    echo [2/8] 启动 MariaDB（端口 3306）...
    %LAUNCH% -Exe "%MARIADB_HOME%\bin\mysqld.exe" -ArgLine "--defaults-file=data\mysql\my.ini" -Cwd "%HERE%" -Log "%LOG_DIR%\mariadb.log"
    echo   等待 MariaDB 就绪...
    set MYSQL_READY=0
    for /L %%i in (1,1,30) do (
        if !MYSQL_READY! equ 0 (
            "%MARIADB_HOME%\bin\mysqladmin.exe" -u root --skip-password ping >nul 2>&1
            if !errorlevel! equ 0 (
                set MYSQL_READY=1
            ) else (
                timeout /t 1 /nobreak >nul
            )
        )
    )
    if !MYSQL_READY! equ 0 (
        echo [错误] MariaDB 启动超时！详见 %LOG_DIR%\mariadb.log
        pause
        exit /b 1
    )
    echo   MariaDB 已就绪
)

REM === 3. 首次运行 - 设置密码并初始化数据库 ===
if not exist "%DATA_DIR%\mysql\.initialized" (
    echo [3/8] 首次运行 - 设置密码并初始化数据库...
    "%MARIADB_HOME%\bin\mysql.exe" -u root --skip-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'campus123'; FLUSH PRIVILEGES;" 2>nul
    "%MARIADB_HOME%\bin\mysql.exe" -u root -pcampus123 < "%~dp0sql\init.sql" 2>nul
    echo   数据库表结构初始化完成
    "%MARIADB_HOME%\bin\mysql.exe" -u root -pcampus123 < "%~dp0sql\init_admin.sql" 2>nul
    echo   管理员账号初始化完成
    echo initialized > "%DATA_DIR%\mysql\.initialized"
)

REM === 4. 启动 Redis ===
mkdir "%DATA_DIR%\redis" 2>nul
netstat -ano | findstr ":6379 " >nul 2>&1
if !errorlevel! equ 0 (
    echo [4/8] 端口 6379 已被占用 - 跳过启动 Redis
) else (
    echo [4/8] 启动 Redis（端口 6379）...
    %LAUNCH% -Exe "%REDIS_HOME%\redis-server.exe" -ArgLine "--port 6379 --dir data\redis --maxmemory 128mb" -Cwd "%HERE%" -Log "%LOG_DIR%\redis.log"
    timeout /t 2 /nobreak >nul
    echo   Redis 已启动
)

REM === 5-8. 启动业务微服务（后台静默） ===
echo [5/8] 启动用户服务 :8084...
%LAUNCH% -Exe "%JAVA_HOME%\bin\javaw.exe" -ArgLine "-jar app\user-service.jar --spring.profiles.active=standalone" -Cwd "%HERE%" -Log "%LOG_DIR%\user-service.log"

echo [6/8] 启动商品服务 :8081...
%LAUNCH% -Exe "%JAVA_HOME%\bin\javaw.exe" -ArgLine "-jar app\product-service.jar --spring.profiles.active=standalone" -Cwd "%HERE%" -Log "%LOG_DIR%\product-service.log"

echo [7/8] 启动订单服务 :8083...
%LAUNCH% -Exe "%JAVA_HOME%\bin\javaw.exe" -ArgLine "-jar app\order-service.jar --spring.profiles.active=standalone" -Cwd "%HERE%" -Log "%LOG_DIR%\order-service.log"

echo [8/8] 启动支付服务 :8085...
%LAUNCH% -Exe "%JAVA_HOME%\bin\javaw.exe" -ArgLine "-jar app\payment-service.jar --spring.profiles.active=standalone" -Cwd "%HERE%" -Log "%LOG_DIR%\payment-service.log"

echo   等待业务服务启动（约 20 秒）...
timeout /t 20 /nobreak >nul

REM === 网关（最后启动，依赖后端服务） ===
echo [网关] 启动网关 :8080...
%LAUNCH% -Exe "%JAVA_HOME%\bin\javaw.exe" -ArgLine "-jar app\campus-gateway.jar --spring.profiles.active=standalone" -Cwd "%HERE%" -Log "%LOG_DIR%\campus-gateway.log"
timeout /t 5 /nobreak >nul

REM === 前端静态服务器 ===
echo [前端] 启动网页服务器 :80...
%LAUNCH% -Exe "%JAVA_HOME%\bin\javaw.exe" -ArgLine "-cp app\gateway-classes com.campus.gateway.FrontendServer 80 frontend http://localhost:8080" -Cwd "%HERE%" -Log "%LOG_DIR%\frontend.log"
timeout /t 10 /nobreak >nul

REM === 状态汇总 ===
echo.
echo ================================================
echo   启动完成！端口状态：
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
echo   管理后台: http://localhost
echo   管理员:   admin / admin123
echo   日志目录: %LOG_DIR%
echo ================================================
echo.

netstat -ano | findstr ":80 " >nul 2>&1
if !errorlevel! equ 0 start http://localhost

echo 按任意键关闭此窗口（不会关闭后台服务）...
pause >nul
exit /b 0

:chk
netstat -ano | findstr ":%~1 " >nul 2>&1
if !errorlevel! equ 0 (
    echo    [运行中]  %~1  %~2
) else (
    echo    [未启动]  %~1  %~2   详见 %LOG_DIR%
)
goto :eof
