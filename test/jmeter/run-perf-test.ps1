#requires -Version 5.1
<#
.SYNOPSIS
  CampusMicroMart JMeter 三场景压测运行器（CLI 模式，产出 HTML 报告）
.DESCRIPTION
  场景一 read  = 商品检索（读，公开接口）
  场景二 login = 用户登录（认证，CSV 参数化）
  场景三 order = 创建订单（写，登录+下单同线程组）
  每个场景独立跑一遍，结果落在 test\results\<时间戳>\<场景>\（result.jtl + html 报告）
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1 -Scenario order -OrderThreads 100 -Loops 10
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File test\jmeter\run-perf-test.ps1 -Scenario all -Ramp 30
#>
param(
    [ValidateSet("read", "login", "order", "all")]
    [string]$Scenario = "all",
    [int]$ReadThreads = 100,
    [int]$LoginThreads = 50,
    [int]$OrderThreads = 50,
    [int]$Loops = 0,
    [int]$Ramp = 30,
    [int]$ProductId = 1031,
    [switch]$OpenReport
)

$ErrorActionPreference = "Stop"

# ---- 定位 JMeter ----
$jmeterBat = $null
if ($env:JMETER_HOME) {
    $candidate = Join-Path $env:JMETER_HOME "bin\jmeter.bat"
    if (Test-Path $candidate) { $jmeterBat = $candidate }
}
if (-not $jmeterBat) {
    $guesses = @(
        "C:\apache-jmeter-5.6.3\bin\jmeter.bat",
        "C:\apache-jmeter\bin\jmeter.bat",
        "$env:USERPROFILE\apache-jmeter-5.6.3\bin\jmeter.bat",
        "$env:USERPROFILE\Downloads\apache-jmeter-5.6.3\bin\jmeter.bat",
        "C:\Program Files\apache-jmeter-5.6.3\bin\jmeter.bat"
    )
    foreach ($g in $guesses) { if (Test-Path $g) { $jmeterBat = $g; break } }
}
if (-not $jmeterBat) {
    Write-Host "[ERROR] 未找到 JMeter。请先下载解压 apache-jmeter-5.6.3.zip 并设置环境变量 JMETER_HOME。" -ForegroundColor Red
    Write-Host "        下载: https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.zip"
    exit 1
}
Write-Host "[INFO] JMeter: $jmeterBat"

# ---- 切换到项目根目录（CSV 相对路径依赖它） ----
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot
Write-Host "[INFO] 工作目录: $projectRoot"

# ---- 前置检查：服务与压测商品 ----
$gw = Test-NetConnection -ComputerName localhost -Port 8090 -InformationLevel Quiet -WarningAction SilentlyContinue
if (-not $gw) {
    Write-Host "[ERROR] 网关 8090 未监听，请先启动服务（start-background-wmi.ps1）。" -ForegroundColor Red
    exit 1
}
Write-Host "[INFO] 网关 8090 正常；压测商品 PRODUCT_ID=$ProductId（库存需足够，见 prepare-perf-data.sql）"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outRoot = Join-Path $projectRoot "test\results\$stamp"
New-Item -ItemType Directory -Path $outRoot -Force | Out-Null

function Invoke-Scenario {
    param(
        [string]$Name,
        [string]$Plan,
        [int]$Threads,
        [string]$ThreadsProp
    )
    $scenarioDir = Join-Path $outRoot $Name
    New-Item -ItemType Directory -Path $scenarioDir -Force | Out-Null
    $jtl = Join-Path $scenarioDir "result.jtl"
    $html = Join-Path $scenarioDir "html"

    $jmeterArgs = @(
        "-n",
        "-t", "test\jmeter\$Plan",
        "-l", $jtl,
        "-e", "-o", $html,
        "-JRAMP=$Ramp",
        "-JPRODUCT_ID=$ProductId",
        "-J$ThreadsProp=$Threads"
    )
    if ($Loops -gt 0) {
        $loopsProp = switch ($Name) {
            "read"  { "L_READ" }
            "login" { "L_LOGIN" }
            "order" { "L_ORDER" }
        }
        $jmeterArgs += "-J$loopsProp=$Loops"
    }

    Write-Host ""
    Write-Host "==== 场景 [$Name] 开始：$Threads 并发 / ramp ${Ramp}s / 计划 $Plan ====" -ForegroundColor Cyan
    & cmd /c "`"$jmeterBat`" $($jmeterArgs -join ' ')"
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Host "[WARN] 场景 [$Name] JMeter 退出码 $code" -ForegroundColor Yellow
    } else {
        Write-Host "[OK] 场景 [$Name] 完成，报告: $html\index.html" -ForegroundColor Green
    }
}

$targets = @()
if ($Scenario -eq "all") { $targets = @("read", "login", "order") } else { $targets = @($Scenario) }

foreach ($t in $targets) {
    switch ($t) {
        "read"  { Invoke-Scenario -Name "read"  -Plan "plan-read.jmx"  -Threads $ReadThreads   -ThreadsProp "T_READ" }
        "login" { Invoke-Scenario -Name "login" -Plan "plan-login.jmx" -Threads $LoginThreads -ThreadsProp "T_LOGIN" }
        "order" { Invoke-Scenario -Name "order" -Plan "plan-order.jmx" -Threads $OrderThreads -ThreadsProp "T_ORDER" }
    }
}

Write-Host ""
Write-Host "==== 全部完成，结果目录: $outRoot ====" -ForegroundColor Green
Get-ChildItem $outRoot -Directory | ForEach-Object {
    $idx = Join-Path $_.FullName "html\index.html"
    if (Test-Path $idx) { Write-Host "  $($_.Name): $idx" }
}
if ($OpenReport) {
    $first = Join-Path $outRoot "$($targets[0])\html\index.html"
    if (Test-Path $first) { Start-Process $first }
}
