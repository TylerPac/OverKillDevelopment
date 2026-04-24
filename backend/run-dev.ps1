<#
Simple dev runner for Windows PowerShell.
Loads environment variables from `backend/.env.development` and runs Maven with the `dev-mysql` profile.

Usage (PowerShell):
    .\backend\run-dev.ps1
#>

$backendEnvFile = Join-Path $PSScriptRoot '.env.development'

$envFile = $null
if (Test-Path $backendEnvFile) {
        $envFile = $backendEnvFile
} else {
    Write-Error "Env file not found. Expected:`n - $backendEnvFile"
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
    if (-not $env:SPRING_PROFILES_ACTIVE) {
        $env:SPRING_PROFILES_ACTIVE = 'dev-mysql'
    }
    & .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=$env:SPRING_PROFILES_ACTIVE
} finally {
    Pop-Location
}
