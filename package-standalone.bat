@echo off
chcp 65001 >nul
title 校园二手交易平台 - 独立安装包构建

echo ================================================
echo   校园二手交易平台 - 独立版打包
echo   (内嵌 MariaDB + Redis + JDK，双击即用)
echo ================================================
echo.

set DIST=%~dp0dist-standalone
set JDK_ZIP=%~dp0env-setup\jdk-17.zip
set REDIS_DIR=%~dp0env-setup\redis
set MARIADB_DIR=%~dp0env-setup\mariadb

:: ============================================
:: Step 0: 准备 Redis 便携版（国内镜像下载）
:: ============================================
echo [0/6] 准备 Redis 便携版...
if not exist "%REDIS_DIR%\redis-server.exe" (
    :: === 下载 Redis MSI 安装包（通过国内可访问的镜像） ===
    if not exist "%~dp0env-setup\Redis-x64-5.0.14.1.msi" (
        echo.
        echo   ============================================
        echo   Redis 便携版未找到，正在下载...
        echo   大小约 7MB，仅首次打包需要下载
        echo   ============================================
        echo.
        :: 优先使用 ghproxy 镜像，国内网络友好
        powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri 'https://ghproxy.net/https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.msi' -OutFile '%~dp0env-setup\Redis-x64-5.0.14.1.msi' -UseBasicParsing -TimeoutSec 120 } catch { exit 1 } }"
        if %errorlevel% neq 0 (
            :: 备用: 尝试直接 GitHub（部分网络可通）
            echo   镜像下载失败，尝试直连 GitHub...
            powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri 'https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.msi' -OutFile '%~dp0env-setup\Redis-x64-5.0.14.1.msi' -UseBasicParsing -TimeoutSec 120 } catch { exit 1 } }"
            if %errorlevel% neq 0 (
                echo   [错误] Redis 下载失败！
                echo   请手动下载 Redis-x64-5.0.14.1.msi 放到 env-setup 目录
                echo   下载地址: https://github.com/tporadowski/redis/releases/tag/v5.0.14.1
                echo   （下载 .msi 文件，约 7MB）
                pause && exit /b 1
            )
        )
        echo   下载完成！
    )

    echo   提取 Redis 便携版...
    rmdir /s /q "%~dp0env-setup\redis_tmp" 2>nul
    msiexec /a "%~dp0env-setup\Redis-x64-5.0.14.1.msi" /qn TARGETDIR="%~dp0env-setup\redis_tmp"
    timeout /t 3 /nobreak >nul

    :: MSI 提取后文件在 Redis\ 子目录下
    if exist "%~dp0env-setup\redis_tmp\Redis\redis-server.exe" (
        xcopy /E /Y /Q "%~dp0env-setup\redis_tmp\Redis\*" "%REDIS_DIR%\" >nul
    ) else (
        :: 兼容其他可能的目录结构
        for /d %%d in ("%~dp0env-setup\redis_tmp\*") do (
            xcopy /E /Y /Q "%%d\*" "%REDIS_DIR%\" >nul
        )
        if not exist "%REDIS_DIR%\redis-server.exe" (
            xcopy /E /Y /Q "%~dp0env-setup\redis_tmp\*" "%REDIS_DIR%\" >nul
        )
    )
    rmdir /s /q "%~dp0env-setup\redis_tmp" 2>nul

    if not exist "%REDIS_DIR%\redis-server.exe" (
        echo   [错误] Redis 提取失败，请检查 env-setup\Redis-x64-5.0.14.1.msi
        pause && exit /b 1
    )
    echo   Redis 准备完成
)

