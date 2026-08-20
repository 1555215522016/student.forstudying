@echo off
rem ============================================================================
rem  Maven Wrapper (only-script)
rem   - Reuse cached Maven from ~/.m2/wrapper if present
rem   - Otherwise download Maven once and reuse forever
rem   Usage: mvnw.cmd <maven args>   e.g. mvnw.cmd -v / mvnw.cmd compile
rem ============================================================================
setlocal EnableDelayedExpansion

set "MAVEN_VERSION=3.9.15"
set "PROJECT_DIR=%~dp0"

rem 1) Reuse existing Maven cache under ~/.m2/wrapper
set "MAVEN_CMD="
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%-bin\mvn.cmd" 2^>nul') do if not defined MAVEN_CMD set "MAVEN_CMD=%%i"
if defined MAVEN_CMD (
    cd /d "%PROJECT_DIR%"
    call "!MAVEN_CMD!" %*
    exit /b %errorlevel%
)

rem 2) Cache directory owned by this script
if not defined LOCALAPPDATA set "LOCALAPPDATA=%USERPROFILE%\AppData\Local"
set "CACHE_DIR=%LOCALAPPDATA%\maven"
set "MAVEN_HOME=%CACHE_DIR%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_ZIP=%CACHE_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"

if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    cd /d "%PROJECT_DIR%"
    call "%MAVEN_HOME%\bin\mvn.cmd" %*
    exit /b %errorlevel%
)

echo First run: downloading Maven %MAVEN_VERSION% ... (about 9MB, please wait)
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

rem Try official repo first, then Aliyun mirror
set "URL1=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
set "URL2=https://maven.aliyun.com/repository/central/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

curl -fL --connect-timeout 20 -o "%MAVEN_ZIP%" "%URL1%"
if errorlevel 1 (
    echo Official repo failed, switching to Aliyun mirror...
    curl -fL --connect-timeout 20 -o "%MAVEN_ZIP%" "%URL2%"
)
if errorlevel 1 (
    echo [ERROR] Failed to download Maven. Check your network and retry.
    exit /b 1
)

echo Download finished, extracting...
powershell -NoProfile -Command "Expand-Archive -Force -Path '%MAVEN_ZIP%' -DestinationPath '%CACHE_DIR%'"
del "%MAVEN_ZIP%" 2>nul

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [ERROR] Extract failed: %MAVEN_HOME%\bin\mvn.cmd not found
    exit /b 1
)

cd /d "%PROJECT_DIR%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %errorlevel%
