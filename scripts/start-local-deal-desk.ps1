# 启动 多Agent智能助手 本地联调环境
# 用法：在项目根目录执行
#   powershell -ExecutionPolicy Bypass -File scripts/start-local-deal-desk.ps1

$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cordysRoot = Join-Path $root 'CordysCRM'
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
  else     { Write-Host '[!!] Backend did not come up in 90s' -ForegroundColor Red }
}

# 联调 Dify Cloud 时需要一个可被外网访问的 HTTPS 隧道。
# 当前推荐使用固定 ngrok 域名：
#   ngrok http --url=your-ngrok-or-domain.example.com 8081
# cloudflared quick tunnel 仅作为备用方案；如需使用，再手动运行：
#   powershell -ExecutionPolicy Bypass -File scripts\start-tunnel.ps1
$tunnelScript = Join-Path $PSScriptRoot 'start-tunnel.ps1'
if (Test-Path $tunnelScript) {
  Write-Host '[--] Dify Cloud tunnel is not auto-started by this script.' -ForegroundColor DarkYellow
  Write-Host '     Preferred: ngrok http --url=your-ngrok-or-domain.example.com 8081' -ForegroundColor DarkYellow
  Write-Host '     Fallback:  powershell -ExecutionPolicy Bypass -File scripts\start-tunnel.ps1' -ForegroundColor DarkYellow
} else {
  Write-Host '[--] start-tunnel.ps1 not found, skipping fallback cloudflared tunnel' -ForegroundColor DarkYellow
}

Write-Host ''
Write-Host 'Open: http://localhost:8081/#/login  (admin / CordysCRM)' -ForegroundColor Cyan
Write-Host '多Agent智能助手: http://localhost:8081/#/agent/deal-desk' -ForegroundColor Cyan

