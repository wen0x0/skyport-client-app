@echo off
REM Check if Java is installed
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo Java is not installed.
    exit /b 1
)

REM Get Java version
for /f "tokens=2 delims==" %%i in ('"java -version 2>&1 | findstr version"') do set JAVAVER=%%i

REM Remove quotes from version string
set JAVAVER=%JAVAVER:"=%

REM Extract major version
for /f "tokens=1 delims=." %%a in ("%JAVAVER%") do set MAJOR=%%a

REM For versions with format like 1.8.0_xx
if "%MAJOR%"=="1" (
    for /f "tokens=2 delims=." %%b in ("%JAVAVER%") do set MAJOR=%%b
)

REM Check if Java version is 21
if "%MAJOR%"=="21" (
    echo Java 21 is installed.
    call mvnw.cmd clean javafx:run
) else (
    echo Java 21 is not installed. Current version: %JAVAVER%
    exit /b 1
)
