#Requires -Version 5.1
<#
.SYNOPSIS
    校园二手交易平台 - 停止脚本
.DESCRIPTION
    按正确顺序优雅关闭所有服务
    停止顺序: 前端 -> 网关 -> 微服务 -> Redis -> MariaDB
.EXAMPLE
    .\stop.ps1
.EXAMPLE
    .\stop.ps1 -Force    # 强制终止所有进程
#>
param([switch]$Force)

$ErrorActionPreference = 'Continue'
$Host.UI.RawUI.WindowTitle = '校园二手交易平台 - 停止中...'

Write-Host ''
Write-Host '========================================' -ForegroundColor Magenta
Write-Host '  校园二手交易平台 - 停止' -ForegroundColor Magenta
Write-Host '========================================' -ForegroundColor Magenta
Write-Host ''

# ========================================
# 停止顺序定义
# ========================================
$Services = @(
    @{ Name='前端';     Match='Frontend' },
    @{ Name='网关';     Match='Gateway' },
    @{ Name='支付服务'; Match='Payment' },
    @{ Name='订单服务'; Match='Order' },
    @{ Name='商品服务'; Match='Product' },
    @{ Name='用户服务'; Match='User' }
)

$totalSteps = $Services.Count + 2
$step = 0

# ========================================
# 停止微服务
# ========================================
foreach ($svc in $Services) {
    $step++
    Write-Host "[$step/$totalSteps] 停止$($svc.Name)..." -ForegroundColor Yellow

    $found = $false
    Get-Process -Name 'javaw' -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine
            if ($cmdLine -match $svc.Match) {
                $found = $true
                Write-Host "  找到进程 PID: $($_.Id)" -ForegroundColor Gray
                if ($Force) {
                    $_.Kill()
                    Write-Host "  已强制终止" -ForegroundColor Red
                } else {
                    $_.CloseMainWindow() | Out-Null
                    Start-Sleep -Seconds 2
                    if (-not $_.HasExited) {
                        $_.Kill()
                    }
                    Write-Host "  已停止" -ForegroundColor Green
                }
            }
        } catch { }
    }

    # 按窗口标题匹配 (备用方案)
    Get-Process -Name 'javaw' -ErrorAction SilentlyContinue | Where-Object {
        $_.MainWindowTitle -match $svc.Match
    } | ForEach-Object {
        if (-not $found) {
            $found = $true
            Write-Host "  按窗口标题匹配 PID: $($_.Id)" -ForegroundColor Gray
            if ($Force) {
                $_.Kill()
            } else {
                $_.CloseMainWindow() | Out-Null
                Start-Sleep -Seconds 2
                if (-not $_.HasExited) { $_.Kill() }
            }
            Write-Host "  已停止" -ForegroundColor Green
        }
    }

    if (-not $found) {
        Write-Host "  (未运行)" -ForegroundColor DarkGray
    }
    Start-Sleep -Milliseconds 500
}

# ========================================
# Redis 优雅关闭
# ========================================
$step++
Write-Host "[$step/$totalSteps] 停止 Redis..." -ForegroundColor Yellow

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$DistDir = Join-Path $ProjectRoot 'dist-standalone'
$RedisCli = Join-Path $DistDir 'redis\redis-cli.exe'

if (Test-Path $RedisCli) {
    $result = cmd /c "`"$RedisCli`" -p 6379 SHUTDOWN 2>&1"
    if ($LASTEXITCODE -eq 0) {
        Write-Host '  Redis SHUTDOWN 命令执行成功' -ForegroundColor Green
    } else {
        Write-Host "  SHUTDOWN 失败: $result" -ForegroundColor Red
    }
} else {
    Write-Host "  redis-cli.exe 未找到，跳过" -ForegroundColor DarkGray
}

# 兜底: 强制终止 redis-server
Get-Process -Name 'redis-server' -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  强制终止 redis-server (PID: $($_.Id))" -ForegroundColor Red
    $_.Kill()
}
Start-Sleep -Seconds 1

# ========================================
# MariaDB 优雅关闭
# ========================================
$step++
Write-Host "[$step/$totalSteps] 停止 MariaDB..." -ForegroundColor Yellow

$MysqlAdmin = Join-Path $DistDir 'mariadb\bin\mysqladmin.exe'
if (Test-Path $MysqlAdmin) {
    $result = cmd /c "`"$MysqlAdmin`" -u root -pcampus123 -h 127.0.0.1 shutdown 2>&1"
    if ($LASTEXITCODE -eq 0) {
        Write-Host '  MariaDB 已关闭' -ForegroundColor Green
    } else {
        Write-Host "  mysqladmin 失败: $result" -ForegroundColor Red
    }
} else {
    Write-Host "  mysqladmin.exe 未找到" -ForegroundColor DarkGray
}

# 兜底: 强制终止 mysqld
Get-Process -Name 'mysqld' -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  强制终止 mysqld (PID: $($_.Id))" -ForegroundColor Red
    $_.Kill()
}

# ========================================
# 确认
# ========================================
Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host '  所有服务已停止' -ForegroundColor Green

# 验证端口
$ports = @(3306, 6379, 8080, 8081, 8083, 8084, 8085, 80)
$stillOpen = @()
foreach ($p in $ports) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $result = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($result.AsyncWaitHandle.WaitOne(500)) {
            $stillOpen += $p
        }
        $client.Close()
    } catch { }
}
if ($stillOpen.Count -gt 0) {
    Write-Host "  警告: 端口仍被占用: $($stillOpen -join ', ')" -ForegroundColor Yellow
} else {
    Write-Host '  所有端口已释放' -ForegroundColor Green
}

Write-Host "  数据保存在 .\dist-standalone\data\ 目录" -ForegroundColor Gray
Write-Host '========================================' -ForegroundColor Green
Write-Host ''
pause
