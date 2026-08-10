@echo off
rem Build the mod jar offline (uses only the local Gradle cache, no proxy/network needed).
cd /d "%~dp0"
call gradlew.bat build --offline
echo.
echo Done. Jar output is in build\libs\autofishs-*.jar
pause