:: ============================================
:: Step 0.5: 检查/下载 MariaDB 便携版
:: ============================================
echo [0/6] 检查 MariaDB 便携版...
if not exist "%MARIADB_DIR%\bin\mysqld.exe" (
    if not exist "%~dp0env-setup\mariadb-11.4.5-winx64.zip" (
        echo.
        echo   ============================================
        echo   MariaDB 便携版未找到，正在下载...
        echo   大小约 230MB，仅首次打包需要下载
        echo   ============================================
        echo.
        powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.mariadb.org/mariadb-11.4.5/winx64-packages/mariadb-11.4.5-winx64.zip' -OutFile '%~dp0env-setup\mariadb-11.4.5-winx64.zip' -UseBasicParsing }"
        if %errorlevel% neq 0 (
            echo.
            echo   [错误] MariaDB 下载失败！
            echo   请手动下载并放到 env-setup\mariadb-11.4.5-winx64.zip
            echo   下载地址: https://archive.mariadb.org/mariadb-11.4.5/winx64-packages/
            pause && exit /b 1
        )
        echo   下载完成！
    )
    
    echo   解压 MariaDB 便携版...
    mkdir "%MARIADB_DIR%" 2>nul
    powershell -Command "Expand-Archive -Path '%~dp0env-setup\mariadb-11.4.5-winx64.zip' -DestinationPath '%~dp0env-setup\mariadb_tmp' -Force"
    :: MariaDB ZIP 解压后里面有一层目录，需要提取出来
    for /d %%d in ("%~dp0env-setup\mariadb_tmp\*") do (
        xcopy /E /Y /Q "%%d\*" "%MARIADB_DIR%\" >nul
    )
    rmdir /s /q "%~dp0env-setup\mariadb_tmp" 2>nul
    
    if not exist "%MARIADB_DIR%\bin\mysqld.exe" (
        echo   [错误] MariaDB 解压失败，请检查 env-setup\mariadb-11.4.5-winx64.zip
        pause && exit /b 1
    )
    echo   MariaDB 准备完成
)

:: ============================================
:: Step 1: 清理 + 编译
:: ============================================
echo [1/6] 清理旧产物...
if exist "%DIST%" rmdir /s /q "%DIST%"
call mvn clean -q

echo [2/6] Maven 编译打包...
call mvn compile -q
if %errorlevel% neq 0 (echo 编译失败！&& pause && exit /b 1)

call mvn package -DskipTests -q
if %errorlevel% neq 0 (echo 打包失败！&& pause && exit /b 1)

:: ============================================
:: Step 3: 收集 JAR + 中间件 + 前端
:: ============================================
echo [3/6] 收集 JAR 产物...
mkdir "%DIST%\app" 2>nul
copy /Y campus-gateway\target\campus-gateway-1.0.0.jar "%DIST%\app\campus-gateway.jar"
copy /Y user-service\target\user-service-1.0.0.jar "%DIST%\app\user-service.jar"
copy /Y product-service\target\product-service-1.0.0.jar "%DIST%\app\product-service.jar"
copy /Y order-service\target\order-service-1.0.0.jar "%DIST%\app\order-service.jar"
copy /Y payment-service\target\payment-service-1.0.0.jar "%DIST%\app\payment-service.jar"

:: 前端网页服务器（FrontendServer）独立运行所需：Spring Boot fat jar 内的
:: BOOT-INF/classes 无法用 -cp 加载，故单独放一份编译产物
echo   复制前端服务器类文件...
xcopy /E /Y /Q "campus-gateway\target\classes\*" "%DIST%\app\gateway-classes\" >nul
if not exist "%DIST%\app\gateway-classes\com\campus\gateway\FrontendServer.class" (
    echo   [警告] 未找到 FrontendServer.class，前端网页服务器可能无法启动
)

echo [4/6] 复制内嵌中间件...
:: 内嵌 JDK
mkdir "%DIST%\jdk" 2>nul
powershell -Command "Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '%DIST%\jdk_tmp' -Force" 2>nul
for /d %%d in ("%DIST%\jdk_tmp\*") do (
    xcopy /E /Y /Q "%%d\*" "%DIST%\jdk\" >nul
)
rmdir /s /q "%DIST%\jdk_tmp" 2>nul

:: 内嵌 MariaDB
echo   复制 MariaDB...
mkdir "%DIST%\mariadb" 2>nul
xcopy /E /Y /Q "%MARIADB_DIR%\*" "%DIST%\mariadb\" >nul

