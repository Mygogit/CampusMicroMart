# ============================================================
# 校园二手交易平台 - 关闭 standalone Seata 并重启（修复版）
# 修复点:
#   1) 路径修正: $projRoot 取 scripts 的上一级(项目根),
#      避免把 dist-standalone/app 解析到 scripts/dist-standalone/app
#   2) CRLF 兼容: 用"上一行是 seata: 且本行 enabled: true"
#      的行级翻转, 替代对 CRLF 失效的正则替换
#   3) 显式 cd 进临时目录再 jar uf (实测 cwd= 参数方式在锁定态下不可靠)
#   4) jar uf 带重试, 应对停服后文件锁释放的延迟
# 用法(项目根目录执行):
#   powershell -ExecutionPolicy Bypass -File .\scripts\restart-with-seata-off.ps1
#   # 仅停服+注入, 不自动重启(便于在你自己的终端常驻启动):
#   powershell -ExecutionPolicy Bypass -File .\scripts\restart-with-seata-off.ps1 -NoStart
# 说明: payment 的 simulatePayment() 用了 @GlobalTransactional,
#   强依赖 Seata TC(8091)。本机未起 TC 时会抛 500。
#   关闭 standalone 的 Seata 后 payment 走本地事务即可。
# ============================================================
[CmdletBinding()]
param([switch]$NoStart)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot  = Split-Path -Parent $scriptDir
$APP = Join-Path $projRoot "dist-standalone/app"
$stopScript  = Join-Path $projRoot "stop-background.ps1"
$startScript = Join-Path $projRoot "start-background-wmi.ps1"
$JAR = "C:\Program Files\Java\jdk-24\bin\jar.exe"
$services = @("payment", "order", "product", "user")
$entry = "BOOT-INF/classes/application-standalone.yml"

if (-not (Test-Path $JAR)) { Write-Error "找不到 jar.exe: $JAR"; exit 1 }

Write-Host "==== [1/3] 停止服务 (保留 MariaDB/Redis) ===="
if (Test-Path $stopScript) {
    & $stopScript -KeepDb
} else {
    Write-Warning "未找到 $stopScript, 跳过停服(请确保服务已停)"
}
Start-Sleep -Seconds 5

Write-Host "==== [2/3] 注入 seata.enabled=false -> 4 个 jar ===="
foreach ($svc in $services) {
    $jar = Join-Path $APP "$svc-service.jar"
    if (-not (Test-Path $jar)) { Write-Host "  [SKIP] 无 $svc jar: $jar"; continue }
    $jarAbs = (Resolve-Path $jar).Path

    $tmp = Join-Path $env:TEMP ("seata_patch_" + $svc)
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null

    Push-Location $tmp
    try {
        & $JAR xf $jarAbs $entry 2>$null
        if (-not (Test-Path $entry)) { Write-Host "  [SKIP] $svc 无 $entry 入口"; continue }

        # 行级翻转: 上一行是 "seata:" 且本行 enabled: true -> false
        # 兼容 CRLF/LF, 且只改 seata 直下的 enabled, 不误伤 degrade-check 等
        $lines = [IO.File]::ReadAllText($entry) -split "`n"
        $changed = $false
        for ($i = 1; $i -lt $lines.Length; $i++) {
            if ($lines[$i-1].Trim() -eq "seata:" -and $lines[$i] -match "enabled:\s*true") {
                $lines[$i] = $lines[$i] -replace "enabled:\s*true", "enabled: false"
                $changed = $true
            }
        }
        if (-not $changed) {
            Write-Host "  [SKIP] $svc 未找到 seata.enabled:true (可能已是 false)"
            continue
        }
        [IO.File]::WriteAllText($entry, ($lines -join "`n"))

        # jar uf 带重试, 应对停服后文件锁延迟释放
        $ok = $false
        for ($t = 1; $t -le 10; $t++) {
            & $JAR uf $jarAbs $entry 2>$null
            $rc = $LASTEXITCODE
            if ($rc -eq 0) { $ok = $true; break }
            Write-Host "  [retry $t/10] $svc jar uf 失败, 2s 后重试"
            Start-Sleep -Seconds 2
        }
        if (-not $ok) { Write-Host "  [FAIL] $svc jar uf 持续失败, 请确认服务已完全停止"; continue }

        # 校验: 重新抽取检查
        $vdir = Join-Path $env:TEMP ("seata_verify_" + $svc)
        Remove-Item $vdir -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $vdir | Out-Null
        Push-Location $vdir
        & $JAR xf $jarAbs $entry 2>$null
        $chk = [IO.File]::ReadAllText($entry)
        Pop-Location
        Remove-Item $vdir -Recurse -Force -ErrorAction SilentlyContinue
        if ($chk -match "seata:[\s\S]*?enabled:\s*false") {
            Write-Host "  [OK] $svc 已注入 seata.enabled=false"
        } else {
            Write-Host "  [WARN] $svc 校验未通过, 请检查"
        }
    } finally {
        Pop-Location
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($NoStart) {
    Write-Host "==== [3/3] 已跳过自动重启 (-NoStart) ===="
    Write-Host "请在你的终端(项目根目录)执行以常驻启动:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\start-background-wmi.ps1"
} else {
    Write-Host "==== [3/3] 重启服务 ===="
    & $startScript
}

