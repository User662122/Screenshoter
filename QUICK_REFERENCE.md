# Quick Build Command Reference

## One-Command Build (Windows PowerShell)

From the project root directory `c:\Users\ROHIT DAS\.gemini\antigravity\scratch\Screenshoter\ScreenshotApp`:

```powershell
# Build Debug APK
./gradlew assembleDebug

# Build and Install on connected device
./gradlew installDebug

# Clean and rebuild
./gradlew clean assembleDebug

# Build with verbose output for debugging
./gradlew assembleDebug --stacktrace

# Run app on connected device
./gradlew installDebug && adb shell am start -n com.trojan.autoscreenshot/.MainActivity
```

## APK Location After Build
```
app\build\outputs\apk\debug\app-debug.apk
```

## What Each Component Does

### MainActivity.kt
- Displays the main UI with ngrok URL input
- Manages accessibility service status checking
- Handles Start/Stop button clicks
- Validates user input

### UITreeAccessibilityService.kt
- Runs as an Android accessibility service
- Listens to all accessibility events in the system
- Every 500ms, captures the current UI hierarchy
- Sends the UI tree as JSON to the configured ngrok URL
- Uses OkHttp for network requests

### UITreeExtractor.kt
- Converts Android's AccessibilityNodeInfo hierarchy to JSON
- Extracts element properties: className, text, resourceId, bounds, actions
- Includes state information: visibility, enabled status, clickable, etc.
- Recursively processes child nodes (limited to 50 per level)
- Adds timestamp to each capture

## JSON Output Format

```json
{
  "tree": {
    "className": "android.widget.FrameLayout",
    "text": "",
    "contentDescription": "",
    "resourceId": "android:id/content",
    "packageName": "com.example.app",
    "bounds": {
      "left": 0,
      "top": 0,
      "right": 1080,
      "bottom": 2340,
      "width": 1080,
      "height": 2340
    },
    "isVisibleToUser": true,
    "isEnabled": true,
    "isClickable": false,
    "isLongClickable": false,
    "actions": [],
    "children": [
      {
        "className": "android.view.View",
        "text": "Welcome",
        "contentDescription": "",
        "resourceId": "com.example.app:id/title",
        "bounds": {...},
        "isVisibleToUser": true,
        "isClickable": true,
        "actions": [
          {
            "id": 16,
            "label": "Click"
          }
        ],
        "children": []
      }
    ]
  },
  "timestamp": 1709132400000
}
```

## Next Steps

1. **Install Dependencies**: Install Java JDK and Android SDK
2. **Set Environment Variables**: Configure JAVA_HOME and ANDROID_HOME
3. **Run Build**: Execute `./gradlew assembleDebug`
4. **Install APK**: Use `adb install app/build/outputs/apk/debug/app-debug.apk`
5. **Setup App**: 
   - Enter ngrok URL
   - Enable accessibility service
   - Start capturing UI tree
