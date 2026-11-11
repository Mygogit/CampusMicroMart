# ============================================================
# fix-seata-and-restart.ps1  (RUN IN YOUR OWN TERMINAL)
# This must be executed by YOU (the interactive user), because the
# AI agent's context cannot terminate your session processes
# (taskkill -> Access denied). Your terminal has the permission.
#
# What it does:
#   1) Stop the 6 microservices (keeps MariaDB / Redis)
#   2) Patch the 4 jars: seata.enabled true -> false
#      (payment's @GlobalTransactional no longer needs Seata TC)
#   3) Restart the microservices
#
# Usage (from project root E:\数据\CampusMicroMart):
#   powershell -ExecutionPolicy Bypass -File .\scripts\fix-seata-and-restart.ps1
#   # to stop+patch only, then start manually:
#   powershell -ExecutionPolicy Bypass -File .\scripts\fix-seata-and-restart.ps1 -NoStart
# ============================================================
[CmdletBinding()]
param([switch]$NoStart)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path   # .../scripts
$projRoot  = Split-Path -Parent $scriptDir                     # project root
$APP = Join-Path $projRoot "dist-standalone/app"
$stopScript  = Join-Path $projRoot "stop-background.ps1"
$startScript = Join-Path $projRoot "start-background-wmi.ps1"
$patchScript = Join-Path $scriptDir "patch_seata_off.py"

# ---- 1) stop ----
Write-Host "==== [1/3] Stopping services (keep MariaDB/Redis) ===="
if (Test-Path $stopScript) {
    & $stopScript -KeepDb
} else {
    Write-Error "Missing $stopScript"; exit 1
}
Start-Sleep -Seconds 5

# ---- 2) patch via python ----
Write-Host "==== [2/3] Patching 4 jars (seata.enabled=false) ===="
$py = $null
$candidates = @(
    "$env:LOCALAPPDATA\Programs\Python\Python314\python.exe",
    "$env:USERPROFILE\.workbuddy\binaries\python\versions\3.13.12\python.exe",
    "python.exe"
)
foreach ($c in $candidates) {
    if (Test-Path $c) { $py = $c; break }
    try { if (Get-Command $c -ErrorAction SilentlyContinue) { $py = $c; break } } catch { }
}
if (-not $py) { Write-Error "Python not found. Install Python 3 or set path."; exit 1 }
Write-Host "Using python: $py"
& $py $patchScript
if ($LASTEXITCODE -ne 0) { Write-Error "Patch step failed (see above)"; exit 1 }

# ---- 3) restart ----
if ($NoStart) {
    Write-Host "==== [3/3] Skipped restart (-NoStart) ===="
    Write-Host "Start manually in this terminal:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\start-background-wmi.ps1"
} else {
    Write-Host "==== [3/3] Restarting services ===="
    & $startScript
}
