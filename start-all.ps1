# CampusMicroMart - Build and Start (silent mode)
#
# This launcher used to spawn five minimized PowerShell windows:
#     Start-Process powershell -ArgumentList (..., "Read-Host 'Press Enter to close'")
#     -WindowStyle Minimized
# and it force-stopped every java process on the machine
# (Get-Process java | Stop-Process -Force).
#
# It now delegates to start-background-wmi.ps1, the canonical local launcher,
# which creates each process through Win32_Process.Create - no console window
# at all - and remaps product-service to 8082 / gateway to 8090 so the stack
# can coexist with the SQB containers that hold 8081/8080.
#
# Original file kept as: start-all.ps1.bak-20260923
#
# Plain ASCII on purpose: PowerShell 5.1 reads a .ps1 without a UTF-8 BOM as
# ANSI, so non-ASCII text here would be mangled.

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

Write-Host "=== CampusMicroMart - Build and Start ===" -ForegroundColor Cyan
Write-Host "    silent mode: no console window will pop up" -ForegroundColor DarkGray
Write-Host ""

Write-Host "[1/2] Building services..." -ForegroundColor Cyan
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "      BUILD FAILED!" -ForegroundColor Red
    Read-Host
    exit 1
}
Write-Host "      Build OK." -ForegroundColor Green

Write-Host "[2/2] Starting services silently..." -ForegroundColor Cyan
powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'start-background-wmi.ps1')

Write-Host ""
Read-Host "Press Enter to exit"
