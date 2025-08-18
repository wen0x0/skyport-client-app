@echo off
setlocal enabledelayedexpansion

:: Check if java exists
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Java is not installed.
    exit /b 1
)

:: Get Java version string
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr "version"') do (
    set "ver=%%~v"
)

:: Remove quotes
set "ver=%ver:"=%"

:: Parse major version
for /f "tokens=1,2 delims=." %%a in ("%ver%") do (
    set major=%%a
    if "%%a"=="1" set major=%%b
)

:: Check if version is 21
if "%major%"=="21" (
    echo Java 21 is installed. Running JavaFX app...
    call mvnw.cmd clean javafx:run
) else (
    echo Java 21 is not installed. Current version: %ver%
    exit /b 1
)

endlocal
