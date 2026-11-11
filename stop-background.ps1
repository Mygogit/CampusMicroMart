# ============================================================
# 校园二手交易平台 - 后台服务停止脚本
# 按端口停止: frontend:80 / gateway:8090 / product:8082 /
# order:8083 / user:8084 / payment:8085 / MariaDB:3306(可选)
# 用法: powershell -ExecutionPolicy Bypass -File stop-background.ps1
#       powershell -ExecutionPolicy Bypass -File stop-background.ps1 -KeepDb
#       (加 -KeepDb 保留 MariaDB 与 Redis 不停止)
# ============================================================
param([switch]$KeepDb)
$ErrorActionPreference = 'Continue'

$stopOrder = @(
    @{N='Frontend';       P=80},
    @{N='campus-gateway'; P=8090},
    @{N='payment-service';P=8085},
    @{N='order-service';  P=8083},
    @{N='product-service';P=8082},
    @{N='user-service';   P=8084}
)
if (-not $KeepDb) {
    $stopOrder += @{N='Redis'; P=6379}
}

Write-Host '==== Stop CampusMicroMart services ===='
foreach ($svc in $stopOrder) {
    $conns = Get-NetTCPConnection -LocalPort $svc.P -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $conns) {
        try {
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host ("[STOP] {0,-16} :{1,-6} (pid={2})" -f $svc.N, $svc.P, $conn.OwningProcess)
        } catch { }
    }
}
Start-Sleep -Seconds 2
Write-Host '==== Done ===='
