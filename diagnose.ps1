# ================================================================
#  CampusMicroMart - Startup Diagnostic Script v2.0
#  Modes: -Mode standalone | docker | dev
#  Quick: -Quick
#  Usage: powershell -ExecutionPolicy Bypass -File diagnose.ps1 -Mode standalone
# ================================================================
param(
    [ValidateSet("standalone","docker","dev")]
    [string]$Mode = "standalone",
    [switch]$Quick
)

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"
$Pss = Split-Path -Parent $MyInvocation.MyCommand.Path
$Rpt = [System.Text.StringBuilder]::new()
$Pass = $Fail = $Warn = 0; $Ck = 0

function WSec($t) { $null = $Rpt.AppendLine("`n" + ("="*56) + "`n  "+$t+"`n"+("="*56)) }
function WChk($l,$s,$d) {
    $script:Ck++
    $i = switch($s){"PASS"{$script:Pass++; "[OK]  "}"FAIL"{$script:Fail++; "[FAIL]"}"WARN"{$script:Warn++; "[WARN]"}}
    $m = "  $i $l"+$(if($d){"  ->  $d"})
    $null = $Rpt.AppendLine($m)
    $c = if($s -eq "PASS"){"Green"}elseif($s -eq "FAIL"){"Red"}else{"Yellow"}
    Write-Host $m -ForegroundColor $c
}

function TcpOk($h,$p,$t=3000) {
    try { $c = New-Object Net.Sockets.TcpClient
        $a = $c.BeginConnect($h,$p,$null,$null)
        $ok = $a.AsyncWaitHandle.WaitOne($t)
        if($ok){try{$c.EndConnect($a)}catch{$ok=$false}}
        $c.Close(); return $ok
    } catch { return $false }
}

function HttpOk($u,$t=5) {
    try { $r = [Net.HttpWebRequest]::Create($u); $r.Timeout=$t*1000
        $r.UserAgent="Diag/1.0"; $r.AllowAutoRedirect=$false
        $resp = $r.GetResponse(); $s=[int]$resp.StatusCode
        $sr = [IO.StreamReader]::new($resp.GetResponseStream())
        $b = $sr.ReadToEnd(); $sr.Close(); $resp.Close()
        $len = [Math]::Min(500,$b.Length)
        return @{Ok=$true; Code=$s; Body=$b.Substring(0,$len)}
    } catch [Net.WebException] {
        $sc = if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{0}
        return @{Ok=$false; Code=$sc; Err=$_.Exception.Message}
    } catch { return @{Ok=$false; Code=0; Err=$_.Exception.Message} }
}

function ProcChk($n) {
    $p = @(Get-Process -Name $n -ErrorAction SilentlyContinue)
    if($p.Count -gt 0){ return "$($p.Count) proc(s): "+$(($p|%{"PID="+$_.Id}) -join ", ") }
    return "NOT RUNNING"
}

function PortOwner($p) {
    try { $c = @(Get-NetTCPConnection -LocalPort $p -State Listen -EA 0)
        if($c){ return $(($c|%{$x=Get-Process -Id $_.OwningProcess -EA 0; "PID="+$_.OwningProcess+"("+$x.ProcessName+")"}) -join "; ") }
    } catch {}; return $null
}

# ===== Config =====
$Cfg = @{
    standalone = @{L="Standalone"; DH="127.0.0.1"; DP=3306; DU="root"; DW="campus123"
                   ME=(Join-Path $Pss "dist-standalone\mariadb\bin\mysql.exe")
                   DD=(Join-Path $Pss "dist-standalone\data\mysql")}
    docker = @{L="Docker"; DH="127.0.0.1"; DP=3306; DU="root"; DW="root"; ME="docker"; DD=$null}
    dev = @{L="Dev"; DH="localhost"; DP=3306; DU="root"; DW="root"; ME="mysql"; DD=$null}
}[$Mode]

# ============ [1] System Environment ============
WSec "[1] System Environment"
$os = Get-CimInstance Win32_OperatingSystem
WChk "OS" "PASS" ("{0}, RAM: {1}MB" -f $os.Caption, [math]::Round($os.TotalVisibleMemorySize/1MB))
try { $jv = & java -version 2>&1 | Select -First 1; WChk "Java Runtime" "PASS" $jv }
catch { WChk "Java Runtime" "FAIL" "java not found! Check JAVA_HOME / PATH" }
if($Cfg.ME -eq "docker") { WChk "MySQL Client" "PASS" "via docker exec" }
elseif(Test-Path $Cfg.ME) { WChk "MySQL Client" "PASS" $Cfg.ME }
else {
    try { $mv = & mysql --version 2>&1; WChk "MySQL Client" "PASS" $mv }
    catch { WChk "MySQL Client" "WARN" "mysql.exe not found (DB checks may fail)" }
}
if($Mode -eq "docker") {
    try { & docker info 2>&1 | Out-Null; WChk "Docker Engine" "PASS" "running" }
    catch { WChk "Docker Engine" "FAIL" "not running or not installed" }
}

