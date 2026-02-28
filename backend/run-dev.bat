@echo off
:: Simple dev runner (Windows CMD)
:: Loads environment variables from root .env.development (fallback: backend\.env.dev)
:: and runs the Maven wrapper with the 'dev' profile.
:: Usage (cmd.exe): backend\run-dev.bat

:: Resolve script directory
set SCRIPT_DIR=%~dp0
for %%I in ("%SCRIPT_DIR%..") do set ROOT_DIR=%%~fI

set ENV_FILE=%ROOT_DIR%\.env.development
if not exist "%ENV_FILE%" set ENV_FILE=%SCRIPT_DIR%.env.dev
if not exist "%ENV_FILE%" (
  echo Env file not found. Expected either:
  echo   %ROOT_DIR%\.env.development
  echo   %SCRIPT_DIR%.env.dev
  exit /b 1
)

:: Load variables from env file into this process (ignore blank/comment lines)
for /f "usebackq tokens=1* delims== eol=#" %%A in ("%ENV_FILE%") do (
  if not "%%A"=="" set "%%A=%%B"
)

pushd "%SCRIPT_DIR%"
echo Starting backend in dev profile using %ENV_FILE%...
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
popd