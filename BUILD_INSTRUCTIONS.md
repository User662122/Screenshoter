# UI Tree Sender App - Build and Installation Guide

## Prerequisites
1. **Android SDK** - Download from https://developer.android.com/studio
2. **Java JDK 11 or higher** - Download from https://adoptopenjdk.net/ or https://jdk.java.net/
3. **Gradle** - Usually included with Android Studio

## Setup Instructions

### Step 1: Set Environment Variables
```powershell
# Set JAVA_HOME (adjust path based on your JDK installation)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-11.0.x", "User")

# Set ANDROID_HOME (adjust path based on your Android SDK installation)
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\YourUsername\AppData\Local\Android\Sdk", "User")

# Add to PATH
$env:PATH += ";C:\Program Files\Java\jdk-11.0.x\bin"
```

### Step 2: Build the APK

Open PowerShell and navigate to the project directory:
```powershell
cd 'c:\Users\ROHIT DAS\.gemini\antigravity\scratch\Screenshoter\ScreenshotApp'
```

Run the build command:
```powershell
./gradlew assembleDebug
```

### Step 3: Find the APK
After successful build, the APK will be located at:
```
c:\Users\ROHIT DAS\.gemini\antigravity\scratch\Screenshoter\ScreenshotApp\app\build\outputs\apk\debug\app-debug.apk
```

### Step 4: Install on Android Device

**Option A: Using adb (Android Debug Bridge)**
```powershell
adb install app\build\outputs\apk\debug\app-debug.apk
```

**Option B: Using Android Studio**
1. Open Android Studio
2. Go to File > Open
3. Select the ScreenshotApp folder
4. Click Run > Run 'app'
5. Select your device/emulator

**Option C: Manual Installation**
1. Transfer the APK file to your Android device
2. Open the file manager on your device
3. Locate and tap the APK file
4. Tap "Install"
5. Allow unknown sources if prompted

## Post-Installation Setup

1. Launch the UI Tree Sender app
2. Enter your ngrok public URL (e.g., https://1234abcd-5678.ngrok.io)
3. Tap "Enable Accessibility Service"
4. In Settings > Accessibility, enable "UI Tree Sender"
5. Return to the app and tap "Start"
6. The app will begin sending UI tree data to your ngrok endpoint every 500ms

## Project Structure
```
ScreenshotApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/trojan/autoscreenshot/
│   │   │   │   ├── MainActivity.kt              # Main UI
│   │   │   │   ├── UITreeAccessibilityService.kt # Accessibility Service
│   │   │   │   └── UITreeExtractor.kt           # JSON Converter
│   │   │   ├── res/
│   │   │   │   ├── layout/activity_main.xml
│   │   │   │   ├── values/strings.xml
│   │   │   │   └── xml/accessibility_service_config.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew
```

## Dependencies
- androidx.core:core-ktx:1.12.0
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.11.0
- androidx.constraintlayout:constraintlayout:2.1.4
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3
- com.squareup.okhttp3:okhttp:4.11.0
- com.google.code.gson:gson:2.10.1

## Troubleshooting

### Build Fails: "SDK location not found"
Set the SDK location in `local.properties`:
```
sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

### Build Fails: "Java not found"
Install JDK and set JAVA_HOME environment variable properly.

### APK Installation Fails
- Enable "Unknown sources" in Settings > Security
- Use ADB with: `adb install -r app-debug.apk` (force replace)

### App Crashes on Start
- Ensure accessibility service is enabled
- Check logcat output: `adb logcat | grep UITreeAccessibilityService`