# ============ [2] Process Status ============
WSec "[2] Process Status"
$procs = @(
    @{n="mysqld";l="MySQL/MariaDB"},
    @{n="redis-server";l="Redis"},
    @{n="java";l="Java (Backend)"},
    @{n="node";l="Node.js (Frontend Dev)"},
    @{n="nginx";l="Nginx (Frontend Prod)"}
)
foreach($ep in $procs) {
    $pi = ProcChk $ep.n
    if($pi.StartsWith("NOT")) { WChk $ep.l "WARN" $pi }
    else { WChk $ep.l "PASS" $pi }
}
if($Mode -eq "standalone") {
    $md = ProcChk "mariadbd"
    if($md.StartsWith("NOT")) { WChk "Embedded MariaDB" "FAIL" "mariadbd not running" }
}

# ============ [3] Port Conflicts ============
WSec "[3] Port Status"
$sp = @(8080,8081,8083,8084,8085,5173,80)
$sn = @("Gateway:8080","Product:8081","Order:8083","User:8084","Payment:8085","Vite:5173","Nginx:80")
$ip = @(3306,6379,8848,8858,9876)
$in = @("MySQL:3306","Redis:6379","Nacos:8848","Sentinel:8858","RocketMQ:9876")

WSec "  -- Service Ports --"
for($i=0;$i -lt $sp.Count;$i++) {
    $o = PortOwner $sp[$i]
    WChk $sn[$i] $(if($o){"PASS"}else{"WARN"}) $(if($o){$o}else{"not listening"})
}
WSec "  -- Infrastructure Ports --"
for($i=0;$i -lt $ip.Count;$i++) {
    $o = PortOwner $ip[$i]
    WChk $in[$i] $(if($o){"PASS"}else{"WARN"}) $(if($o){$o}else{"not listening"})
}

# ============ [4] Database Connectivity ============
WSec "[4] Database Connectivity"
$dbOk = TcpOk $Cfg.DH $Cfg.DP 3000
WChk ("MySQL TCP {0}:{1}" -f $Cfg.DH, $Cfg.DP) $(if($dbOk){"PASS"}else{"FAIL"}) $(if($dbOk){"reachable"}else{"NOT reachable - is DB started?"})

$loginOk = $false
# Build mysql command array (avoid scriptblock scoping issues)
function Run-Sql($exe, $user, $pass, $dbHost, $port, $sql, $db) {
    if($exe -eq "docker") {
        return & docker exec -i campus-mysql mysql "--user=$user" "--password=$pass" "--execute=$sql" --batch --skip-column-names $db 2>&1
    }
    $exePath = if(Test-Path $exe) { $exe } else { "mysql" }
    return & $exePath "--user=$user" "--password=$pass" "--host=$dbHost" "--port=$port" --connect-timeout=5 "--execute=$sql" --batch --skip-column-names $db 2>&1
}

if($dbOk) {
    $me = $Cfg.ME; $du = $Cfg.DU; $dw = $Cfg.DW; $dh = $Cfg.DH; $dp = $Cfg.DP
    try {
        $r = Run-Sql $me $du $dw $dh $dp "SELECT 1" "mysql"
        if($LASTEXITCODE -eq 0 -and "$r" -match "1") {
            $loginOk = $true; WChk "DB Login" "PASS" "user=$du, auth OK"
        } else {
            WChk "DB Login" "FAIL" "auth failed: $r"
            $alt = if($dw -eq "campus123"){"root"}else{"campus123"}
            try {
                $r2 = Run-Sql $me $du $alt $dh $dp "SELECT 1" "mysql"
                if($LASTEXITCODE -eq 0) { WChk "Alt Password" "PASS" "alt password '$alt' works" }
            } catch {}
        }
    } catch { WChk "DB Login" "FAIL" "exception: $_" }
}

