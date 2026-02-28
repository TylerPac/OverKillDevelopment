<#
Simple dev runner for Windows PowerShell.
Loads environment variables from root `.env.development` (fallback: `backend/.env.dev`) and runs Maven with the `dev` profile.

Usage (PowerShell):
    .\backend\run-dev.ps1
#>

$rootEnvFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env.development'
$backendEnvFile = Join-Path $PSScriptRoot '.env.dev'

$envFile = $null
if (Test-Path $rootEnvFile) {
        $envFile = $rootEnvFile
} elseif (Test-Path $backendEnvFile) {
        $envFile = $backendEnvFile
} else {
        Write-Error "Env file not found. Expected either:`n - $rootEnvFile`n - $backendEnvFile"
        exit 1
}

# Load variables from env file into this process environment
Get-Content $envFile | ForEach-Object {
    if ($_ -and ($_ -notmatch '^\s*#')) {
        if ($_ -match '^(.*?)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            Set-Item -Path Env:$name -Value $value
        }
    }
}

Push-Location $PSScriptRoot
try {
    Write-Host "Starting backend in 'dev' profile using $envFile..." -ForegroundColor Cyan
    & .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
} finally {
    Pop-Location
}
