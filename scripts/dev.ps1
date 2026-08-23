#Requires -Version 5.1
<#
.SYNOPSIS
  Start / stop / restart local admin-app, portal-app, admin Vite, client Vite.

.DESCRIPTION
  Native Windows processes. Connects to VM MySQL/Redis via repo-root .env.local.
  Does not read or write deploy/.env (Compose / 线上). Does not start Docker Compose.

.EXAMPLE
  .\scripts\dev.ps1 start
  .\scripts\dev.ps1 restart -Rebuild
  .\scripts\dev.ps1 stop
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'init', 'start', 'stop', 'restart', 'status', 'logs')]
    [string]$Command = 'help',

    [ValidateSet('all', 'backend', 'frontend', 'admin', 'portal', 'admin-web', 'client')]
    [string]$Target = 'all',

    [switch]$Rebuild,
    [switch]$DebugJvm,
    [switch]$Follow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$RunDir = Join-Path $Root '.run'
$WebDir = Join-Path $Root 'web'
$DefaultJdk = 'D:\develop\jdk\jdk-26.0.2'

$ServiceOrder = @('admin', 'portal', 'admin-web', 'client')
$Services = @{
    admin = @{
        Kind      = 'java'
        Port      = 8080
        DebugPort = 5005
        Health    = 'http://127.0.0.1:8080/actuator/health/readiness'
        JarGlob   = 'server\admin-app\target\admin-app-*.jar'
        JarSkip   = '\.original$'
    }
    portal = @{
        Kind      = 'java'
        Port      = 8081
        DebugPort = 5006
        Health    = 'http://127.0.0.1:8081/actuator/health/readiness'
        JarGlob   = 'server\portal-app\target\portal-app-*-exec.jar'
        JarSkip   = $null
    }
    'admin-web' = @{
        Kind   = 'node'
        Port   = 5173
        Filter = 'admin'
        Health = 'http://localhost:5173/'
    }
    client = @{
        Kind   = 'node'
        Port   = 5174
        Filter = 'client'
        Health = 'http://localhost:5174/'
    }
}

function Write-Info([string]$Message) { Write-Host $Message -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host $Message -ForegroundColor Green }
function Write-WarnMsg([string]$Message) { Write-Host $Message -ForegroundColor Yellow }
function Write-ErrMsg([string]$Message) { Write-Host $Message -ForegroundColor Red }

