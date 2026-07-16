# 启动 多Agent智能助手 本地联调环境
# 用法：在项目根目录执行
#   powershell -ExecutionPolicy Bypass -File scripts/start-local-deal-desk.ps1

$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cordysRoot = Join-Path $root 'CordysCRM'
$frontendRoot = Join-Path $cordysRoot 'frontend'
$webRoot = Join-Path $frontendRoot 'packages\web'
$webDistIndex = Join-Path $webRoot 'dist\index.html'
$backendStaticIndex = Join-Path $cordysRoot 'backend\app\src\main\resources\static\index.html'
$logsDir = Join-Path $root '.codex-logs'
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
Set-Location $root

$mysqlExe  = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe'
$mysqlIni  = 'C:\opt\cordys\mysql\my.ini'
$redisExe  = '<path-to-redis-server.exe>'
$redisConf = '/cygdrive/c/opt/cordys/redis/redis.conf'

function Test-Port($port) {
  $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  return [bool]$c
}

function Get-NewestWriteTimeUtc($paths) {
  $files = foreach ($path in $paths) {
    if (-not (Test-Path $path)) { continue }
    $item = Get-Item $path
    if ($item.PSIsContainer) {
      Get-ChildItem -Recurse -File $path -ErrorAction SilentlyContinue
    } else {
      $item
    }
  }
  if (-not $files) { return [datetime]::MinValue }
  return ($files | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
}

function Test-SameFile($left, $right) {
  if (-not (Test-Path $left) -or -not (Test-Path $right)) { return $false }
  return (Get-FileHash $left -Algorithm SHA256).Hash -eq (Get-FileHash $right -Algorithm SHA256).Hash
}

function Build-WebFrontend {
  $pnpm = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
  if (-not $pnpm) { $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue }
  if (-not $pnpm) { throw 'pnpm not found. Install pnpm before building the Web frontend.' }

  Write-Host '[..] Web source is newer than dist. Building @cordys/web...' -ForegroundColor Yellow
  Push-Location $frontendRoot
  try {
    & $pnpm.Source --filter '@cordys/web' build
    if ($LASTEXITCODE -ne 0) { throw "Web frontend build failed with exit code $LASTEXITCODE" }
  } finally {
    Pop-Location
  }
  if (-not (Test-Path $webDistIndex)) { throw "Web build completed without creating $webDistIndex" }
  Write-Host '[OK] Web frontend build completed' -ForegroundColor Green
}

function Stop-CordysBackend {
  $connections = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) { return }

  $pids = $connections | ForEach-Object { $_.OwningProcess } | Select-Object -Unique
  foreach ($processId in $pids) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if (-not $process -or $process.ProcessName -ne 'java' -or $processInfo.CommandLine -notmatch 'cn\.cordys\.Application') {
      throw "Port 8081 is occupied by an unrecognized process (PID=$processId). Refusing to stop it automatically."
    }
    Write-Host "[..] Stopping stale CordysCRM backend PID=$processId..." -ForegroundColor Yellow
    Stop-Process -Id $processId -Force
  }

  $deadline = (Get-Date).AddSeconds(15)
  while ((Test-Port 8081) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
  if (Test-Port 8081) { throw 'CordysCRM backend did not release port 8081 in 15 seconds.' }
}

$webSourcePaths = @(
  (Join-Path $webRoot 'src'),
  (Join-Path $webRoot 'config'),
  (Join-Path $webRoot 'public'),
  (Join-Path $webRoot 'package.json'),
  (Join-Path $frontendRoot 'package.json'),
  (Join-Path $frontendRoot 'pnpm-lock.yaml'),
  (Join-Path $frontendRoot 'pnpm-workspace.yaml')
)
$webSourceTime = Get-NewestWriteTimeUtc $webSourcePaths
$webDistTime = if (Test-Path $webDistIndex) { (Get-Item $webDistIndex).LastWriteTimeUtc } else { [datetime]::MinValue }
if ($webSourceTime -gt $webDistTime) { Build-WebFrontend }
else { Write-Host '[OK] Web dist is current' -ForegroundColor Green }

