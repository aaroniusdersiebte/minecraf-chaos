@echo off
echo ========================================
echo   CHAOS STREAM MOD - BUILD SCRIPT
echo ========================================
echo.

REM Check if Gradle is installed
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] Gradle found in PATH
    echo Building with Gradle...
    gradle build
    goto :success
)

REM Check if gradlew wrapper exists
if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [OK] Gradle Wrapper found
    echo Building with Gradle Wrapper...
    .\gradlew.bat build
    goto :success
)

REM Neither gradle nor wrapper found
echo [ERROR] Gradle not found!
echo.
echo Please choose one option:
echo.
echo OPTION 1 - Download Gradle Wrapper JAR manually:
echo   1. Go to: https://raw.githubusercontent.com/gradle/gradle/v8.3.0/gradle/wrapper/gradle-wrapper.jar
echo   2. Save file to: gradle\wrapper\gradle-wrapper.jar
echo   3. Run this script again
echo.
echo OPTION 2 - Install Gradle:
echo   1. Download from: https://gradle.org/releases/
echo   2. Extract and add to PATH
echo   3. Run this script again
echo.
echo OPTION 3 - Use Chocolatey (if installed):
echo   choco install gradle
echo.
pause
exit /b 1

:success
echo.
echo ========================================
echo   BUILD SUCCESSFUL!
echo ========================================
echo.
echo Mod JAR location:
echo   build\libs\chaos-stream-mod-1.0.0.jar
echo.
echo Next steps:
echo   1. Copy JAR to your server's mods folder
echo   2. Restart the server
echo   3. Use test-commands.ps1 to test!
echo.
pause
