@echo off
cd /d "%~dp0"
echo === Building AI-Commerce ===
call mvn clean install -DskipTests
if %ERRORLEVEL% neq 0 (
    echo BUILD FAILED
    exit /b 1
)
echo === Build Successful ===
echo === Starting Application ===
call mvn spring-boot:run
