@echo off
rem Copies a FOCUS FLOW database to a .sql file outside the project. Reads only.
rem Usage: export-data.bat [port] [user] [database]
rem The file holds your attendance and work records: move it privately, never commit it.
setlocal
set "PORT=%~1"
if not defined PORT set "PORT=8181"
set "DB_USER=%~2"
if not defined DB_USER set "DB_USER=root"
set "DB=%~3"
if not defined DB set "DB=focus_flow"

set "BIN="
where mysqldump >nul 2>&1
if errorlevel 1 for /d %%D in ("%ProgramFiles%\MySQL\MySQL Server *") do if exist "%%~D\bin\mysqldump.exe" set "BIN=%%~D\bin\"
if not defined BIN where mysqldump >nul 2>&1
if not defined BIN if errorlevel 1 (
    echo  mysqldump was not found. Install MySQL Server 8.0 or add its bin folder to PATH.
    exit /b 1
)

if not defined MYSQL_PWD for /f "delims=" %%P in ('powershell -NoProfile -Command "$p=Read-Host 'MySQL password' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"') do set "MYSQL_PWD=%%P"

for /f %%T in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%T"
set "OUT_DIR=%USERPROFILE%\focus_flow_backups"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
set "OUT=%OUT_DIR%\focus_flow_backup_%STAMP%.sql"

"%BIN%mysqldump" -h 127.0.0.1 -P %PORT% -u %DB_USER% --single-transaction --skip-add-drop-table --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file="%OUT%" %DB%
if errorlevel 1 (
    echo  Export failed. Nothing was changed.
    if exist "%OUT%" del "%OUT%"
    exit /b 1
)
echo  Exported to: %OUT%
