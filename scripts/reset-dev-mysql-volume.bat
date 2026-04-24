@echo off
setlocal
cd /d "%~dp0.."
echo Stopping dev MySQL and removing volume mysql_dev_data...
docker compose -f docker-compose.dev.yml down -v
if errorlevel 1 (
  echo Failed to reset MySQL volume.
  exit /b 1
)
echo Dev MySQL volume reset complete.
