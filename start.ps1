#Requires -Version 5.1
<#
.SYNOPSIS
    校园二手交易平台 - 统一启动脚本
.DESCRIPTION
    支持 standalone / dev / prod 三种模式
    自动初始化 MariaDB + Redis，启动所有微服务
    带健康检查、进程守护、优雅退出、彩色日志
.PARAMETER Mode
    运行模式: standalone (默认) | dev | prod
.PARAMETER SkipBuild
    跳过 Maven 编译，直接使用已有 JAR
.PARAMETER ForceRebuild
    强制重新编译所有模块
.EXAMPLE
    .\start.ps1                    # standalone 模式启动
    .\start.ps1 -Mode dev          # 开发模式启动
    .\start.ps1 -Mode prod         # 生产模式启动
    .\start.ps1 -ForceRebuild      # 强制重编译后启动
#>

param(
    [ValidateSet('standalone', 'dev', 'prod')]
    [string]$Mode = 'standalone',

    [switch]$SkipBuild,

    [switch]$ForceRebuild
)

$ErrorActionPreference = 'Stop'
$script:ServicePids = @{}
$script:StartTime = Get-Date

# ========================================
# 配置
# ========================================
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$DistDir = Join-Path $ProjectRoot 'dist-standalone'
$LogDir = Join-Path $ProjectRoot 'logs'
$DataDir = Join-Path $DistDir 'data'
$JdkHome = Join-Path $DistDir 'jdk'
$MariadbHome = Join-Path $DistDir 'mariadb'
$RedisHome = Join-Path $DistDir 'redis'

# 服务定义
$Services = @(
    @{ Name='user-service';    Port=8084; Jar='app\user-service.jar';    DependsOn=@() },
    @{ Name='product-service'; Port=8081; Jar='app\product-service.jar'; DependsOn=@() },
    @{ Name='order-service';   Port=8083; Jar='app\order-service.jar';   DependsOn=@('user-service','product-service') },
    @{ Name='payment-service'; Port=8085; Jar='app\payment-service.jar'; DependsOn=@() },
    @{ Name='campus-gateway';  Port=8080; Jar='app\campus-gateway.jar';  DependsOn=@('user-service','product-service','order-service','payment-service'); IsGateway=$true }
)

# 中间件
$Middlewares = @(
    @{ Name='MariaDB'; Port=3306; Exe="$MariadbHome\bin\mysqld.exe";    Args="--defaults-file=$DataDir\mysql\my.ini"; ReadyCheck={ Test-Port 3306 }; TimeoutSec=45 },
    @{ Name='Redis';   Port=6379; Exe="$RedisHome\redis-server.exe";    Args="--port 6379 --maxmemory 128mb"; ReadyCheck={ Test-Port 6379 }; TimeoutSec=10 }
)

# ========================================
# 工具函数
# ========================================
function Write-ColorLog {
    param([string]$Message, [string]$Level='INFO')
    $time = Get-Date -Format 'HH:mm:ss'
    switch ($Level) {
        'SUCCESS' { Write-Host "[$time] [✓] $Message" -ForegroundColor Green }
        'ERROR'   { Write-Host "[$time] [✗] $Message" -ForegroundColor Red }
        'WARN'    { Write-Host "[$time] [!] $Message" -ForegroundColor Yellow }
        'STEP'    { Write-Host "[$time] [►] $Message" -ForegroundColor Cyan }
        'TITLE'   { Write-Host "`n========================================" -ForegroundColor Magenta; Write-Host "  $Message" -ForegroundColor Magenta; Write-Host "========================================" -ForegroundColor Magenta }
        default   { Write-Host "[$time] [·] $Message" -ForegroundColor Gray }
    }
}

function Test-Port {
    param([int]$Port, [int]$TimeoutMs=2000)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $result = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        $success = $result.AsyncWaitHandle.WaitOne($TimeoutMs)
        $client.Close()
        return $success
    } catch { return $false }
}

function Wait-ForCondition {
    param([ScriptBlock]$Condition, [string]$Description, [int]$TimeoutSec=60, [int]$IntervalSec=1)
    $elapsed = 0
    while ($elapsed -lt $TimeoutSec) {
        if (& $Condition) { return $true }
        Write-Host "  等待 $Description ... ($elapsed/$TimeoutSec s)" -ForegroundColor DarkGray -NoNewline
        Start-Sleep -Seconds $IntervalSec
        $elapsed += $IntervalSec
        Write-Host "`r" -NoNewline
    }
    return $false
}

