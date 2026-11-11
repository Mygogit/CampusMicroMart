$jdkRel = "dist-standalone\jdk\bin\java.exe"
$app = "E:\数据\CampusMicroMart\dist-standalone\app"
$logDir = "E:\数据\CampusMicroMart\logs"
$services = @(
    @{Name="user-service";Jar="user-service.jar"},
    @{Name="product-service";Jar="product-service.jar"},
    @{Name="order-service";Jar="order-service.jar"},
    @{Name="payment-service";Jar="payment-service.jar"},
    @{Name="campus-gateway";Jar="campus-gateway.jar"}
)

foreach ($s in $services) {
    $jarPath = Join-Path $app $s.Jar
    $logFile = Join-Path $logDir "$($s.Name).log"
    $errFile = Join-Path $logDir "$($s.Name).err.log"
    if (Test-Path $logFile) { Remove-Item $logFile -Force -EA SilentlyContinue }
    if (Test-Path $errFile) { Remove-Item $errFile -Force -EA SilentlyContinue }

    # Use cmd /c to avoid .NET path encoding issues with Chinese chars
    $cmdLine = "`"$jdkRel`" -Xms128m -Xmx256m -jar `"$jarPath`""
    Write-Host "Starting $($s.Name): $cmdLine"

    $proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c","cd /d `"$app`" && $cmdLine" -RedirectStandardOutput $logFile -RedirectStandardError $errFile -WindowStyle Hidden -PassThru
    Write-Host "  PID $($proc.Id)"
}
Write-Host "ALL_DONE"