$dbs = @("campus_user","campus_product","campus_order","campus_payment","seata")
if($loginOk) {
    WSec "  -- Database & Tables --"
    foreach($db in $dbs) {
        try {
            $tbls = Run-Sql $Cfg.ME $Cfg.DU $Cfg.DW $Cfg.DH $Cfg.DP ("USE $db; SHOW TABLES") $db
            $tl = (($tbls | Where-Object { $_ -notmatch "^Tables_in" -and $_.Trim() }) -join ", ").Trim()
            if($tl) { WChk $db "PASS" "tables: $tl" }
            else {
                $de = Run-Sql $Cfg.ME $Cfg.DU $Cfg.DW $Cfg.DH $Cfg.DP ("SELECT 1 FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$db'") "mysql"
                if("$de" -match "1") { WChk $db "FAIL" "DB exists but NO tables! Run init.sql" }
                else { WChk $db "FAIL" "DATABASE '$db' does not exist!" }
            }
        } catch { WChk $db "FAIL" "query error: $_" }
    }
}

# Data dir check (standalone only)
if($Mode -eq "standalone" -and $Cfg.DD) {
    WSec "  -- Data Directory Integrity --"
    if(Test-Path $Cfg.DD) {
        $ib = Test-Path (Join-Path $Cfg.DD "ibdata1")
        $ms = Test-Path (Join-Path $Cfg.DD "mysql")
        $in = Test-Path (Join-Path $Cfg.DD ".initialized")
        WChk "ibdata1 (core data)" $(if($ib){"PASS"}else{"FAIL"}) $(if($ib){"exists"}else{"MISSING! Re-init required"})
        WChk "mysql system db" $(if($ms){"PASS"}else{"FAIL"}) $(if($ms){"exists"}else{"MISSING! Incomplete init"})
        WChk ".initialized marker" $(if($in){"WARN"}else{"PASS"}) $(if($in){"exists (may hide init failures!)"}else{"clean"})
        if(-not $ib -or -not $ms) {
            WChk "*** ACTION ***" "FAIL" "Delete $($Cfg.DD) directory then restart!"
        }
    } else { WChk "Data directory" "FAIL" "$($Cfg.DD) does NOT exist" }
}

# ============ [5] Middleware Connectivity ============
WSec "[5] Middleware Connectivity"
$mw = [ordered]@{Redis=6379; Nacos=8848; Seata=7091; RocketMQ=9876; Sentinel=8858}
foreach($k in $mw.Keys) {
    $ok = TcpOk "127.0.0.1" $mw[$k] 2000
    WChk ("$k ({0})" -f $mw[$k]) $(if($ok){"PASS"}else{"WARN"}) $(if($ok){"reachable"}else{"not reachable"})
}

# ============ [6] Microservice Health ============
WSec "[6] Microservice Health (Actuator)"
$svcs = @(
    @{N="Gateway";         P=8080; H="/actuator/health"},
    @{N="Product Service"; P=8081; H="/actuator/health"},
    @{N="Order Service";   P=8083; H="/actuator/health"},
    @{N="User Service";    P=8084; H="/actuator/health"},
    @{N="Payment Service"; P=8085; H="/actuator/health"}
)
foreach($s in $svcs) {
    $url = "http://127.0.0.1:$($s.P)$($s.H)"
    $r = HttpOk $url 5
    if($r.Ok -and $r.Code -eq 200) {
        $st = "?"
        try { $j = $r.Body | ConvertFrom-Json; $st = $j.status } catch {}
        WChk $s.N "PASS" "HTTP 200, status=$st"
    } else {
        WChk $s.N "FAIL" "HTTP $($r.Code): $($r.Err)"
    }
}

# Gateway API chain test
$gwApi = HttpOk "http://127.0.0.1:8080/api/product/category/list" 5
WChk "Gateway API Chain" $(if($gwApi.Ok -and $gwApi.Code -eq 200){"PASS"}else{"FAIL"}) $(if($gwApi.Ok){"HTTP $($gwApi.Code)"}else{$gwApi.Err})

# ============ [7] Frontend ============
WSec "[7] Frontend"
$feV = HttpOk "http://127.0.0.1:5173" 3
WChk "Vite Dev (5173)" $(if($feV.Ok){"PASS"}else{"WARN"}) $(if($feV.Ok){"HTTP $($feV.Code)"}else{"not running"})
$feN = HttpOk "http://127.0.0.1:80" 3
WChk "Nginx (80)" $(if($feN.Ok){"PASS"}else{"WARN"}) $(if($feN.Ok){"HTTP $($feN.Code)"}else{"not running"})