function Start-ProcessDetached {
    param([string]$FilePath, [string]$Arguments, [string]$LogFile, [string]$WindowTitle)
    $procInfo = New-Object System.Diagnostics.ProcessStartInfo
    $procInfo.FileName = $FilePath
    $procInfo.Arguments = $Arguments
    $procInfo.UseShellExecute = $false
    $procInfo.RedirectStandardOutput = $true
    $procInfo.RedirectStandardError = $true
    $procInfo.CreateNoWindow = $true
    $procInfo.WorkingDirectory = $DistDir

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $procInfo

    $exitEvent = Register-ObjectEvent -InputObject $proc -EventName 'Exited' -Action {
        $name = $Event.SourceArgs[0].Name
        Write-ColorLog "进程退出: $name (ExitCode: $($Event.SourceEventArgs.ExitCode))" -Level 'WARN'
    } -MessageData @{ Name=$WindowTitle } | Out-Null

    $proc.EnableRaisingEvents = $true
    $proc.Start() | Out-Null

    # 异步读取输出到日志
    if ($LogFile) {
        $proc.BeginOutputReadLine()
        $proc.BeginErrorReadLine()
        $writer = [System.IO.StreamWriter]::new($LogFile, $true)
        Register-ObjectEvent -InputObject $proc -EventName 'OutputDataReceived' -Action {
            $writer.WriteLine($Event.SourceEventArgs.Data)
        } | Out-Null
        Register-ObjectEvent -InputObject $proc -EventName 'ErrorDataReceived' -Action {
            $writer.WriteLine("[ERR] " + $Event.SourceEventArgs.Data)
        } | Out-Null
    }

    return @{ Process=$proc; ExitEvent=$exitEvent }
}

function Stop-ServiceGracefully {
    param([string]$Name, [int]$ProcId)
    if (-not $ProcId) { return }
    try {
        $proc = Get-Process -Id $ProcId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-ColorLog "停止 $Name (PID:$ProcId)..." -Level 'WARN'
            $proc.CloseMainWindow() | Out-Null
            Start-Sleep -Seconds 3
            if (-not $proc.HasExited) {
                $proc.Kill()
                Start-Sleep -Seconds 1
            }
            Write-ColorLog "$Name 已停止" -Level 'INFO'
        }
    } catch { }
}

# ========================================
# 编译阶段
# ========================================
function Invoke-BuildPhase {
    Write-ColorLog '编译阶段' -Level 'TITLE'

    if ($SkipBuild -and -not $ForceRebuild) {
        $allExist = $true
        foreach ($svc in $Services) {
            $jarPath = Join-Path $DistDir $svc.Jar
            if (-not (Test-Path $jarPath)) { $allExist = $false; break }
        }
        if ($allExist) {
            Write-ColorLog '跳过编译 (JAR 已存在)' -Level 'WARN'
            return
        }
    }

    Write-ColorLog 'Maven 编译打包...' -Level 'STEP'
    Push-Location $ProjectRoot
    try {
        if ($ForceRebuild) {
            mvn clean -q 2>&1 | Out-Null
        }
        mvn package -DskipTests -q 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Maven 编译失败' }

        # 复制 JAR 到 dist
        if (-not (Test-Path "$DistDir\app")) { New-Item -ItemType Directory "$DistDir\app" -Force | Out-Null }
        @(
            @{Src='campus-gateway\target\campus-gateway-1.0.0.jar';  Dst='app\campus-gateway.jar'},
            @{Src='user-service\target\user-service-1.0.0.jar';       Dst='app\user-service.jar'},
            @{Src='product-service\target\product-service-1.0.0.jar'; Dst='app\product-service.jar'},
            @{Src='order-service\target\order-service-1.0.0.jar';     Dst='app\order-service.jar'},
            @{Src='payment-service\target\payment-service-1.0.0.jar'; Dst='app\payment-service.jar'}
        ) | ForEach-Object {
            Copy-Item (Join-Path $ProjectRoot $_.Src) (Join-Path $DistDir $_.Dst) -Force
            $size = [math]::Round((Get-Item (Join-Path $DistDir $_.Dst)).Length / 1MB, 1)
            Write-ColorLog "$($_.Dst)  (${size} MB)" -Level 'SUCCESS'
        }
        Write-ColorLog '编译完成' -Level 'SUCCESS'
    } finally {
        Pop-Location
    }
}

