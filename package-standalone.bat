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

> "%DIST%\启动.bat" (
echo @echo off
echo chcp 65001 ^>nul
echo title 校园二手交易平台 - 启动中...
echo cd /d "%%~dp0"
echo.
echo set JAVA_HOME=%%~dp0jdk
echo set PATH=%%JAVA_HOME%%\bin;%%PATH%%
echo.
echo set MARIADB_HOME=%%~dp0mariadb
echo set REDIS_HOME=%%~dp0redis
echo set DATA_DIR=%%~dp0data
echo.
echo REM === 检查依赖文件完整性 ===
echo if not exist "%%MARIADB_HOME%%\bin\mysqld.exe" (
echo     echo [错误] 缺少文件: %%MARIADB_HOME%%\bin\mysqld.exe
echo     echo 安装包不完整，请重新获取完整安装包！
echo     pause
echo     exit /b 1
echo ^)
echo if not exist "%%REDIS_HOME%%\redis-server.exe" (
echo     echo [错误] 缺少文件: %%REDIS_HOME%%\redis-server.exe
echo     echo 安装包不完整，请重新获取完整安装包！
echo     pause
echo     exit /b 1
echo ^)
echo if not exist "%%JAVA_HOME%%\bin\javaw.exe" (
echo     echo [错误] 缺少文件: %%JAVA_HOME%%\bin\javaw.exe
echo     echo 安装包不完整，请重新获取完整安装包！
echo     pause
echo     exit /b 1
echo ^)
echo.
echo echo ================================================
echo echo   校园二手交易平台 v1.0.0 正在启动...
echo echo   内嵌 MariaDB ^| Redis ^| JDK ^| 5 微服务
echo echo ================================================
echo echo.
echo.
echo REM === 检查端口占用 ===
echo echo [检查] 端口占用检测...
echo netstat -ano ^| findstr ":3306 " ^>nul 2^>^&1
echo if %%errorlevel%% equ 0 (
echo     echo [警告] 端口 3306 已被占用，请先关闭占用 3306 端口的程序！
echo     echo        可能是本地已安装的 MySQL 服务
echo     pause
echo     exit /b 1
echo ^)
echo netstat -ano ^| findstr ":6379 " ^>nul 2^>^&1
echo if %%errorlevel%% equ 0 (
echo     echo [警告] 端口 6379 已被占用，请先关闭占用 6379 端口的程序！
echo     pause
echo     exit /b 1
echo ^)
echo.
echo REM === 1. 初始化 MariaDB (首次运行) ===
echo if not exist "%%DATA_DIR%%\mysql" (
echo     echo [1/8] 首次运行 - 初始化 MariaDB 数据目录...
echo     mkdir "%%DATA_DIR%%\mysql" 2^>nul
echo.
echo     REM 创建 MariaDB 配置文件
echo     ^(
echo         echo [mysqld]
echo         echo port=3306
echo         echo basedir=%%MARIADB_HOME%%
echo         echo datadir=%%DATA_DIR%%\mysql
echo         echo character-set-server=utf8mb4
echo         echo collation-server=utf8mb4_unicode_ci
echo         echo default-storage-engine=InnoDB
echo         echo max_connections=100
echo         echo innodb_buffer_pool_size=128M
echo         echo skip-log-bin
echo         echo sql_mode=NO_ENGINE_SUBSTITUTION
echo         echo default_authentication_plugin=mysql_native_password
echo         echo.
echo         echo [client]
echo         echo default-character-set=utf8mb4
echo         echo port=3306
echo         echo user=root
echo         echo password=campus123
echo     ^) ^> "%%DATA_DIR%%\mysql\my.ini"
echo.
echo     REM 初始化数据目录（无密码 root 用户）
echo     "%%MARIADB_HOME%%\bin\mysqld.exe" --defaults-file="%%DATA_DIR%%\mysql\my.ini" --initialize-insecure --console
echo     echo   MariaDB 数据目录初始化完成
echo ^)
echo.
echo REM === 2. 启动 MariaDB ===
echo echo [2/8] 启动 MariaDB (端口 3306)...
echo start /MIN "MariaDB" "%%MARIADB_HOME%%\bin\mysqld.exe" --defaults-file="%%DATA_DIR%%\mysql\my.ini"
echo.
echo REM 等待 MariaDB 就绪
echo echo   等待 MariaDB 启动...
echo set MYSQL_READY=0
echo for /L %%%%i in (1,1,30^) do (
echo     "%%MARIADB_HOME%%\bin\mysqladmin.exe" -u root --skip-password ping 2^>nul ^>nul
echo     if %%%%errorlevel%%%% equ 0 (
echo         set MYSQL_READY=1
echo         goto :mysql_ready
echo     ^)
echo     timeout /t 1 /nobreak ^>nul
echo ^)
echo :mysql_ready
echo if %%MYSQL_READY%% equ 0 (
echo     echo [错误] MariaDB 启动超时！
echo     pause
echo     exit /b 1
echo ^)
echo echo   MariaDB 已就绪
echo.
echo REM === 3. 首次运行 - 设置密码 + 初始化数据库 ===
echo if not exist "%%DATA_DIR%%\mysql\.initialized" (
echo     echo [3/8] 首次运行 - 设置密码并初始化数据库...
echo.
echo     REM 设置 root 密码
echo     "%%MARIADB_HOME%%\bin\mysql.exe" -u root --skip-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'campus123'; FLUSH PRIVILEGES;" 2^>nul
echo.
echo     REM 创建数据库并导入表结构
echo     "%%MARIADB_HOME%%\bin\mysql.exe" -u root -pcampus123 ^< "%%~dp0sql\init.sql" 2^>nul
echo     echo   数据库表结构初始化完成
echo.
echo     REM 导入管理员账号
echo     "%%MARIADB_HOME%%\bin\mysql.exe" -u root -pcampus123 ^< "%%~dp0sql\init_admin.sql" 2^>nul
echo     echo   管理员账号初始化完成
echo.
echo     REM 标记已初始化
echo     echo initialized ^> "%%DATA_DIR%%\mysql\.initialized"
echo ^)
echo.
echo REM === 4. 启动 Redis ===
echo echo [4/8] 启动 Redis (端口 6379)...
echo mkdir "%%DATA_DIR%%\redis" 2^>nul
echo start /MIN "Redis" "%%REDIS_HOME%%\redis-server.exe" --port 6379 --dir "%%DATA_DIR%%\redis" --maxmemory 128mb
echo timeout /t 2 /nobreak ^>nul
echo echo   Redis 已启动
echo.
echo REM === 5-9. 启动微服务 ===
echo echo [5/8] 启动用户服务 :8084...
echo start /MIN "UserService" "%%JAVA_HOME%%\bin\javaw" -jar app\user-service.jar --spring.profiles.active=standalone
echo.
echo echo [6/8] 启动商品服务 :8081...
echo start /MIN "ProductService" "%%JAVA_HOME%%\bin\javaw" -jar app\product-service.jar --spring.profiles.active=standalone
echo.
echo echo [7/8] 启动订单服务 :8083...
echo start /MIN "OrderService" "%%JAVA_HOME%%\bin\javaw" -jar app\order-service.jar --spring.profiles.active=standalone
echo.
echo echo [8/8] 启动支付服务 :8085...
echo start /MIN "PaymentService" "%%JAVA_HOME%%\bin\javaw" -jar app\payment-service.jar --spring.profiles.active=standalone
echo.
echo REM 等待业务服务启动
echo echo   等待业务服务启动 (约 20 秒)...
echo timeout /t 20 /nobreak ^>nul
echo.
echo REM 启动网关（最后启动，依赖后端服务）
echo echo [网关] 启动网关 :8080...
echo start /MIN "Gateway" "%%JAVA_HOME%%\bin\javaw" -jar app\campus-gateway.jar --spring.profiles.active=standalone
echo timeout /t 5 /nobreak ^>nul
echo.
echo REM === 启动前端 ===
echo echo [前端] 启动网页服务器 :80...
echo start /MIN "Frontend" "%%JAVA_HOME%%\bin\javaw" -cp app\campus-gateway.jar com.campus.gateway.FrontendServer 80 frontend
echo.
echo REM === 打开浏览器 ===
echo echo ================================================
echo echo   启动完成！
echo echo ================================================
echo echo   管理后台: http://localhost
echo echo   管理员:   admin / admin123
echo echo ================================================
echo echo.
echo start http://localhost
echo echo.
echo echo 按任意键关闭此窗口 (不会关闭后台服务)...
echo pause ^>nul
)

