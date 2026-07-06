# 停止 多Agent智能助手 本地联调环境
# 用法：在项目根目录执行
#   powershell -ExecutionPolicy Bypass -File scripts/stop-local-deal-desk.ps1

function Stop-Port($port, $name) {
  $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if (-not $conns) { Write-Host "[--] $name not running on $port"; return }
  $pids = $conns | ForEach-Object { $_.OwningProcess } | Select-Object -Unique
  foreach ($p in $pids) {
    $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
    if ($proc) {
      Write-Host "[ok] stopping $name PID=$p ($($proc.ProcessName))"
      try { Stop-Process -Id $p -Force } catch {}
    }
  }
}

Stop-Port 8081 'Backend'
Stop-Port 6379 'Redis'
Stop-Port 3306 'MySQL'

# 关掉 cloudflared quick tunnel
$cfProcs = Get-Process cloudflared -ErrorAction SilentlyContinue
if ($cfProcs) {
  foreach ($p in $cfProcs) {
    Write-Host "[ok] stopping cloudflared PID=$($p.Id)"
    try { Stop-Process -Id $p.Id -Force } catch {}
  }
} else {
  Write-Host "[--] cloudflared not running"
}