# ========================================
# 环境检查
# ========================================
function Invoke-EnvCheck {
    Write-ColorLog '环境检查' -Level 'TITLE'

    # 检查关键文件
    $checks = @(
        @{Path="$JdkHome\bin\java.exe"; Name='JDK 17'},
        @{Path="$MariadbHome\bin\mysqld.exe"; Name='MariaDB'},
        @{Path="$RedisHome\redis-server.exe"; Name='Redis'}
    )
    foreach ($check in $checks) {
        if (Test-Path $check.Path) {
            Write-ColorLog "$($check.Name) - OK" -Level 'SUCCESS'
        } else {
            Write-ColorLog "$($check.Name) - 缺失: $($check.Path)" -Level 'ERROR'
            throw '环境不完整，请先运行 package-standalone.bat'
        }
    }

    # 检查端口
    foreach ($mw in $Middlewares) {
        if (Test-Port $mw.Port) {
            Write-ColorLog "端口 $($mw.Port) 已被占用 ($($mw.Name) 可能已在运行)" -Level 'WARN'
        } else {
            Write-ColorLog "端口 $($mw.Port) - 空闲" -Level 'SUCCESS'
        }
    }
    foreach ($svc in $Services) {
        if (Test-Port $svc.Port) {
            Write-ColorLog "端口 $($svc.Port) 已被占用 ($($svc.Name) 可能已在运行)" -Level 'WARN'
        }
    }

    Write-ColorLog "运行模式: $Mode" -Level 'INFO'
    Write-ColorLog "数据目录: $DataDir" -Level 'INFO'
}

# ========================================
# MariaDB 初始化
# ========================================
function Initialize-MariaDB {
    $mysqlDir = "$DataDir\mysql"
    if (Test-Path $mysqlDir) { return }

    # 检测路径是否含中文 (MariaDB 11.4 不支持中文路径)
    $hasChinese = $ProjectRoot -match '[\u4e00-\u9fff]'
    if ($hasChinese) {
        $mysqlDir = "C:\CampusMart\data\mysql"
        $actualDataDir = "C:\CampusMart\data"
        Write-ColorLog "中文路径检测: 数据目录改用 $mysqlDir" -Level 'WARN'
    } else {
        $actualDataDir = $DataDir
    }

    Write-ColorLog '首次运行 - 初始化 MariaDB...' -Level 'STEP'
    New-Item -ItemType Directory -Path $mysqlDir -Force | Out-Null

    # MariaDB 11.4 用 mariadb-install-db 替代 mysqld --initialize-insecure
    Write-ColorLog '执行 mariadb-install-db ...' -Level 'INFO'
    $installDbExe = "$MariadbHome\bin\mariadb-install-db.exe"
    if (-not (Test-Path $installDbExe)) {
        $installDbExe = "$MariadbHome\bin\mysql_install_db.exe"
    }
    $initArgs = @(
        "--datadir=$mysqlDir",
        '--auth-root-authentication-method=normal',
        '--skip-test-db'
    )
    $initProc = Start-Process -FilePath $installDbExe -ArgumentList $initArgs -NoNewWindow -Wait -PassThru
    if ($initProc.ExitCode -ne 0) {
        Write-ColorLog "MariaDB 初始化失败 (ExitCode: $($initProc.ExitCode))" -Level 'ERROR'
        throw 'MariaDB 初始化失败'
    }

    # 生成启动用 my.ini
    $myIniContent = @"
[mysqld]
port=3306
basedir=$MariadbHome
datadir=$mysqlDir
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-storage-engine=InnoDB
max_connections=100
innodb_buffer_pool_size=128M
skip-log-bin
sql_mode=NO_ENGINE_SUBSTITUTION
default_authentication_plugin=mysql_native_password

[client]
default-character-set=utf8mb4
port=3306
"@
    [System.IO.File]::WriteAllText("$mysqlDir\my.ini", $myIniContent, [System.Text.Encoding]::Default)

    # 更新全局变量供后续使用
    if ($hasChinese) {
        Set-Variable -Name DataDir -Value $actualDataDir -Scope Script
    }
    Write-ColorLog 'MariaDB 初始化完成' -Level 'SUCCESS'
    return @{ MysqlDir=$mysqlDir; HasChinese=$hasChinese }
}

