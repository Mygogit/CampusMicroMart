# CampusMicroMart silent launcher / stopper
# ------------------------------------------------------------------
# Started by 启动.bat / 停止.bat.
#
# Start one process:
#   powershell -NoProfile -ExecutionPolicy Bypass -File _hidden.ps1 `
#       -Exe <exe> -ArgLine "<args>" -Cwd <dir> -Log <logfile>
#   Uses ProcessStartInfo with CreateNoWindow = $true, which allocates
#   NO console window at all (verified: no ConsoleWindowClass window is
#   created, not even a hidden one). Never use cmd "start" / "start /MIN"
#   for this purpose - each one opens a real console window.
#   Paths inside -ArgLine should be relative to -Cwd so that no inner
#   quoting is needed (quoting inside batch arguments is fragile).
#
# Stop everything:
#   ... -Stop [-Force] [-Root <dist dir>]
#   -Stop   : kills java/javaw whose command line references app\*.jar
#             (business services + gateway) or FrontendServer
#   -Force  : additionally kills whatever still holds 3306 / 6379,
#             but only if its executable lives under -Root (so a local
#             MySQL/Redis installed elsewhere is never touched)
# ------------------------------------------------------------------
param(
    [string]$Exe,
    [string]$ArgLine = '',
    [string]$Cwd = '',
    [string]$Log = '',
    [string]$Root = '',
    [switch]$Stop,
    [switch]$Force
)

$ErrorActionPreference = 'Continue'

function Kill-One {
    param($Proc)
    try {
        Stop-Process -Id $Proc.ProcessId -Force -ErrorAction Stop
        return ("{0}({1})" -f $Proc.Name, $Proc.ProcessId)
    } catch {
        return ''
    }
}

if ($Stop) {
    $killed = @()

    # 业务微服务 + 网关（java -jar app\*.jar）与前端（FrontendServer）
    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -like 'java*' -and
            ($_.CommandLine -like '*app\*.jar*' -or $_.CommandLine -like '*FrontendServer*')
        } |
        ForEach-Object { $r = Kill-One $_; if ($r) { $killed += $r } }

    # 残留中间件：仅处理仍占用 3306 / 6379 且可执行文件位于本安装目录下的进程
    if ($Force) {
        $rootFull = ''
        if ($Root) { $rootFull = $Root.TrimEnd('\').ToLower() }
        foreach ($port in @(3306, 6379)) {
            Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
                ForEach-Object {
                    $owner = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $_.OwningProcess) -ErrorAction SilentlyContinue
                    if (-not $owner) { return }
                    $isMine = ($owner.Name -like 'mysqld*' -or $owner.Name -like 'mariadb*' -or $owner.Name -like 'redis-server*')
                    if (-not $isMine) { return }
                    if ($rootFull -and $owner.ExecutablePath) {
                        if (-not $owner.ExecutablePath.ToLower().StartsWith($rootFull)) { return }
                    }
                    $r = Kill-One $owner
                    if ($r) { $killed += $r }
                }
        }
    }

    if ($killed.Count -eq 0) { Write-Output 'STOPPED none' }
    else { Write-Output ('STOPPED ' + $killed.Count + ' : ' + ($killed -join ', ')) }
    return
}

if (-not $Exe) {
    Write-Output 'ERROR: -Exe is required (or use -Stop)'
    return
}

if ($Cwd) { $Cwd = $Cwd.TrimEnd('\') }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
if ($Cwd) { $psi.WorkingDirectory = $Cwd }

if ($Log) {
    # run through cmd so stdout/stderr go straight into the log file
    # (a redirected pipe would deadlock once this script exits)
    $psi.FileName = $env:ComSpec
    $psi.Arguments = '/c ""' + $Exe + '" ' + $ArgLine + ' > "' + $Log + '" 2>&1"'
} else {
    $psi.FileName = $Exe
    $psi.Arguments = $ArgLine
}

try {
    $p = [System.Diagnostics.Process]::Start($psi)
    Write-Output ('STARTED ' + $p.Id + ' : ' + [System.IO.Path]::GetFileName($Exe))
} catch {
    Write-Output ('ERROR: ' + $_.Exception.Message)
}
