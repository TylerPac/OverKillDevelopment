@echo off
:: Simple dev runner (Windows CMD)
:: Loads environment variables from backend\.env.development
:: and runs the Maven wrapper with the 'dev-mysql' profile.
:: Usage (cmd.exe): backend\run-dev.bat

:: Resolve script directory
set SCRIPT_DIR=%~dp0

set ENV_FILE=%SCRIPT_DIR%.env.development
if not exist "%ENV_FILE%" (
  echo Env file not found. Expected:
  echo   %SCRIPT_DIR%.env.development
  exit /b 1
)

:: Load variables from env file into this process (ignore blank/comment lines)
for /f "usebackq tokens=1* delims== eol=#" %%A in ("%ENV_FILE%") do (
  if not "%%A"=="" set "%%A=%%B"
)

pushd "%SCRIPT_DIR%"
echo Starting backend in dev profile using %ENV_FILE%...
if "%SPRING_PROFILES_ACTIVE%"=="" set SPRING_PROFILES_ACTIVE=dev-mysql
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=%SPRING_PROFILES_ACTIVE%
popd