function Initialize-Database {
    $markerFile = "$DataDir\mysql\.initialized"
    if (Test-Path $markerFile) { return }

    Write-ColorLog '首次运行 - 设置密码并导入数据库...' -Level 'STEP'
    $mysql = "$MariadbHome\bin\mysql.exe"

    # 检测是否已设密码
    $testProc = Start-Process -FilePath $mysql -ArgumentList '-u','root','--skip-password','-e','SELECT 1' -NoNewWindow -Wait -PassThru
    if ($testProc.ExitCode -eq 0) {
        # 无密码，设置密码
        Start-Process -FilePath $mysql -ArgumentList '-u','root','--skip-password','-e',"ALTER USER 'root'@'localhost' IDENTIFIED BY 'campus123'; FLUSH PRIVILEGES;" -NoNewWindow -Wait | Out-Null
        Write-ColorLog '密码设置完成' -Level 'SUCCESS'
    } else {
        Write-ColorLog '密码已存在，跳过设置' -Level 'INFO'
    }

    # 导入表结构
    $sqlDir = Join-Path $DistDir 'sql'
    foreach ($sql in @('init.sql', 'init_admin.sql')) {
        $sqlPath = Join-Path $sqlDir $sql
        if (Test-Path $sqlPath) {
            Start-Process -FilePath $mysql -ArgumentList '-u','root','-pcampus123' -RedirectStandardInput $sqlPath -NoNewWindow -Wait
            Write-ColorLog "  $sql 导入完成" -Level 'SUCCESS'
        }
    }

    'initialized' | Out-File -FilePath $markerFile -Encoding Default
    Write-ColorLog '数据库初始化完成' -Level 'SUCCESS'
}

# ========================================
# 启动中间件
# ========================================
function Start-Middleware {
    param($Mw)
    Write-ColorLog "启动 $($Mw.Name) (端口 $($Mw.Port))..." -Level 'STEP'

    $proc = Start-Process -FilePath $Mw.Exe -ArgumentList $Mw.Args -NoNewWindow -PassThru
    $script:ServicePids[$Mw.Name] = $proc.Id

    Write-ColorLog "$($Mw.Name) PID: $($proc.Id), 等待就绪..." -Level 'INFO'
    $ready = Wait-ForCondition -Condition $Mw.ReadyCheck -Description "$($Mw.Name) 启动" -TimeoutSec $Mw.TimeoutSec
    if (-not $ready) {
        Write-ColorLog "$($Mw.Name) 启动超时" -Level 'ERROR'
        throw "$($Mw.Name) 启动失败"
    }
    Write-ColorLog "$($Mw.Name) 已就绪" -Level 'SUCCESS'
}

