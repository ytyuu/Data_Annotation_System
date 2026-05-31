param(
  [int]$Port = 7000
)

$ErrorActionPreference = "Continue"

# 禁用 jline 终端探测，避免 mvnd 输出大量终端相关的 warning
$env:JLINE_TERMINAL = "dumb"
# 禁用 mvnd 的 transfer progress 日志
$env:MVND_NO_TRANSFER_PROGRESS = "true"

function Invoke-CheckedCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,

    [string[]]$Arguments = @()
  )

  & $FilePath @Arguments
  return $LASTEXITCODE
}

function Invoke-StreamingCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,

    [string[]]$Arguments = @()
  )

  & $FilePath @Arguments 2>&1 | ForEach-Object { Write-Host $_ }
  return $LASTEXITCODE
}

$compileExitCode = Invoke-CheckedCommand -FilePath "mvnd" -Arguments @("-q", "compile")
if ($compileExitCode -ne 0) {
  exit $compileExitCode
}

$maxAttempts = 2

for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
  $runExitCode = Invoke-StreamingCommand -FilePath "mvnd" -Arguments @("exec:java", "-Dexec.fork=true")

  if ($runExitCode -eq 0) {
    exit 0
  }

  if ($attempt -ge $maxAttempts) {
    exit $runExitCode
  }

  $portListeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $portListeners) {
    exit $runExitCode
  }

  Write-Host "API exited with code $runExitCode. Releasing port $Port and retrying once..."
  & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\stop-api-port.ps1" -Port $Port

  if ($LASTEXITCODE -ne 0) {
    exit $runExitCode
  }
}
