Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$rootEnvFile = Join-Path $repoRoot 'dev.env'
$backendDevEnvFile = Join-Path $backendDir 'dev.env'
$backendDotEnvDevelopmentFile = Join-Path $backendDir '.env.development'

$loadedKeys = New-Object System.Collections.Generic.List[string]

function Get-DisplayValue([string]$value, [string]$fallback) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $fallback
    }

    return $value
}

function Import-DotEnvFile([string]$path) {
    if (-not (Test-Path $path)) {
        return
    }

    Get-Content $path | ForEach-Object {
        $line = $_.Trim()

        if (-not $line) { return }
        if ($line.StartsWith('#')) { return }

        $parts = $line.Split('=', 2)
        if ($parts.Count -ne 2) { return }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        if ($name) {
            Set-Item -Path ("Env:{0}" -f $name) -Value $value
            $loadedKeys.Add($name) | Out-Null
        }
    }
}

Import-DotEnvFile $rootEnvFile
Import-DotEnvFile $backendDevEnvFile
Import-DotEnvFile $backendDotEnvDevelopmentFile

if ($loadedKeys.Count -gt 0) {
    Write-Host ("Loaded env keys: {0}" -f (($loadedKeys | Sort-Object -Unique) -join ', ')) -ForegroundColor DarkGray
} else {
    Write-Host "No env file found at: $rootEnvFile, $backendDevEnvFile, or $backendDotEnvDevelopmentFile" -ForegroundColor Yellow
}

if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = 'dev-mysql'
}

Write-Host ("Using SPRING_PROFILES_ACTIVE: {0}" -f $env:SPRING_PROFILES_ACTIVE) -ForegroundColor DarkGray
Write-Host ("Using DB_URL: {0}" -f (Get-DisplayValue $env:DB_URL '(not set)')) -ForegroundColor DarkGray
Write-Host ("Using DB_USERNAME: {0}" -f (Get-DisplayValue $env:DB_USERNAME '(not set)')) -ForegroundColor DarkGray

$mavenWrapper = Join-Path $backendDir 'mvnw.cmd'
if (-not (Test-Path $mavenWrapper)) {
    throw "Maven wrapper not found at $mavenWrapper"
}

Push-Location $backendDir
try {
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$env:SPRING_PROFILES_ACTIVE"
} finally {
    Pop-Location
}