# ========================================
# 启动微服务
# ========================================
function Start-MicroService {
    param($Svc)

    $javaExe = "$JdkHome\bin\java.exe"
    $jarPath = Join-Path $DistDir $Svc.Jar
    $logPath = Join-Path $LogDir "$($Svc.Name).log"
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

    Write-ColorLog "启动 $($Svc.Name) (:$($Svc.Port))..." -Level 'STEP'

    # JVM 内存参数 — 所有模式都限制堆大小，防止 OOM Killer 随机杀进程
    $heapDumpDir = Join-Path $LogDir 'heapdumps'
    if (-not (Test-Path $heapDumpDir)) { New-Item -ItemType Directory $heapDumpDir -Force | Out-Null }
    $heapDumpPath = Join-Path $heapDumpDir "$($Svc.Name)-oom.hprof"

    $jvmMemArgs = @()
    if ($Mode -eq 'prod') {
        # 生产模式: 适当调大
        $jvmMemArgs = @('-Xms256m', '-Xmx512m')
    } else {
        # standalone/dev 模式: 严格限制，5 个服务总计 ~1.25GB，安全运行
        $jvmMemArgs = @('-Xms128m', '-Xmx256m')
    }
    # 通用 JVM 优化参数
    $jvmCommonArgs = @(
        '-XX:+UseG1GC',                          # G1 垃圾回收器
        '-XX:MaxGCPauseMillis=200',              # GC 暂停目标 200ms
        '-XX:+HeapDumpOnOutOfMemoryError',       # OOM 时生成堆转储
        "-XX:HeapDumpPath=$heapDumpPath",        # 堆转储路径
        '-XX:+ExitOnOutOfMemoryError',           # OOM 时立即退出（而非僵死）
        '-XX:MaxMetaspaceSize=128m'              # 限制元空间
    )

    $args = @(
        '-jar', $jarPath,
        "--spring.profiles.active=$Mode",
        "-Dfile.encoding=UTF-8"
    ) + $jvmMemArgs + $jvmCommonArgs

    $errLogPath = Join-Path $LogDir "$($Svc.Name)-err.log"
    $proc = Start-Process -FilePath $javaExe -ArgumentList $args -NoNewWindow -PassThru `
        -RedirectStandardOutput $logPath -RedirectStandardError $errLogPath

    $script:ServicePids[$Svc.Name] = $proc.Id
    # 记录启动参数，方便排查
    Write-ColorLog "$($Svc.Name) PID: $($proc.Id) | 堆内存: $($jvmMemArgs -join ' ') | 日志: $logPath" -Level 'INFO'

    # 微服务启动较慢，等待更长时间以确保初始化完成
    Start-Sleep -Seconds 20
    if ($proc.HasExited) {
        Write-ColorLog "$($Svc.Name) 启动后立即退出 (ExitCode: $($proc.ExitCode))" -Level 'ERROR'
        Write-ColorLog "查看错误日志: $errLogPath" -Level 'WARN'
        Write-ColorLog "查看堆转储: $heapDumpPath (如果 OOM)" -Level 'WARN'
        throw "$($Svc.Name) 启动失败"
    }
}

# ========================================
# 启动前端 (仅 standalone 模式)
# ========================================
function Start-Frontend {
    if ($Mode -ne 'standalone') { return }
    Write-ColorLog '启动前端网页服务器 (:80)...' -Level 'STEP'

    $javaExe = "$JdkHome\bin\java.exe"
    $gatewayJar = Join-Path $DistDir 'app\campus-gateway.jar'
    $frontendDir = Join-Path $DistDir 'frontend'
    $logPath = Join-Path $LogDir 'frontend.log'

    if (-not (Test-Path $frontendDir)) {
        Write-ColorLog '前端目录不存在，跳过' -Level 'WARN'
        return
    }

    # 使用编译后的 classes 目录作为 classpath（Spring Boot fat JAR 的 -cp 无法加载嵌套类）
    $classesDir = Join-Path $ProjectRoot 'campus-gateway\target\classes'
    if (-not (Test-Path $classesDir)) {
        Write-ColorLog "campus-gateway classes 目录不存在，尝试从 JAR 启动" -Level 'WARN'
        $classesDir = $gatewayJar
    } else {
        Write-ColorLog "使用 classes 目录作为 classpath" -Level 'INFO'
    }
    $errLogPath = Join-Path $LogDir 'frontend-err.log'
    $proc = Start-Process -FilePath $javaExe -NoNewWindow -PassThru `
        -ArgumentList @('-cp', $classesDir, 'com.campus.gateway.FrontendServer', '80', $frontendDir, 'http://localhost:8080') `
        -RedirectStandardOutput $logPath -RedirectStandardError $errLogPath

    $script:ServicePids['Frontend'] = $proc.Id
    Write-ColorLog "Frontend PID: $($proc.Id) | http://localhost | API 代理 → :8080" -Level 'SUCCESS'
}

# ========================================
# 优雅退出
# ========================================
function Invoke-GracefulShutdown {
    Write-ColorLog '优雅退出' -Level 'TITLE'

    # 停止顺序: 前端 -> 网关 -> 微服务(倒序) -> Redis -> MariaDB
    $stopOrder = @('Frontend', 'campus-gateway', 'payment-service', 'order-service', 'product-service', 'user-service', 'Redis', 'MariaDB')

    foreach ($name in $stopOrder) {
        if ($script:ServicePids.ContainsKey($name)) {
            Stop-ServiceGracefully -Name $name -ProcId $script:ServicePids[$name]
        }
    }

    # 确保所有 javaw 已退出
    Get-Process -Name 'javaw' -ErrorAction SilentlyContinue | Where-Object {
        $_.MainWindowTitle -match '(User|Product|Order|Payment|Gateway|Frontend)'
    } | Stop-Process -Force -ErrorAction SilentlyContinue

    Write-ColorLog '所有服务已停止' -Level 'SUCCESS'
    Write-ColorLog "运行时长: $([math]::Round(((Get-Date) - $script:StartTime).TotalMinutes, 1)) 分钟" -Level 'INFO'
}

# ========================================
# 注册退出事件
# ========================================
$null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action {
    Write-Host "`n收到退出信号，优雅关闭..." -ForegroundColor Yellow
}

