param(
  [int]$Port = 7000
)

$ErrorActionPreference = "Stop"

$connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue

if (-not $connections) {
  Write-Host "No API process is listening on port $Port."
  exit 0
}

$processIds = $connections |
  Select-Object -ExpandProperty OwningProcess -Unique |
  Where-Object { $_ -gt 0 -and $_ -ne $PID }

foreach ($processId in $processIds) {
  $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
  if (-not $process) {
    continue
  }

  Write-Host "Stopping process $($process.ProcessName) ($processId) listening on port $Port."
  Stop-Process -Id $processId -Force
}

Start-Sleep -Milliseconds 300

$remaining = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($remaining) {
  Write-Error "Port $Port is still in use after stopping API processes."
  exit 1
}

Write-Host "Port $Port is free."