:: 内嵌 Redis
echo   复制 Redis...
mkdir "%DIST%\redis" 2>nul
xcopy /E /Y /Q "%REDIS_DIR%\*.exe" "%DIST%\redis\" >nul
xcopy /E /Y /Q "%REDIS_DIR%\*.dll" "%DIST%\redis\" >nul
copy /Y "%REDIS_DIR%\redis.windows.conf" "%DIST%\redis\redis.conf" >nul 2>nul

:: 复制 SQL 初始化脚本
mkdir "%DIST%\sql" 2>nul
copy /Y "%~dp0sql\init.sql" "%DIST%\sql\init.sql" >nul
copy /Y "%~dp0sql\init_admin.sql" "%DIST%\sql\init_admin.sql" >nul

:: 复制 SQL 迁移脚本（增量更新用）
if exist "%~dp0sql\migrate" (
    xcopy /E /Y /Q "%~dp0sql\migrate\*" "%DIST%\sql\migrate\" >nul
) else (
    mkdir "%DIST%\sql\migrate" 2>nul
)

:: 创建 update 占位目录（用户解压增量更新包用）
mkdir "%DIST%\update" 2>nul
> "%DIST%\update\请将更新包解压到此目录.txt" echo 请将收到的 校园二手更新包_vX.X.X.zip 解压到此 update 目录中，然后运行上一层的 "增量更新.bat"。

echo [5/6] 复制前端...
if exist "campus-frontend\dist" (
    xcopy /E /Y /Q "campus-frontend\dist\*" "%DIST%\frontend\" >nul
) else (
    echo   警告: 前端未构建，请先 cd campus-frontend ^&^& npm run build
    mkdir "%DIST%\frontend" 2>nul
)

:: ============================================
:: Step 6: 生成启动脚本
:: ============================================
echo [6/6] 生成启动脚本...

