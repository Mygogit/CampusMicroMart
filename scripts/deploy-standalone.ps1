# ============================================================
# 部署新构建产物到 dist-standalone\app
# 流程: 停止服务 -> 备份旧 jar -> 拷贝新 jar -> 校验关键依赖
# 用法: powershell -ExecutionPolicy Bypass -File scripts\deploy-standalone.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$Root    = 'E:\数据\CampusMicroMart'
$DistDir = "$Root\dist-standalone"
$AppDir  = "$DistDir\app"
$BakDir  = "$DistDir\app.bak-20260923"
$LogDir  = "$Root\logs"
$Jar     = "$DistDir\jdk\bin\jar.exe"

$props = @(
    @{ Src = "$Root\campus-gateway\target\campus-gateway-1.0.0.jar";   Dst = 'campus-gateway.jar' },
    @{ Src = "$Root\user-service\target\user-service-1.0.0.jar";       Dst = 'user-service.jar' },
    @{ Src = "$Root\product-service\target\product-service-1.0.0.jar"; Dst = 'product-service.jar' },
    @{ Src = "$Root\order-service\target\order-service-1.0.0.jar";     Dst = 'order-service.jar' },
    @{ Src = "$Root\payment-service\target\payment-service-1.0.0.jar"; Dst = 'payment-service.jar' }
)

Write-Host '==== 1/4 校验新产物存在 ===='
foreach ($p in $props) {
    if (-not (Test-Path $p.Src)) { throw "missing artifact: $($p.Src)" }
    Write-Host ("  OK {0,-24} {1} MB" -f $p.Dst, [math]::Round((Get-Item $p.Src).Length / 1MB, 1))
}

Write-Host '==== 2/4 停止应用服务 (保留 MariaDB/Redis) ===='
& powershell -ExecutionPolicy Bypass -File "$Root\stop-background.ps1" -KeepDb

Write-Host '==== 3/4 备份旧 jar 并拷贝新 jar ===='
if (-not (Test-Path $BakDir)) { New-Item -ItemType Directory $BakDir -Force | Out-Null }
foreach ($p in $props) {
    $target = Join-Path $AppDir $p.Dst
    if (Test-Path $target) {
        $backup = Join-Path $BakDir $p.Dst
        if (Test-Path $backup) { Remove-Item $backup -Force }
        Move-Item $target $backup -Force
        Write-Host "  BACKUP $($p.Dst)"
    }
    Copy-Item $p.Src $target -Force
    Write-Host "  DEPLOY $($p.Dst)"
}

Write-Host '==== 4/4 校验 feign-micrometer 已进入包内 ===='
foreach ($p in $props) {
    $target = Join-Path $AppDir $p.Dst
    $lst = & $Jar tf $target 2>&1
    $hit = $lst | Where-Object { $_ -match 'BOOT-INF/lib/feign-micrometer' }
    if ($hit) { Write-Host "  OK   $($p.Dst) -> $hit" }
    else      { Write-Host "  WARN $($p.Dst) -> feign-micrometer NOT FOUND" }
}

Write-Host '==== DONE ===='
