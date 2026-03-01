@echo off
setlocal enabledelayedexpansion

REM Set environment variables
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=%APPDATA%\..\Local\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_HOME: %ANDROID_HOME%

REM Verify Java is available
"%JAVA_HOME%\bin\java.exe" -version

REM Change to project directory
cd /d "C:\Users\ROHIT DAS\.gemini\antigravity\scratch\Screenshoter\ScreenshotApp"

REM Run gradle build using bash (via git)
"C:\Program Files\Git\bin\bash.exe" -c "chmod +x ./gradlew && ./gradlew assembleDebug"

REM Check if APK was created
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ===============================================
    echo APK BUILD SUCCESSFUL!
    echo Location: app\build\outputs\apk\debug\app-debug.apk
    echo ===============================================
    dir "app\build\outputs\apk\debug\app-debug.apk"
) else (
    echo APK was not created. Check for errors above.
)

pause