function Show-Help {
    @"
本机四进程启停（admin-app / portal-app / admin / client）。不走 Docker Compose，不改 deploy/。

用法:
  .\scripts\dev.ps1 <command> [-Target all|backend|frontend|admin|portal|admin-web|client]

命令:
  init      生成仓库根 .env.local（从 scripts/env.example），与 deploy/.env 无关
  start     启动（缺 jar 会 mvn package）
  stop      停止
  restart   停止再启动
  status    端口 / pid / 健康检查
  logs      看 .run/*.log ；加 -Follow 且指定单个 Target 可跟踪

常用:
  .\scripts\dev.ps1 init
  .\scripts\dev.ps1 start
  .\scripts\dev.ps1 restart -Rebuild
  .\scripts\dev.ps1 restart -Target backend
  .\scripts\dev.ps1 start -Target backend -DebugJvm
  .\scripts\dev.ps1 stop
  .\scripts\dev.ps1 logs -Target admin -Follow

入口: 管理端 http://127.0.0.1:5173   C 端 http://127.0.0.1:5174
说明: scripts/README.md
"@ | Write-Host
}

function Get-SelectedNames {
    switch ($Target) {
        'all' { return @('admin', 'portal', 'admin-web', 'client') }
        'backend' { return @('admin', 'portal') }
        'frontend' { return @('admin-web', 'client') }
        default { return @($Target) }
    }
}

function Get-PidPath([string]$Name) { Join-Path $RunDir "$Name.pid" }
function Get-OutLog([string]$Name) { Join-Path $RunDir "$Name.log" }

function Get-ListeningPid([int]$Port) {
    $lines = & netstat -ano 2>$null
    foreach ($line in $lines) {
        if ($line -notmatch ":$Port\s") { continue }
        if ($line -match "LISTENING\s+(\d+)\s*$") {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Test-ListeningPort([int]$Port) {
    return $null -ne (Get-ListeningPid $Port)
}

function Test-Tcp([string]$HostName, [int]$Port, [int]$TimeoutMs = 2000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($iar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Stop-PidTree([int]$ProcessId) {
    if ($ProcessId -le 0) { return }
    & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
}

function Get-LocalEnvPath {
    if ($env:MKT_ENV_FILE) { return $env:MKT_ENV_FILE }
    return (Join-Path $Root '.env.local')
}

function Read-DotEnv([string]$Path) {
    $map = [ordered]@{}
    foreach ($raw in Get-Content -LiteralPath $Path) {
        $line = $raw.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { continue }
        $key = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1)
        if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $map[$key] = $value
    }
    return $map
}

function Test-DockerDnsEnv($Map) {
    $url = [string]$Map['MKT_DATASOURCE_URL']
    $redis = [string]$Map['MKT_REDIS_HOST']
    if (-not $redis) { $redis = [string]$Map['REDIS_HOST'] }
    if ($url -match '://mysql[:/]') { return $true }
    if ($redis -eq 'redis') { return $true }
    return $false
}

function Resolve-EnvFile {
    $path = Get-LocalEnvPath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "找不到本机配置 $path 。先运行 .\scripts\dev.ps1 init（只写 .env.local，不碰 deploy/.env）。"
    }
    $map = Read-DotEnv $path
    return @{ Path = $path; Map = $map }
}

function Import-ProcessEnv($Map) {
    foreach ($key in $Map.Keys) {
        Set-Item -Path "Env:$key" -Value $Map[$key]
    }
}

function Get-JdbcEndpoint($Map) {
    $url = [string]$Map['MKT_DATASOURCE_URL']
    if ($url -match 'jdbc:mysql://([^:/?]+):?(\d+)?') {
        $p = 3306
        if ($Matches[2]) { $p = [int]$Matches[2] }
        return @{ Host = $Matches[1]; Port = $p }
    }
    throw "MKT_DATASOURCE_URL 无法解析: $url"
}

function Assert-LocalEnv($Map) {
    $db = [string]$Map['MKT_REDIS_DATABASE']
    if (-not $db) { $db = [string]$Map['REDIS_DATABASE'] }
    if (-not $db) { $db = '2' }
    if ($db -ne '2') {
        throw "REDIS_DATABASE 必须是 2，当前是 '$db'（禁止落到 db0/db1）。"
    }
    if (Test-DockerDnsEnv $Map) {
        throw "当前 .env.local 仍是 Compose 主机名 mysql/redis。本机必须用虚拟机 IP，不要从 deploy/.env 拷。"
    }
    foreach ($req in @('MKT_DATASOURCE_URL', 'MKT_DATASOURCE_USERNAME', 'MKT_DATASOURCE_PASSWORD')) {
        if (-not $Map[$req]) { throw "缺少 $req" }
    }
}

function Assert-Middleware($Map) {
    $jdbc = Get-JdbcEndpoint $Map
    $rh = [string]$Map['MKT_REDIS_HOST']
    if (-not $rh) { $rh = [string]$Map['REDIS_HOST'] }
    $rp = [string]$Map['MKT_REDIS_PORT']
    if (-not $rp) { $rp = [string]$Map['REDIS_PORT'] }
    if (-not $rp) { $rp = '6379' }
    if (-not (Test-Tcp $jdbc.Host $jdbc.Port)) {
        throw "MySQL 连不上 $($jdbc.Host):$($jdbc.Port) 。先开虚拟机，或检查 .env.local 里的 JDBC。"
    }
    if (-not (Test-Tcp $rh ([int]$rp))) {
        throw "Redis 连不上 ${rh}:$rp 。先开虚拟机，且必须用 DB 2。"
    }
    Write-Ok "中间件可达  MySQL $($jdbc.Host):$($jdbc.Port)  Redis ${rh}:$rp / DB 2"
}

function Resolve-Jdk26 {
    $candidates = @()
    if ($env:MKT_JAVA_HOME) { $candidates += $env:MKT_JAVA_HOME }
    $candidates += $DefaultJdk
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($jdkDir in $candidates) {
        $java = Join-Path $jdkDir 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $java)) { continue }
        $ver = & $java -version 2>&1 | Out-String
        if ($ver -match 'version "26') {
            return @{ JdkHome = $jdkDir; Java = $java }
        }
    }
    throw "需要 JDK 26。本机默认 PATH 是 25。安装 $DefaultJdk 或设置 MKT_JAVA_HOME。"
}

function Use-Jdk26 {
    $jdk = Resolve-Jdk26
    $env:JAVA_HOME = $jdk.JdkHome
    $env:Path = "$(Join-Path $jdk.JdkHome 'bin');$env:Path"
    return $jdk
}

function Resolve-Pnpm {
    $cmd = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $cmd = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "找不到 pnpm。先在 web/ 执行 pnpm install。"
}

function Resolve-Jar([string]$Name) {
    $svc = $Services[$Name]
    $matches = @(Get-ChildItem -Path (Join-Path $Root $svc.JarGlob) -ErrorAction SilentlyContinue |
        Where-Object { -not $svc.JarSkip -or $_.Name -notmatch $svc.JarSkip } |
        Sort-Object LastWriteTime -Descending)
    if ($matches.Count -eq 0) { return $null }
    return $matches[0].FullName
}

function Invoke-Package {
    Write-Info "mvn -DskipTests -DskipITs package （JAVA_HOME=$env:JAVA_HOME）"
    Push-Location (Join-Path $Root 'server')
    try {
        & mvn -DskipTests -DskipITs package
        if ($LASTEXITCODE -ne 0) { throw "mvn package 失败 (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

function Ensure-FrontendDeps {
    if (-not (Test-Path -LiteralPath (Join-Path $WebDir 'node_modules'))) {
        Write-Info 'web/node_modules 不存在，执行 pnpm install --frozen-lockfile'
        Push-Location $WebDir
        try {
            & pnpm install --frozen-lockfile
            if ($LASTEXITCODE -ne 0) { throw "pnpm install 失败 (exit $LASTEXITCODE)" }
        } finally {
            Pop-Location
        }
    }
}

function Save-Pid([string]$Name, [int]$ProcessId) {
    if (-not (Test-Path -LiteralPath $RunDir)) {
        New-Item -ItemType Directory -Path $RunDir | Out-Null
    }
    Set-Content -LiteralPath (Get-PidPath $Name) -Value $ProcessId -Encoding ascii
}

function Read-SavedPid([string]$Name) {
    $path = Get-PidPath $Name
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $raw = (Get-Content -LiteralPath $path -TotalCount 1).Trim()
    if ($raw -match '^\d+$') { return [int]$raw }
    return $null
}

function Test-Health([string]$Url, [int]$TimeoutSec = 2) {
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500
    } catch {
        return $false
    }
}

function Wait-Health([string]$Name, [int]$TimeoutSec) {
    $svc = $Services[$Name]
    $saved = Read-SavedPid $Name
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ($saved -and -not (Get-Process -Id $saved -ErrorAction SilentlyContinue)) {
            return $false
        }
        if ($svc.Health -and (Test-Health $svc.Health)) { return $true }
        if (-not $svc.Health -and (Test-ListeningPort $svc.Port)) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Stop-Service([string]$Name) {
    $svc = $Services[$Name]
    $saved = Read-SavedPid $Name
    $portPid = Get-ListeningPid $svc.Port
    $killed = $false
    if ($saved) {
        Stop-PidTree $saved
        $killed = $true
    }
    $portPid2 = Get-ListeningPid $svc.Port
    if ($portPid2) {
        Stop-PidTree $portPid2
        $killed = $true
    } elseif ($portPid -and $portPid -ne $saved) {
        Stop-PidTree $portPid
        $killed = $true
    }
    $pidPath = Get-PidPath $Name
    if (Test-Path -LiteralPath $pidPath) { Remove-Item -LiteralPath $pidPath -Force }
    if ($killed) { Write-Ok "已停止 $Name" } else { Write-Host "未运行 $Name" }
}

function Start-Detached([string]$Name, [string]$Exe, [string]$ArgString, [string]$WorkDir) {
    if (-not (Test-Path -LiteralPath $RunDir)) {
        New-Item -ItemType Directory -Path $RunDir | Out-Null
    }
    $log = Get-OutLog $Name
    $wrapper = Join-Path $RunDir "$Name-run.cmd"
    $lines = @(
        '@echo off'
        "cd /d `"$WorkDir`""
        "set `"JAVA_HOME=$($env:JAVA_HOME)`""
        "set `"PATH=%JAVA_HOME%\bin;%PATH%`""
        "`"$Exe`" $ArgString >> `"$log`" 2>&1"
    )
    Set-Content -LiteralPath $wrapper -Value $lines -Encoding ascii
    if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log -Force }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $env:ComSpec
    $psi.Arguments = "/c `"$wrapper`""
    $psi.WorkingDirectory = $WorkDir
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    if (-not $proc) { throw "无法启动 $Name" }
    Start-Sleep -Milliseconds 800
    if ($proc.HasExited) {
        $snippet = ''
        if (Test-Path -LiteralPath $log) { $snippet = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue }
        throw "$Name 立刻退出 (code=$($proc.ExitCode))。JDK=$($env:JAVA_HOME)`n日志 $log`n$snippet"
    }
    Save-Pid $Name $proc.Id
    return $proc.Id
}

function Start-JavaService([string]$Name, $Jdk) {
    $svc = $Services[$Name]
    if (Test-ListeningPort $svc.Port) {
        Write-WarnMsg "$Name 端口 $($svc.Port) 已被占用，跳过启动。"
        return
    }
    $jar = Resolve-Jar $Name
    if (-not $jar) { throw "$Name 找不到可执行 jar，先 -Rebuild 或 mvn package。" }
    $jvmArgs = ''
    if ($DebugJvm) {
        $jvmArgs = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$($svc.DebugPort) "
    }
    $argString = '{0}-jar "{1}"' -f $jvmArgs, $jar
    $oldPort = $env:SERVER_PORT
    $oldFlyway = $env:MKT_FLYWAY_ENABLED
    try {
        $env:SERVER_PORT = [string]$svc.Port
        if ($Name -eq 'portal') { $env:MKT_FLYWAY_ENABLED = 'false' }
        $procId = Start-Detached $Name $Jdk.Java $argString $Root
    } finally {
        if ($null -eq $oldPort) { Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue } else { $env:SERVER_PORT = $oldPort }
        if ($null -eq $oldFlyway) { Remove-Item Env:MKT_FLYWAY_ENABLED -ErrorAction SilentlyContinue } else { $env:MKT_FLYWAY_ENABLED = $oldFlyway }
    }
    $extra = ''
    if ($DebugJvm) { $extra = "  JDWP $($svc.DebugPort)" }
    Write-Info "已启动 $Name pid=$procId port=$($svc.Port) jar=$(Split-Path $jar -Leaf)$extra"
}

function Start-NodeService([string]$Name, [string]$PnpmPath) {
    $svc = $Services[$Name]
    if (Test-ListeningPort $svc.Port) {
        Write-WarnMsg "$Name 端口 $($svc.Port) 已被占用，跳过启动。"
        return
    }
    $procId = Start-Detached $Name $PnpmPath "--filter $($svc.Filter) dev" $WebDir
    Write-Info "已启动 $Name pid=$procId port=$($svc.Port) (pnpm --filter $($svc.Filter) dev)"
}

function Invoke-Init {
    $dest = Get-LocalEnvPath
    if (Test-Path -LiteralPath $dest) {
        Write-WarnMsg ".env.local 已存在，未覆盖: $dest"
        return
    }
    $example = Join-Path $PSScriptRoot 'env.example'
    $legacyRoot = Join-Path $Root '.env'
    if ((Test-Path -LiteralPath $legacyRoot) -and -not (Test-DockerDnsEnv (Read-DotEnv $legacyRoot))) {
        Copy-Item -LiteralPath $legacyRoot -Destination $dest
        Remove-Item -LiteralPath $legacyRoot -Force
        Write-Ok "已把仓库根 .env 挪到 .env.local，并删掉根 .env。deploy/.env 未改，可以还原成 Compose 模板。"
        return
    }
    Copy-Item -LiteralPath $example -Destination $dest
    Write-Ok "已从 scripts/env.example 生成 .env.local，请填虚拟机 IP 和密码。不要改 deploy/.env。"
}

function Invoke-Start {
    $selected = @(Get-SelectedNames)
    $envFile = Resolve-EnvFile
    Assert-LocalEnv $envFile.Map
    Import-ProcessEnv $envFile.Map
    Write-Info "env: $($envFile.Path.Substring($Root.Length).TrimStart('\','/'))"
    Assert-Middleware $envFile.Map
    $jdk = Use-Jdk26
    Write-Info "JDK $($jdk.JdkHome)"

    $needJava = @($selected | Where-Object { $Services[$_].Kind -eq 'java' })
    $needNode = @($selected | Where-Object { $Services[$_].Kind -eq 'node' })
    if ($needJava.Count -gt 0) {
        $missing = @($needJava | Where-Object { -not (Resolve-Jar $_) })
        if ($Rebuild -or $missing.Count -gt 0) {
            Invoke-Package
        }
    }
    $pnpm = $null
    if ($needNode.Count -gt 0) {
        Ensure-FrontendDeps
        $pnpm = Resolve-Pnpm
    }
    foreach ($name in $selected) {
        if ($Services[$name].Kind -eq 'java') {
            Start-JavaService $name $jdk
        } else {
            Start-NodeService $name $pnpm
        }
    }
    foreach ($name in $selected) {
        $timeout = 90
        if ($Services[$name].Kind -eq 'node') { $timeout = 45 }
        if (Wait-Health $name $timeout) {
            Write-Ok "$name 就绪  $($Services[$name].Health)"
        } else {
            $log = Get-OutLog $name
            $tail = ''
            if (Test-Path -LiteralPath $log) {
                $tail = (Get-Content -LiteralPath $log -Tail 20) -join "`n"
            }
            Write-WarnMsg "$name 在 ${timeout}s 内未就绪，看日志: $log"
            if ($tail) { Write-Host $tail }
        }
    }
}

function Invoke-Stop {
    $selected = @(Get-SelectedNames)
    [array]::Reverse($selected)
    foreach ($name in $selected) { Stop-Service $name }
}

function Invoke-Status {
    $envExists = $false
    try {
        $envFile = Resolve-EnvFile
        $envExists = $true
        Write-Host "env  $($envFile.Path)"
        $jdbc = Get-JdbcEndpoint $envFile.Map
        $rh = [string]$envFile.Map['MKT_REDIS_HOST']
        if (-not $rh) { $rh = [string]$envFile.Map['REDIS_HOST'] }
        Write-Host "jdbc $($jdbc.Host):$($jdbc.Port)   redis $rh"
    } catch {
        Write-WarnMsg $_.Exception.Message
    }
    Write-Host ''
    '{0,-12} {1,-6} {2,-8} {3}' -f 'NAME', 'PORT', 'PID', 'HEALTH' | Write-Host
    foreach ($name in $ServiceOrder) {
        $svc = $Services[$name]
        $pidVal = Get-ListeningPid $svc.Port
        $pidText = '-'
        if ($pidVal) { $pidText = [string]$pidVal }
        $health = 'down'
        if ($pidVal -and $svc.Health -and (Test-Health $svc.Health)) { $health = 'up' }
        elseif ($pidVal) { $health = 'listen' }
        '{0,-12} {1,-6} {2,-8} {3}' -f $name, $svc.Port, $pidText, $health | Write-Host
    }
    if (-not $envExists) { return }
}

function Invoke-Logs {
    $selected = @(Get-SelectedNames)
    if ($Follow) {
        if ($selected.Count -ne 1) {
            throw "-Follow 只能配合单个 -Target（admin|portal|admin-web|client）"
        }
        $log = Get-OutLog $selected[0]
        if (-not (Test-Path -LiteralPath $log)) { throw "没有日志 $log" }
        Get-Content -LiteralPath $log -Wait -Tail 80
        return
    }
    foreach ($name in $selected) {
        Write-Host "----- $name -----" -ForegroundColor Cyan
        $log = Get-OutLog $name
        if (Test-Path -LiteralPath $log) {
            Write-Host $log -ForegroundColor DarkGray
            Get-Content -LiteralPath $log -Tail 40
        } else {
            Write-Host "(无日志)" -ForegroundColor DarkGray
        }
    }
}

try {
    switch ($Command) {
        'help' { Show-Help }
        'init' { Invoke-Init }
        'start' { Invoke-Start }
        'stop' { Invoke-Stop }
        'restart' { Invoke-Stop; Invoke-Start }
        'status' { Invoke-Status }
        'logs' { Invoke-Logs }
    }
} catch {
    Write-ErrMsg $_.Exception.Message
    exit 1
}