# ============ [8] Deployment Integrity ============
if($Mode -eq "standalone") {
    WSec "[8] Deployment Integrity (JARs)"
    $jd = Join-Path $Pss "dist-standalone"
    if(Test-Path $jd) {
        $jars = @("campus-gateway.jar","campus-user-service.jar","campus-product-service.jar",
                  "campus-order-service.jar","campus-payment-service.jar")
        foreach($j in $jars) {
            $jp = Join-Path $jd $j
            if(Test-Path $jp) {
                $sz = [math]::Round((Get-Item $jp).Length/1MB,1)
                WChk $j $(if($sz -gt 1){"PASS"}else{"WARN"}) "$sz MB"
            } else { WChk $j "FAIL" "MISSING!" }
        }
        $sq = Join-Path $jd "sql\init.sql"
        WChk "init.sql" $(if(Test-Path $sq){"PASS"}else{"FAIL"}) $(if(Test-Path $sq){"exists"}else{"MISSING!"})
        $mq = Join-Path $jd "mariadb\bin\mysqld.exe"
        WChk "Embedded MariaDB" $(if(Test-Path $mq){"PASS"}else{"FAIL"}) $(if(Test-Path $mq){"exists"}else{"MISSING!"})
    } else { WChk "dist-standalone" "FAIL" "directory not found!" }
}

# ============ [9] Error Log Analysis ============
if(-not $Quick) {
    WSec "[9] Error Log Analysis"
    $patterns = "(ERROR|FATAL|Exception:|Caused by:|Connection refused|Access denied|Unknown database|Communications link failure)"
    $logDirs = @(
        (Join-Path $Pss "logs"),
        (Join-Path $Pss "dist-standalone\logs")
    )
    $found = $false
    foreach($ld in $logDirs) {
        if(Test-Path $ld) {
            Get-ChildItem $ld -Filter "*.log" -EA 0 | ForEach-Object {
                $errs = Select-String -Path $_.FullName -Pattern $patterns -SimpleMatch:$false | Select -First 5
                if($errs) {
                    $found = $true
                    WChk $_.Name "FAIL" "errors detected"
                    $errs | ForEach-Object {
                        $ln = $_.Line.Trim()
                        if($ln.Length -gt 200) { $ln = $ln.Substring(0,200)+"..." }
                        WChk "  ->" "FAIL" $ln
                    }
                }
            }
        }
    }
    # Root-level service logs
    Get-ChildItem $Pss -Filter "*.log" -EA 0 | Where-Object { $_.Name -match "(gateway|order-service|product-service|user-service|payment-service)" } | ForEach-Object {
        $errs = Select-String -Path $_.FullName -Pattern $patterns -SimpleMatch:$false | Select -First 5
        if($errs) {
            $found = $true
            WChk $_.Name "FAIL" "errors detected"
            $errs | ForEach-Object {
                $ln = $_.Line.Trim()
                if($ln.Length -gt 200) { $ln = $ln.Substring(0,200)+"..." }
                WChk "  ->" "FAIL" $ln
            }
        }
    }
    if(-not $found) { WChk "All logs" "PASS" "No critical errors found in log files" }
} else {
    WSec "[9] Log analysis SKIPPED (Quick mode)"
}

# ============ [10] Summary ============
WSec "[10] DIAGNOSTIC SUMMARY"
$total = $Pass + $Fail + $Warn
$sum = @"

   Checks run:  $Ck  |  [OK]: $Pass  |  [FAIL]: $Fail  |  [WARN]: $Warn

"@
$null = $Rpt.AppendLine($sum)

if($Fail -eq 0 -and $Warn -le 2) {
    $verdict = "System is HEALTHY - all critical components operational."
    $vc = "Green"
} elseif($Fail -gt 0) {
    $verdict = @"
$Fail failure(s) detected. Common fixes:
  1. DB missing/not created -> Delete data dir then restart
  2. Port not listening -> Check if service process started (see .log)
  3. Actuator unreachable -> Check service log for exception stacktrace
  4. Auth failed -> Confirm password: 'campus123'(standalone) vs 'root'(dev)
"@
    $vc = "Red"
} else {
    $verdict = "System is mostly OK. $Warn warning(s) to review."
    $vc = "Yellow"
}

$null = $Rpt.AppendLine("  Verdict:").AppendLine($verdict).AppendLine("")
$null = $Rpt.AppendLine("  Report time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$null = $Rpt.AppendLine("  Mode: $Mode")

Write-Host ""
Write-Host ("-"*56) -ForegroundColor $vc
Write-Host $sum -ForegroundColor White
Write-Host "  Verdict: $verdict" -ForegroundColor $vc
Write-Host ("-"*56) -ForegroundColor $vc

# Save
$rp = Join-Path $Pss "diagnose-report.txt"
$Rpt.ToString() | Out-File -FilePath $rp -Encoding UTF8 -Force
Write-Host "`n  Report saved to: $rp`n" -ForegroundColor Green
