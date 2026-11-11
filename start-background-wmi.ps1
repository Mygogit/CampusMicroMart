# ============================================================
# 校园二手交易平台 - 后台启动脚本 (WMI 独立进程, 可复用)
# 使用场景: 测试环境后台常驻运行, 关掉本脚本窗口服务也不受影响
# 端口: MariaDB:3306 / Redis:6379 / user:8084 / product:8082
#       order:8083 / payment:8085 / gateway:8090 / frontend:80
# 说明: 8081/8080 被 WSL 智能题库(sqb)容器占用, product 与
#       gateway 改端口运行 (product->8082, gateway->8090)
#       MariaDB / Redis 若未运行, 由本脚本自动拉起
#       (数据目录 C:\CampusMart\data\mysql, 因项目路径含中文)
# 依赖: 需先在命令行执行  powershell -ExecutionPolicy Bypass
# 用法: powershell -ExecutionPolicy Bypass -File start-background-wmi.ps1
# 停止: powershell -ExecutionPolicy Bypass -File stop-background.ps1
# ============================================================
$ErrorActionPreference = 'Continue'
$DistDir = 'E:\数据\CampusMicroMart\dist-standalone'
$LogDir  = 'E:\数据\CampusMicroMart\logs'
$Jdk     = "$DistDir\jdk\bin\java.exe"
$GwCfg   = 'E:\数据\CampusMicroMart\scripts\gw-config'

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory $LogDir -Force | Out-Null }

function Test-Port([int]$Port, [int]$TimeoutMs = 1000) {
    $c = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs)
        if (-not $ok) { $c.Close(); return $false }
        $c.EndConnect($iar); $c.Close(); return $true
    } catch { $c.Close(); return $false }
}

function Wait-Port([int]$Port, [int]$TimeoutSec, [string]$Label) {
    $elapsed = 0
    while ($elapsed -lt $TimeoutSec) {
        if (Test-Port $Port) { Write-Host "[OK]   $Label :$Port ready (${elapsed}s)"; return $true }
        Start-Sleep -Seconds 3
        $elapsed += 3
    }
    Write-Host "[FAIL] $Label :$Port NOT ready after ${TimeoutSec}s"
    return $false
}

# 启动独立进程 (不受脚本退出影响)
# ⚠️ 弹窗根因(已修复): 本机"默认终端应用"原为 Windows Terminal。该设置下,
#    任何请求控制台的进程都会被 Terminal 接管并弹出可见窗口, 且以下手段
#    全部失效: Win32_Process.Create / CreateNoWindow / conhost --headless cmd。
#    注意窗口类名是 CASCADIA_HOSTING_WINDOW_CLASS, 不是 ConsoleWindowClass,
#    排查时若只匹配后者会把"有窗口"误判成"无窗口"。
# ✅ 修复方式: 把 HKCU\Console\%%Startup 下的 DelegationConsole /
#    DelegationTerminal 设为 Windows 控制台主机
#    {B23D10C0-E52E-411E-9D5B-C09FDF709C7D}, 之后 CreateNoWindow 恢复正常。
#    若日后 Windows 更新重置了该键值, 弹窗问题会复现, 重新设置即可。
function Start-Wmi([string]$CmdLine, [string]$Label) {
    try {
        $inner = $CmdLine -replace '^cmd\.exe\s+/c\s+', ''
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $env:ComSpec
        $psi.Arguments = '/c ' + $inner
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $p = [System.Diagnostics.Process]::Start($psi)
        Write-Host "[START] $Label (pid=$($p.Id))"
    } catch { Write-Host "[ERROR] $Label start error: $_" }
}

$jvm = '-Dfile.encoding=UTF-8 -Xms128m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:MaxMetaspaceSize=128m'
# 在 cmd 内部清空 Spring 端口环境变量 (WorkBuddy 注入的 SERVER__PORT 会覆盖 server.port)
$envClr = 'set SERVER__PORT=& set SERVER_PORT=&'
$cdCmd  = "cd /d `"$DistDir`" &&"

Write-Host '==== CampusMicroMart background startup (WMI) ===='
Write-Host "[ENV] MariaDB:3306 / Redis:6379 / user:8084 / product:8082 / order:8083 / payment:8085 / gateway:8090 / frontend:80"

# ---- 中间件 (已运行则跳过, 否则自动拉起) ----
$DbDataDir = 'C:\CampusMart\data\mysql'
$mysqldExe = "$DistDir\mariadb\bin\mysqld.exe"
$redisExe  = "$DistDir\redis\redis-server.exe"

if (Test-Port 3306) {
    Write-Host '[SKIP] MariaDB already running :3306'
} elseif (Test-Path "$DbDataDir\my.ini") {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$mysqldExe`" --defaults-file=`"$DbDataDir\my.ini`" > `"$LogDir\mariadb.log`" 2>&1" 'MariaDB'
    Wait-Port 3306 45 'MariaDB' | Out-Null
} else {
    Write-Host "[ERROR] MariaDB my.ini not found: $DbDataDir\my.ini"
}