:: ============================================
:: 生成启动 / 停止脚本
::   ——内容由仓库 dist-templates\ 统一维护，改模板即生效，勿直接改本文件
::   ——服务全部通过 _hidden.ps1 以 CreateNoWindow 方式静默启动，不弹控制台窗口
:: ============================================
:: 模板以 UTF-8 维护；生成的 .bat 必须是 GBK(936) + CRLF，
:: 否则 cmd 按系统 OEM 代码页误读中文并丢字节（netstat 变 etstat）
copy /Y "%~dp0dist-templates\_hidden.ps1" "%DIST%\_hidden.ps1" >nul
if not exist "%DIST%\_hidden.ps1" (
    echo   [ERROR] missing dist-templates\_hidden.ps1
    pause && exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dist-templates\_prepare.ps1" -Src "%~dp0dist-templates" -Dst "%DIST%"
if errorlevel 1 (
    echo   [ERROR] failed to generate start/stop bat from dist-templates
    pause && exit /b 1
)
echo   start/stop scripts ready (silent hidden start, no console windows)

:: ============================================
:: 生成 增量更新.bat
:: ============================================
> "%DIST%\增量更新.bat" (
echo @echo off
echo chcp 65001 ^>nul
echo title 校园二手交易平台 - 增量更新
echo cd /d "%%~dp0"
echo.
echo echo ================================================
echo echo   校园二手交易平台 - 增量更新
echo echo   （仅替换 JAR 和前端，保留数据）
echo echo ================================================
echo echo.
echo echo [警告] 更新前请先运行 "停止.bat" 停止所有服务！
echo echo 如有 update 目录更新包，将自动安装。
echo echo.
echo pause
echo.
echo set DIST=%%~dp0
echo.
echo echo [1/5] 停止所有服务...
echo call "%%DIST%%停止.bat"
echo.
echo :: 创建备份目录
echo echo [2/5] 备份当前版本...
echo if not exist "%%DIST%%backup" mkdir "%%DIST%%backup"
echo for /f "tokens=1-6 delims=/-.: " %%%%a in ("%%date%% %%time%%"^) do set TS=%%%%a%%%%b%%%%c_%%%%d%%%%e%%%%f
echo set BACKUP_DIR=%%DIST%%backup\%%TS%%
echo mkdir "%%BACKUP_DIR%%" 2^>nul
echo xcopy /E /Y /Q "%%DIST%%app\*" "%%BACKUP_DIR%%\app\" ^>nul 2^>^&1
echo xcopy /E /Y /Q "%%DIST%%frontend\*" "%%BACKUP_DIR%%\frontend\" ^>nul 2^>^&1
echo echo   已备份到: %%BACKUP_DIR%%
echo.
echo :: 应用更新
echo echo [3/5] 应用更新...
echo if exist "%%DIST%%update\app" (
echo     echo   更新 JAR 文件...
echo     xcopy /E /Y /Q "%%DIST%%update\app\*" "%%DIST%%app\" ^>nul
echo ^)
echo if exist "%%DIST%%update\frontend" (
echo     echo   更新前端文件...
echo     xcopy /E /Y /Q "%%DIST%%update\frontend\*" "%%DIST%%frontend\" ^>nul
echo ^)
echo.
echo :: 执行 SQL 迁移
echo echo [4/5] 检查数据库迁移...
echo if exist "%%DIST%%update\migrate" (
echo     echo   执行增量 SQL 迁移...
echo     set MIGRATE_OK=1
echo     for %%%%f in ("%%DIST%%update\migrate\*.sql"^) do (
echo         echo     执行: %%%%~nxf
echo         "%%DIST%%mariadb\bin\mysql.exe" -u root -pcampus123 -h 127.0.0.1 ^< "%%%%f" 2^>nul
echo         if ^^!errorlevel^^! neq 0 (
echo             echo     [错误] 迁移失败^^! 文件: %%%%~nxf
echo             set MIGRATE_OK=0
echo         ^)
echo     ^)
echo     if ^^!MIGRATE_OK^^! equ 0 (
echo         echo.
echo         echo   [回滚] 恢复备份...
echo         xcopy /E /Y /Q "%%BACKUP_DIR%%\app\*" "%%DIST%%app\" ^>nul
echo         xcopy /E /Y /Q "%%BACKUP_DIR%%\frontend\*" "%%DIST%%frontend\" ^>nul
echo         echo   已恢复到更新前版本
echo         pause
echo         exit /b 1
echo     ^)
echo     echo   数据库迁移完成
echo ^) else (
echo     echo   无需数据库迁移
echo ^)
echo.
echo :: 清理更新包
echo rmdir /s /q "%%DIST%%update" 2^>nul
echo mkdir "%%DIST%%update" 2^>nul
echo echo   更新包已清理
echo.
echo echo [5/5] 重新启动服务...
echo call "%%DIST%%启动.bat"
echo.
echo echo ================================================
echo echo   更新完成！
echo echo   如有问题，可从 backup\ 目录恢复旧版本
echo echo ================================================
echo pause
)

echo.
echo ================================================
echo   打包完成！
echo   产物目录: dist-standalone\
echo.
echo   目录结构:
echo     dist-standalone\
echo       ├── 启动.bat         双击启动
echo       ├── 停止.bat         双击停止
echo       ├── 增量更新.bat     一键增量更新（保留数据）
echo       ├── update\          放置更新包的目录
echo       ├── jdk\             JDK 17 运行环境
echo       ├── mariadb\         MariaDB 11.4 数据库
echo       ├── redis\           Redis 5.0 缓存
echo       ├── app\             5 个微服务 JAR
echo       ├── frontend\        前端静态文件
echo       └── sql\             SQL 初始化 + 迁移脚本
echo.
echo   用户拿到 dist-standalone 文件夹后：
echo     1. 双击 "启动.bat"
echo     2. 首次自动初始化数据库（约 30 秒）
echo     3. 浏览器自动打开 http://localhost
echo     4. 登录: admin / admin123
echo     5. 关闭时双击 "停止.bat"
echo.
echo   === 版本更新方式 ===
echo     1. 收到 更新包.zip → 解压到 update\ 目录
echo     2. 双击 "停止.bat" 停止所有服务
echo     3. 双击 "增量更新.bat" 自动备份+更新+重启
echo     4. 更新失败会自动回滚到备份版本
echo ================================================
pause