# 捕获 Ctrl+C (仅在交互模式下有效)
try { [Console]::TreatControlCAsInput = $false } catch { }
try { $null = [Console]::CancelKeyPress } catch { }

# ========================================
# 主流程
# ========================================
function Main {
    Write-ColorLog '校园二手交易平台 v1.0.0' -Level 'TITLE'

    try {
        # 0. 编译
        if (-not $SkipBuild) { Invoke-BuildPhase }

        # 1. 环境检查
        Invoke-EnvCheck

        # 2. 初始化 MariaDB (首次)
        $initResult = Initialize-MariaDB

        # 更新 MariaDB 启动参数（中文路径可能已变更）
        if ($initResult -and $initResult.MysqlDir) {
            $script:Middlewares[0].Args = "--defaults-file=$($initResult.MysqlDir)\my.ini"
        }

        # 3. 启动中间件
        if (-not (Test-Port 3306)) { Start-Middleware $Middlewares[0] }
        else { Write-ColorLog 'MariaDB 已在运行' -Level 'WARN' }

        # 4. 初始化数据库 (首次)
        Initialize-Database

        # 5. 启动 Redis
        $redisDataDir = "$DataDir\redis"
        if (-not (Test-Path $redisDataDir)) { New-Item -ItemType Directory $redisDataDir -Force | Out-Null }
        $script:Middlewares[1].Args = "--port 6379 --dir `"$redisDataDir`" --maxmemory 128mb"
        if (-not (Test-Port 6379)) { Start-Middleware $Middlewares[1] }
        else { Write-ColorLog 'Redis 已在运行' -Level 'WARN' }

        # 6. 启动微服务 (先启动无依赖的)
        $noDep = $Services | Where-Object { $_.DependsOn.Count -eq 0 -and -not $_.IsGateway }
        $hasDep = $Services | Where-Object { $_.DependsOn.Count -gt 0 -and -not $_.IsGateway }
        $gateway = $Services | Where-Object { $_.IsGateway }

        Write-ColorLog "启动微服务 (模式: $Mode)" -Level 'STEP'
        foreach ($svc in $noDep) {
            if (-not (Test-Port $svc.Port)) { Start-MicroService $svc }
            else { Write-ColorLog "$($svc.Name) 已在运行" -Level 'WARN' }
        }

        # 等待基础服务就绪
        Write-ColorLog '等待服务就绪 (15s)...' -Level 'INFO'
        Start-Sleep -Seconds 15

        foreach ($svc in $hasDep) {
            if (-not (Test-Port $svc.Port)) { Start-MicroService $svc }
            else { Write-ColorLog "$($svc.Name) 已在运行" -Level 'WARN' }
        }

        # 等待所有业务服务就绪
        Write-ColorLog '等待业务服务就绪 (30s)...' -Level 'INFO'
        Start-Sleep -Seconds 30

        # 7. 启动网关
        if ($gateway) {
            if (-not (Test-Port $gateway.Port)) { Start-MicroService $gateway }
            else { Write-ColorLog "Gateway 已在运行" -Level 'WARN' }
        }

        # 8. 启动前端
        Start-Sleep -Seconds 5
        Start-Frontend

        # 9. 状态汇总
        Start-Sleep -Seconds 3
        Write-ColorLog '启动状态汇总' -Level 'TITLE'
        $allPorts = @(3306, 6379, 8080, 8081, 8083, 8084, 8085, 80)
        $portNames = @{3306='MariaDB'; 6379='Redis'; 8080='Gateway'; 8081='Product'; 8083='Order'; 8084='User'; 8085='Payment'; 80='Frontend'}
        foreach ($port in $allPorts) {
            $status = if (Test-Port $port) { '✓ 运行中' } else { '  - 未启动' }
            $color = if (Test-Port $port) { 'Green' } else { 'DarkGray' }
            Write-Host "  端口 $port ($($portNames[$port])) : $status" -ForegroundColor $color
        }

        Write-ColorLog '' -Level 'TITLE'
        Write-Host '  ==========================================' -ForegroundColor Cyan
        if (Test-Port 80) {
            Write-Host '   管理后台: http://localhost' -ForegroundColor Green
        }
        if (Test-Port 8080) {
            Write-Host '   API 网关: http://localhost:8080' -ForegroundColor Green
        }
        Write-Host '   管理员:   admin / admin123' -ForegroundColor Yellow
        Write-Host "   日志目录: $LogDir" -ForegroundColor Gray
        Write-Host '  ==========================================' -ForegroundColor Cyan
        Write-Host ''
        Write-Host '  按 Ctrl+C 停止所有服务' -ForegroundColor Yellow
        Write-Host ''

        # ==========================================
        # 进程守护：监控并自动重启崩溃的服务
        # ==========================================
        $restartCount = @{}           # 每个服务的重启次数
        $maxRestarts = 5              # 每个服务最多连续重启 5 次
        $restartWindow = 60           # 60 秒内连续重启 5 次则放弃
        $lastRestartTime = @{}        # 最近一次重启时间

        while ($true) {
            Start-Sleep -Seconds 5
            $deadServices = @()
            foreach ($key in $script:ServicePids.Keys) {
                $svcPid = $script:ServicePids[$key]
                try {
                    $proc = Get-Process -Id $svcPid -ErrorAction Stop
                    if ($proc.HasExited) {
                        $exitCode = $proc.ExitCode
                        $deadServices += @{ Name=$key; ExitCode=$exitCode }
                    }
                } catch {
                    $deadServices += @{ Name=$key; ExitCode=-1 }
                }
            }

            foreach ($dead in $deadServices) {
                $svcName = $dead.Name
                $exitCode = $dead.ExitCode

                # 检查是否是 OOM 退出 (ExitCode 3 是 ExitOnOutOfMemoryError 的典型值)
                if ($exitCode -eq 3) {
                    Write-ColorLog "$svcName 因 OutOfMemoryError 退出！考虑增加 -Xmx 或排查内存泄漏" -Level 'ERROR'
                }

                # 重启节流：防止无限重启循环
                $now = Get-Date
                if ($lastRestartTime.ContainsKey($svcName)) {
                    $elapsed = ($now - $lastRestartTime[$svcName]).TotalSeconds
                    if ($elapsed -gt $restartWindow) {
                        $restartCount[$svcName] = 0  # 窗口过期，重置计数
                    }
                }
                if (-not $restartCount.ContainsKey($svcName)) {
                    $restartCount[$svcName] = 0
                }

                if ($restartCount[$svcName] -ge $maxRestarts) {
                    Write-ColorLog "$svcName 在 ${restartWindow}秒内连续崩溃 $maxRestarts 次，放弃自动重启。请检查日志" -Level 'ERROR'
                    $script:ServicePids.Remove($svcName)
                    continue
                }

                # 执行重启
                $restartCount[$svcName]++
                $lastRestartTime[$svcName] = $now
                Write-ColorLog "$svcName 已崩溃 (ExitCode: $exitCode)，第 $($restartCount[$svcName]) 次自动重启..." -Level 'WARN'

                # 找到对应的服务定义
                $svcDef = $Services | Where-Object { $_.Name -eq $svcName } | Select-Object -First 1
                if ($svcDef) {
                    try {
                        # 等待几秒确保端口释放
                        Start-Sleep -Seconds 3
                        Start-MicroService $svcDef
                        Write-ColorLog "$svcName 重启成功" -Level 'SUCCESS'
                    } catch {
                        Write-ColorLog "$svcName 重启失败: $_" -Level 'ERROR'
                    }
                } elseif ($svcName -eq 'Frontend') {
                    try {
                        Start-Sleep -Seconds 3
                        Start-Frontend
                        Write-ColorLog "Frontend 重启成功" -Level 'SUCCESS'
                    } catch {
                        Write-ColorLog "Frontend 重启失败: $_" -Level 'ERROR'
                    }
                }
            }
        }

    } catch {
        Write-ColorLog "启动失败: $_" -Level 'ERROR'
        Write-ColorLog "详细信息: $($_.Exception.Message)" -Level 'ERROR'
        if ($_.Exception.StackTrace) {
            Write-ColorLog $_.Exception.StackTrace -Level 'ERROR'
        }
    } finally {
        Invoke-GracefulShutdown
    }
}

# 启动
Main
