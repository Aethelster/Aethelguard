@echo off
setlocal

set "MAVEN_VERSION=3.9.10"
set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "DIST_DIR=%WRAPPER_DIR%\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_HOME=%DIST_DIR%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
set "DIST_ZIP=%DIST_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"

if not exist "%MAVEN_CMD%" (
    if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
    echo Downloading Apache Maven %MAVEN_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$ErrorActionPreference = 'Stop';" ^
        "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
        "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%DIST_ZIP%';" ^
        "Expand-Archive -Path '%DIST_ZIP%' -DestinationPath '%DIST_DIR%' -Force"
    if errorlevel 1 (
        echo Failed to download Apache Maven %MAVEN_VERSION%.
        exit /b 1
    )
)

call "%MAVEN_CMD%" %*
endlocal
