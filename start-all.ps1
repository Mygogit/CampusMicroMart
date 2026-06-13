$root = "e:\数据\CampusMicroMart"
Set-Location $root

Write-Host "=== CampusMicroMart - Building ===" -ForegroundColor Cyan

# Build
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED!" -ForegroundColor Red
    Read-Host
    exit 1
}
Write-Host "Build OK" -ForegroundColor Green

# Kill old Java (needs admin)
Write-Host "`nKilling old Java processes..." -ForegroundColor Yellow
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 3

# Start services
Write-Host "`n=== Starting Services ===" -ForegroundColor Cyan

$services = @(
    @{Name="user-service"; Port=8084; Wait=0},
    @{Name="product-service"; Port=8081; Wait=8},
    @{Name="order-service"; Port=8082; Wait=10},
    @{Name="payment-service"; Port=8083; Wait=15},
    @{Name="campus-gateway"; Port=8080; Wait=0}
)

foreach ($svc in $services) {
    $logFile = "$root\logs\$($svc.Name).log"
    $jarDir = "$root\$($svc.Name)\target"

    Start-Process powershell -ArgumentList @(
        "-NoProfile", "-Command",
        "Set-Location '$jarDir';",
        "java -jar $($svc.Name)-1.0.0.jar 2>&1 | Out-File -FilePath '$logFile' -Encoding utf8;",
        "Read-Host 'Press Enter to close'"
    ) -WindowStyle Minimized

    Write-Host "  $($svc.Name) (port $($svc.Port)) - started" -ForegroundColor Green

    if ($svc.Wait -gt 0) {
        Start-Sleep $svc.Wait
    }
}

Write-Host "`n=== All services launched! ===" -ForegroundColor Green
Write-Host "Gateway: http://localhost:8080" -ForegroundColor Cyan
Write-Host "Product: http://localhost:8081"
Write-Host "Order:   http://localhost:8082"
Write-Host "Payment: http://localhost:8083"
Write-Host "User:    http://localhost:8084"
Read-Host "`nPress Enter to exit"
