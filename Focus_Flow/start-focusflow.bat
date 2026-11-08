@echo off
setlocal
cd /d "%~dp0"

if not exist "application-local.properties" if not exist "src\main\resources\application-local.properties" (
    echo.
    echo  Database settings are missing. Run this once, then fill in your MySQL password:
    echo    copy application-local.properties.example application-local.properties
    echo    notepad application-local.properties
    echo.
    pause
    exit /b 1
)

rem A JAVA_HOME pointing at nothing would stop Gradle, so fall back to java on PATH.
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

if not exist "build\libs\project-flow-0.0.1-SNAPSHOT.jar" (
    echo  First run: building FOCUS FLOW. This takes a few minutes.
    call gradlew.bat bootJar
    if errorlevel 1 (
        echo  Build failed. FOCUS FLOW needs JDK 21.
        pause
        exit /b 1
    )
)

echo.
echo  FOCUS FLOW - open one of these addresses on your phone:
for /f "tokens=2 delims=:" %%A in ('ipconfig ^| findstr /C:"IPv4"') do (
    for /f "tokens=*" %%B in ("%%A") do echo    http://%%B:8080
)
echo.
"%JAVA_CMD%" -jar build\libs\project-flow-0.0.1-SNAPSHOT.jar %*
pause
