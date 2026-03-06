# Screenshoter - Android Automation for Kiwi Browser

This app allows you to automate interactions with the Kiwi Browser using simple `.txt` scripts.

## How to Use

1.  **Enable Accessibility Service**: Go to Android Settings > Accessibility and enable the "Screenshoter" (or "uitreecapture") service.
2.  **Grant Storage Permission**: Ensure the app has permission to read and write to external storage.
3.  **Create a Script**: Create a file named `script.txt` in the `/sdcard/Controller/` directory on your device.
4.  **Run the Script**: Open the Screenshoter app and tap the "RUN SCRIPT" button.
5.  **Check Results**: After execution, a `result.txt` file will be created in the same `/sdcard/Controller/` directory, showing which commands succeeded and which failed.

## Script Commands

| Command | Arguments | Description |
|---|---|---|
| `OPEN_KIWI` | None | Launches the Kiwi Browser. |
| `NEW_TAB` | None | Clicks "New tab" (usually in the menu). |
| `NEW_INCOGNITO` | None | Clicks "New incognito tab" (usually in the menu). |
| `GOTO` | `<url>` | Navigates to the specified URL in Kiwi Browser. |
| `CLICK` | `<target>` | Clicks an element by text, ID, or description. |
| `TYPE` | `<target> <text>` or `input<index> <text>` | Types text into an input field. The target can be text, ID, description, or an index (e.g., `input1` for the first input field). |
| `CHECK` | `<target>` | Checks a checkbox or radio button. |
| `UNCHECK` | `<target>` | Unchecks a checkbox. |
| `SCROLL` | `<distance_in_px>` (optional) | Scrolls the screen down by the specified pixel distance. If no distance is provided, it scrolls down by half the screen height. Positive values scroll down, negative values scroll up. |
| `CLICK_AREA` | `<coords>` | Clicks a specific area (`x1,y1,x2,y2`) or coordinate (`x,y`). |
| `WAIT` | `<ms>` | Pauses execution for the specified milliseconds. |

## Example Script

```text
# Open Kiwi Browser
OPEN_KIWI
WAIT 2000

# Go to a website
GOTO https://www.example.com
WAIT 3000

# Type username and password using index-based targeting
TYPE input1 myuser
TYPE input2 mypass

# Check remember me
CHECK "Remember Me"

# Click login
CLICK "Login"
```

## Automation Engine

The automation engine is built into `MyAccessibilityService.kt`. It uses Android\\'s `AccessibilityService` to find and interact with UI elements. It supports finding elements by:
-   **Resource ID**
-   **Text** (exact or partial)
-   **Content Description** (exact or partial)
-   **Input Field Index** (e.g., `input1`, `input2` for `TYPE` command)
-   **Coordinates** (via `CLICK_AREA`)

## Troubleshooting

-   **Service Not Running**: Make sure the accessibility service is enabled.
-   **Element Not Found**: Ensure the target text or ID matches what\\'s on the screen. Try using partial text.
-   **Script File Not Found**: The script must be located at `/sdcard/Controller/script.txt`.