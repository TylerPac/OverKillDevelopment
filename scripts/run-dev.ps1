Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot 'frontend'
$backendScript = Join-Path $PSScriptRoot 'run-backend-dev.ps1'
$shellPath = (Get-Process -Id $PID).Path

if (-not (Test-Path $backendScript)) {
    throw "Backend launcher not found: $backendScript"
}

if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

$frontendCommand = "& { Set-Location -LiteralPath '$($frontendDir.Replace("'", "''"))'; npm run dev }"

Write-Host 'Starting backend dev profile in a new PowerShell window...' -ForegroundColor Cyan
Start-Process -FilePath $shellPath -WorkingDirectory $repoRoot -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-File', $backendScript
) | Out-Null

Write-Host 'Starting frontend Vite dev server in a new PowerShell window...' -ForegroundColor Cyan
Start-Process -FilePath $shellPath -WorkingDirectory $frontendDir -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-Command', $frontendCommand
) | Out-Null

Write-Host 'Started both dev processes.' -ForegroundColor Green
Write-Host 'Backend: Spring Boot dev-mysql profile (default backend port 8081)' -ForegroundColor DarkGray
Write-Host 'Frontend: Vite dev server (default port 5173)' -ForegroundColor DarkGray
