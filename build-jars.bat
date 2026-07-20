@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   WebSocket Message Broker - Local Build Utility
echo ===================================================

:: Check if Maven is available in PATH
where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 goto :has_system_mvn

echo [INFO] Maven not found in PATH.
if not exist ".maven-temp" mkdir ".maven-temp"

set MVN_CMD=%CD%\.maven-temp\apache-maven-3.9.6\bin\mvn.cmd

if exist "%MVN_CMD%" goto :maven_ready

echo [INFO] Downloading portable Maven (3.9.6)...
powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '.maven-temp\maven.zip'"

echo [INFO] Extracting Maven zip...
powershell -Command "Expand-Archive -Path '.maven-temp\maven.zip' -DestinationPath '.maven-temp' -Force"

del /q ".maven-temp\maven.zip" >nul 2>nul
echo [OK] Portable Maven is ready.
goto :start_build

:has_system_mvn
echo [OK] Found Maven on system PATH.
set MVN_CMD=mvn
goto :start_build

:maven_ready
echo [OK] Using cached portable Maven.
goto :start_build

:start_build
:: Build services
for %%s in (api-gateway user-service chat-service) do (
    echo.
    echo ---------------------------------------------------
    echo   🔨 Building Service: %%s
    echo ---------------------------------------------------
    cd %%s
    call "%MVN_CMD%" clean package -DskipTests
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Build failed for %%s. Exiting.
        cd ..
        exit /b 1
    )
    cd ..
)

echo.
echo ===================================================
echo   🎉 Success! All microservice JARs are compiled.
echo   You can now run: docker compose up --build
echo ===================================================
pause