> "%DIST%\停止.bat" (
echo @echo off
echo chcp 65001 ^>nul
echo title 校园二手交易平台 - 停止中...
echo cd /d "%%~dp0"
echo.
echo echo ================================================
echo echo   正在停止校园二手交易平台...
echo echo ================================================
echo echo.
echo.
echo REM 停止前端
echo echo [1/6] 停止前端服务...
echo taskkill /F /FI "WINDOWTITLE eq Frontend" 2^>nul
echo.
echo REM 停止网关
echo echo [2/6] 停止网关服务...
echo taskkill /F /FI "WINDOWTITLE eq Gateway" 2^>nul
echo.
echo REM 停止业务服务（按依赖倒序）
echo echo [3/6] 停止微服务...
echo taskkill /F /FI "WINDOWTITLE eq PaymentService" 2^>nul
echo taskkill /F /FI "WINDOWTITLE eq OrderService" 2^>nul
echo taskkill /F /FI "WINDOWTITLE eq ProductService" 2^>nul
echo taskkill /F /FI "WINDOWTITLE eq UserService" 2^>nul
echo.
echo REM 停止 Redis
echo echo [4/6] 停止 Redis...
echo "%%~dp0redis\redis-cli.exe" -p 6379 SHUTDOWN 2^>nul
echo taskkill /F /FI "WINDOWTITLE eq Redis" 2^>nul
echo.
echo REM 停止 MariaDB
echo echo [5/6] 停止 MariaDB...
echo "%%~dp0mariadb\bin\mysqladmin.exe" -u root -pcampus123 -h 127.0.0.1 shutdown 2^>nul
echo taskkill /F /FI "WINDOWTITLE eq MariaDB" 2^>nul
echo.
echo REM 清理临时文件
echo echo [6/6] 清理...
echo taskkill /F /IM javaw.exe /FI "WINDOWTITLE eq *Service" 2^>nul
echo taskkill /F /IM javaw.exe /FI "WINDOWTITLE eq Gateway" 2^>nul
echo taskkill /F /IM javaw.exe /FI "WINDOWTITLE eq Frontend" 2^>nul
echo.
echo echo ================================================
echo echo   所有服务已停止，可以安全关闭此窗口
echo echo   数据保存在 .\data\ 目录，下次启动自动恢复
echo echo ================================================
echo echo.
echo pause
)

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
