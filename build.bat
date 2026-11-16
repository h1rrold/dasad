@echo off
rem Builds target\RelicPowers-1.0.0.jar  (needs Java 21 + Maven, and internet for the first build)
cd /d "%~dp0"
call mvn -q -B clean package
if errorlevel 1 (echo BUILD FAILED & pause & exit /b 1)
echo.
echo Done - look in the target folder for RelicPowers-1.0.0.jar
pause
