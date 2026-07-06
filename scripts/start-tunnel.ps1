# 启动并常驻 cloudflared quick tunnel，用于 Dify Cloud 联调本机后端 8081
#
# 默认行为：
#   - 若 cloudflared 已起，直接复用并打印当前 URL
#   - 若未起，拉起常驻进程并等待 URL 出现（最多 30 秒）
#   - 把 stdout/stderr 重定向到 logs/cloudflared.out.log / logs/cloudflared.err.log
#
# 用法（在项目根目录）：
#   powershell -ExecutionPolicy Bypass -File scripts/start-tunnel.ps1
#
# 可选参数：
#   -Port       本机后端端口，默认 8081
#   -Restart    已起的情况下先停再起
#   -Stop       仅停止已有的 cloudflared 进程，不重新拉起

[CmdletBinding()]
param(
  [int]$Port = 8081,
  [switch]$Restart,
  [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$logsDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
$outLog = Join-Path $logsDir 'cloudflared.out.log'
$errLog = Join-Path $logsDir 'cloudflared.err.log'

$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue)
if (-not $cf) {
  $cf = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
  if (-not (Test-Path $cf)) {
    throw 'cloudflared not found. Install from https://github.com/cloudflare/cloudflared or run: winget install --id Cloudflare.cloudflared'
  }
}

function Stop-Cloudflared() {
  $procs = Get-Process cloudflared -ErrorAction SilentlyContinue
  if (-not $procs) { Write-Host '[--] cloudflared not running'; return }
  foreach ($p in $procs) {
    Write-Host "[ok] stopping cloudflared PID=$($p.Id)"
    try { Stop-Process -Id $p.Id -Force } catch {}
  }
  Start-Sleep -Seconds 1
}

function Read-TunnelUrl() {
  $lines = @()
  if (Test-Path $outLog) { $lines += Get-Content $outLog -ErrorAction SilentlyContinue }
  if (Test-Path $errLog) { $lines += Get-Content $errLog -ErrorAction SilentlyContinue }
  foreach ($l in $lines) {
    if ($l -match 'https://[a-z0-9-]+\.trycloudflare\.com') {
      return ($matches[0] -replace '\.+$','')
    }
  }
  return $null
}

function Wait-TunnelUrl($TimeoutSec = 30) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $u = Read-TunnelUrl
    if ($u) { return $u }
    Start-Sleep -Seconds 2
  }
  return $null
}

if ($Stop) { Stop-Cloudflared; exit 0 }

$existing = Get-Process cloudflared -ErrorAction SilentlyContinue
if ($existing -and -not $Restart) {
  $u = Wait-TunnelUrl 5
  if ($u) {
    Write-Host '[OK] cloudflared already running, tunnel URL:' -ForegroundColor Green
    Write-Host "     $u"
    Write-Host '     Update Dify Cloud env manually if needed.' -ForegroundColor DarkYellow
    exit 0
  } else {
    Write-Host '[!!] cloudflared process exists but no tunnel URL yet; will restart.' -ForegroundColor Yellow
    $Restart = $true
  }
}

if ($Restart) { Stop-Cloudflared }

Write-Host "[..] starting cloudflared quick tunnel for http://localhost:$Port" -ForegroundColor Yellow
if (Test-Path $outLog) { Remove-Item $outLog }
if (Test-Path $errLog) { Remove-Item $errLog }

$proc = Start-Process -FilePath $cf.Source -ArgumentList @('tunnel','--url',"http://localhost:$Port") -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
Write-Host "[..] cloudflared started PID=$($proc.Id), waiting for URL ..."

$url = Wait-TunnelUrl 30
if (-not $url) {
  Write-Host '[!!] cloudflared did not publish a trycloudflare.com URL within 30s.' -ForegroundColor Red
  Write-Host '     Check logs/cloudflared.err.log for details.'
  exit 2
}

Write-Host '[OK] tunnel up' -ForegroundColor Green
Write-Host "     $url"
Write-Host '     Dify Cloud env is fixed; no local YAML sync is performed.' -ForegroundColor DarkYellow