if (Test-Port 6379) {
    Write-Host '[SKIP] Redis already running :6379'
} elseif (Test-Path $redisExe) {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$redisExe`" --port 6379 --maxmemory 128mb > `"$LogDir\redis.log`" 2>&1" 'Redis'
    Wait-Port 6379 15 'Redis' | Out-Null
} else {
    Write-Host '[ERROR] redis-server.exe not found'
}

# ---- user-service :8084 ----
if (-not (Test-Port 8084)) {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$Jdk`" $jvm -jar `"$DistDir\app\user-service.jar`" --server.port=8084 --spring.profiles.active=standalone > `"$LogDir\user-service.log`" 2>&1" 'user-service'
} else { Write-Host '[SKIP] user-service already :8084' }
Wait-Port 8084 150 'user-service' | Out-Null

# ---- product-service :8082 (原 8081 被占, 改端口) ----
if (-not (Test-Port 8082)) {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$Jdk`" $jvm -jar `"$DistDir\app\product-service.jar`" --server.port=8082 --spring.profiles.active=standalone > `"$LogDir\product-service.log`" 2>&1" 'product-service'
} else { Write-Host '[SKIP] product-service already :8082' }
Wait-Port 8082 150 'product-service' | Out-Null

# ---- payment-service :8085 ----
if (-not (Test-Port 8085)) {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$Jdk`" $jvm -jar `"$DistDir\app\payment-service.jar`" --server.port=8085 --spring.profiles.active=standalone > `"$LogDir\payment-service.log`" 2>&1" 'payment-service'
} else { Write-Host '[SKIP] payment-service already :8085' }
Wait-Port 8085 150 'payment-service' | Out-Null

# ---- order-service :8083 (Feign 调 product 走 8082) ----
if (-not (Test-Port 8083)) {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$Jdk`" $jvm -jar `"$DistDir\app\order-service.jar`" --server.port=8083 --spring.profiles.active=standalone --spring.cloud.openfeign.client.config.product-service.url=http://localhost:8082 > `"$LogDir\order-service.log`" 2>&1" 'order-service'
} else { Write-Host '[SKIP] order-service already :8083' }
Wait-Port 8083 150 'order-service' | Out-Null

# ---- campus-gateway :8090 (routes 覆盖文件在 scripts\gw-config) ----
if (-not (Test-Port 8090)) {
    Start-Wmi "cmd.exe /c $envClr $cdCmd `"$Jdk`" $jvm -jar `"$DistDir\app\campus-gateway.jar`" --server.port=8090 --spring.profiles.active=standalone --spring.config.additional-location=file:$GwCfg/ > `"$LogDir\campus-gateway.log`" 2>&1" 'campus-gateway'
} else { Write-Host '[SKIP] campus-gateway already :8090' }
Wait-Port 8090 150 'campus-gateway' | Out-Null

# ---- Frontend :80 (代理 -> gateway 8090) ----
if (-not (Test-Port 80)) {
    $classesDir = 'E:\数据\CampusMicroMart\campus-gateway\target\classes'
    if (Test-Path "$classesDir\com\campus\gateway\FrontendServer.class") {
        Start-Wmi "cmd.exe /c $envClr $cdCmd `"$Jdk`" -cp `"$classesDir`" com.campus.gateway.FrontendServer 80 `"$DistDir\frontend`" http://localhost:8090 > `"$LogDir\frontend.log`" 2>&1" 'Frontend'
    } else { Write-Host '[ERROR] FrontendServer.class missing' }
} else { Write-Host '[SKIP] Frontend already :80' }
Wait-Port 80 30 'Frontend' | Out-Null

# ---- Summary ----
Start-Sleep -Seconds 2
Write-Host ''
Write-Host '==== SUMMARY ===='
$rows = @(
    @{N='MariaDB';P=3306},@{N='Redis';P=6379},@{N='user-service';P=8084},@{N='product-service';P=8082},
    @{N='order-service';P=8083},@{N='payment-service';P=8085},@{N='campus-gateway';P=8090},@{N='Frontend';P=80}
)
$allUp = $true
foreach ($r in $rows) {
    $up = Test-Port $r.P
    if (-not $up) { $allUp = $false }
    Write-Host ("  {0,-16} :{1,-6} {2}" -f $r.N, $r.P, $(if ($up) { 'RUNNING' } else { 'DOWN' }))
}
Write-Host ("ALL_UP=" + $allUp)
Write-Host ''
Write-Host '  管理后台: http://localhost   (账号 admin / admin123)'
Write-Host '  API 网关: http://localhost:8090'
Write-Host "  日志目录: $LogDir"
Write-Host '  停止服务: powershell -ExecutionPolicy Bypass -File stop-background.ps1'
Write-Host '==== DONE ===='