$backendStaticStale = -not (Test-SameFile $webDistIndex $backendStaticIndex)
if ($backendStaticStale) {
  Write-Host '[..] Spring Boot static resources are older than Web dist.' -ForegroundColor Yellow
  if (Test-Port 8081) { Stop-CordysBackend }
}

if (Test-Port 3306) { Write-Host '[OK] MySQL already running on 3306' -ForegroundColor Green }
else {
  Write-Host '[..] Starting MySQL...' -ForegroundColor Yellow
  Start-Process `
    -FilePath $mysqlExe `
    -ArgumentList @("--defaults-file=$mysqlIni", '--console') `
    -RedirectStandardOutput (Join-Path $logsDir 'mysql-stdout.log') `
    -RedirectStandardError (Join-Path $logsDir 'mysql-stderr.log') `
    -WindowStyle Hidden
}

if (Test-Port 6379) { Write-Host '[OK] Redis already running on 6379' -ForegroundColor Green }
else {
  Write-Host '[..] Starting Redis...' -ForegroundColor Yellow
  Start-Process -FilePath $redisExe -ArgumentList @($redisConf) -WindowStyle Hidden
}

if (Test-Port 8081) { Write-Host '[OK] Backend already running on 8081' -ForegroundColor Green }
else {
  Write-Host '[..] Starting Spring Boot backend (mvnw)...' -ForegroundColor Yellow
  $backendCommand = '.\mvnw.cmd -pl backend/app -am -DskipTests install && .\mvnw.cmd -pl backend/app spring-boot:run "-Dspring-boot.run.jvmArguments=-Dai.deal-desk.dify.tool-token=replace-with-your-own-token -Dai.deal-desk.dify.user-id=admin -Dai.deal-desk.dify.organization-id=100001"'
  Start-Process `
    -FilePath 'cmd.exe' `
    -ArgumentList @('/c', $backendCommand) `
    -WorkingDirectory $cordysRoot `
    -RedirectStandardOutput (Join-Path $logsDir 'app-stdout.log') `
    -RedirectStandardError (Join-Path $logsDir 'app-stderr.log') `
    -WindowStyle Hidden
  Write-Host '     Waiting for /is-login ...' -NoNewline
  $deadline = (Get-Date).AddSeconds(180)
  $up = $false
  while ((Get-Date) -lt $deadline) {
    try {
      $r = Invoke-WebRequest -UseBasicParsing http://localhost:8081/is-login -TimeoutSec 2
      if ($r.StatusCode -eq 200) { $up = $true; break }
    } catch {}
    Start-Sleep -Seconds 3
    Write-Host '.' -NoNewline
  }
  Write-Host ''
  if ($up) { Write-Host '[OK] Backend is up' -ForegroundColor Green }
  else     { Write-Host '[!!] Backend did not come up in 180s' -ForegroundColor Red }
}

# 联调 Dify Cloud 时需要一个可被外网访问的 HTTPS 隧道。
# 当前推荐使用固定 ngrok 域名：
#   ngrok http --url=your-domain.example.com 8081
# cloudflared quick tunnel 仅作为备用方案；如需使用，再手动运行：
#   powershell -ExecutionPolicy Bypass -File scripts\start-tunnel.ps1
$tunnelScript = Join-Path $PSScriptRoot 'start-tunnel.ps1'
if (Test-Path $tunnelScript) {
  Write-Host '[--] Dify Cloud tunnel is not auto-started by this script.' -ForegroundColor DarkYellow
  Write-Host '     Preferred: ngrok http --url=your-domain.example.com 8081' -ForegroundColor DarkYellow
  Write-Host '     Fallback:  powershell -ExecutionPolicy Bypass -File scripts\start-tunnel.ps1' -ForegroundColor DarkYellow
} else {
  Write-Host '[--] start-tunnel.ps1 not found, skipping fallback cloudflared tunnel' -ForegroundColor DarkYellow
}

Write-Host ''
Write-Host 'Open: http://localhost:8081/#/login  (admin / CordysCRM)' -ForegroundColor Cyan
Write-Host '多Agent智能助手: http://localhost:8081/#/ai-deal-desk/index' -ForegroundColor Cyan
