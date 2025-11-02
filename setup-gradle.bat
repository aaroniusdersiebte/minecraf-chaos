@echo off
echo ========================================
echo   GRADLE WRAPPER SETUP
echo ========================================
echo.

REM Check if gradle-wrapper.jar already exists
if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [OK] Gradle Wrapper already exists!
    echo You can now run: build.bat
    pause
    exit /b 0
)

echo Downloading Gradle Wrapper JAR...
echo.

REM Create directory if it doesn't exist
if not exist "gradle\wrapper" mkdir gradle\wrapper

REM Download using PowerShell
powershell -Command "& {Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'}"

if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo.
    echo [SUCCESS] Gradle Wrapper downloaded!
    echo.
    echo You can now run: build.bat
) else (
    echo.
    echo [ERROR] Download failed!
    echo.
    echo Please download manually:
    echo   URL: https://raw.githubusercontent.com/gradle/gradle/v8.3.0/gradle/wrapper/gradle-wrapper.jar
    echo   Save to: gradle\wrapper\gradle-wrapper.jar
)

echo.
pause
