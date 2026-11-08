@echo off
rem Loads a file from export-data.bat into an EMPTY database, and refuses anything else.
rem Run it before the first start of FOCUS FLOW against that database.
rem Usage: import-data.bat backup.sql [port] [user] [database]
setlocal
set "FILE=%~1"
if not exist "%FILE%" (
    echo  Usage: import-data.bat backup.sql [port] [user] [database]
    exit /b 1
)
set "PORT=%~2"
if not defined PORT set "PORT=3306"
set "DB_USER=%~3"
if not defined DB_USER set "DB_USER=root"
set "DB=%~4"
if not defined DB set "DB=focus_flow"

set "BIN="
where mysql >nul 2>&1
if errorlevel 1 for /d %%D in ("%ProgramFiles%\MySQL\MySQL Server *") do if exist "%%~D\bin\mysql.exe" set "BIN=%%~D\bin\"
if not defined BIN where mysql >nul 2>&1
if not defined BIN if errorlevel 1 (
    echo  mysql was not found. Install MySQL Server 8.0 or add its bin folder to PATH.
    exit /b 1
)

if not defined MYSQL_PWD for /f "delims=" %%P in ('powershell -NoProfile -Command "$p=Read-Host 'MySQL password' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"') do set "MYSQL_PWD=%%P"

set "MYSQL_ARGS=-h 127.0.0.1 -P %PORT% -u %DB_USER%"
"%BIN%mysql" %MYSQL_ARGS% -e "CREATE DATABASE IF NOT EXISTS `%DB%` CHARACTER SET utf8mb4"
if errorlevel 1 (
    echo  Could not reach MySQL. Check the port, user and password.
    exit /b 1
)

set "TABLES="
for /f %%C in ('call "%BIN%mysql" %MYSQL_ARGS% -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%DB%'"') do set "TABLES=%%C"
if not "%TABLES%"=="0" (
    echo  Database "%DB%" already has tables, so nothing was imported or changed.
    echo  Import into a new database name instead, and set DB_NAME to it in application-local.properties.
    exit /b 1
)

"%BIN%mysql" %MYSQL_ARGS% --default-character-set=utf8mb4 "%DB%" < "%FILE%"
if errorlevel 1 (
    echo  Import failed.
    exit /b 1
)
echo  Imported into database "%DB%".
