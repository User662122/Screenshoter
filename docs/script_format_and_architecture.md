# Proposed Script Format and Automation Engine Architecture

This document outlines the proposed `.txt` script format and the architectural considerations for extending the "Screenshoter" Android application to include robust Kiwi Browser automation capabilities. The goal is to enable users to write simple, human-readable scripts to automate various web interactions.

## 1. Extended Script Format

The existing `script.txt` format supports `CLICK`, `SCROLL`, and `WAIT` commands. This proposal introduces new commands and enhancements to provide comprehensive browser automation.

### 1.1 Core Commands (Existing and Enhanced)

| Command | Description | Example Usage |
|---|---|---|
| `CLICK <selector_type> <selector_value>` | Clicks on an element identified by text, content description, resource ID, or coordinates. | `CLICK TEXT "Submit Button"` <br> `CLICK ID "com.kiwibrowser.browser:id/url_bar"` <br> `CLICK COORDS 500 800` |
| `SCROLL <direction>` | Scrolls the current view. `direction` can be `DOWN`, `UP`, `LEFT`, `RIGHT`, `TO_TOP`, `TO_BOTTOM`. | `SCROLL DOWN` <br> `SCROLL TO_BOTTOM` |
| `WAIT <milliseconds>` | Pauses script execution for a specified duration. | `WAIT 2000` |

### 1.2 Kiwi Browser Specific Commands

| Command | Description | Example Usage |
|---|---|---|
| `OPEN_URL <url>` | Opens a specified URL in the current tab. | `OPEN_URL https://www.google.com` |
| `OPEN_NEW_TAB <url>` | Opens a new tab and navigates to the specified URL. | `OPEN_NEW_TAB https://www.example.com` |
| `OPEN_INCOGNITO <url>` | Opens a new incognito tab and navigates to the specified URL. | `OPEN_INCOGNITO https://www.privatesite.com` |
| `CLOSE_CURRENT_TAB` | Closes the currently active tab. | `CLOSE_CURRENT_TAB` |
| `SWITCH_TAB <tab_index>` | Switches to a tab by its index (0-based). | `SWITCH_TAB 1` |
| `GO_BACK` | Navigates back in the browser history. | `GO_BACK` |
| `GO_FORWARD` | Navigates forward in the browser history. | `GO_FORWARD` |

### 1.3 Form Interaction Commands

| Command | Description | Example Usage |
|---|---|---|
| `TYPE <selector_type> <selector_value> <text_to_type>` | Types the specified text into an input field. | `TYPE ID "username_field" "myusername"` <br> `TYPE TEXT "Password" "mypassword"` |
| `CHECK <selector_type> <selector_value>` | Checks a checkbox or selects a radio button. | `CHECK ID "remember_me_checkbox"` |
| `UNCHECK <selector_type> <selector_value>` | Unchecks a checkbox. | `UNCHECK ID "newsletter_signup"` |

### 1.4 Control Flow (Advanced)

| Command | Description | Example Usage |
|---|---|---|
| `IF_EXISTS <selector_type> <selector_value>` | Executes commands if an element exists. | `IF_EXISTS TEXT "Login Button"` |
| `ELSE` | Executes commands if the `IF_EXISTS` condition is false. | `ELSE` |
| `ENDIF` | Marks the end of an `IF_EXISTS` block. | `ENDIF` |
| `LOOP <count>` | Repeats a block of commands a specified number of times. | `LOOP 5` |
| `ENDLOOP` | Marks the end of a `LOOP` block. | `ENDLOOP` |

## 2. Automation Engine Architecture

The automation engine will primarily reside within `MyAccessibilityService.kt`, extending its capabilities to parse and execute the new script commands.

### 2.1 Script Parsing and Execution Flow

1.  **`runScript()` Enhancement**: The `runScript` function will be modified to handle the extended command set. Each line of `script.txt` will be parsed to identify the command and its arguments.
2.  **Command Handlers**: A `when` statement (or similar structure) will dispatch to specific handler functions for each command (e.g., `handleOpenUrl`, `handleClick`, `handleType`).
3.  **Accessibility Node Interaction**: All interactions will leverage `AccessibilityNodeInfo` to find and interact with UI elements. This includes:
    *   **Finding Nodes**: `findNodeByText`, `findNodeById`, `findNodeByContentDescription` will be implemented or enhanced.
    *   **Performing Actions**: `performAction(AccessibilityNodeInfo.ACTION_CLICK)`, `performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)`, `performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)`, etc.
    *   **Gesture Dispatching**: For actions like scrolling or clicking by coordinates, `dispatchGesture` will be used with `GestureDescription`.

### 2.2 Kiwi Browser Specifics

*   **Package Name**: Kiwi Browser's package name (`com.kiwibrowser.browser`) will be used to ensure actions are targeted correctly when interacting with the browser. The service will need to check if Kiwi Browser is the active application.
*   **UI Element Identification**: Kiwi Browser's UI elements (e.g., URL bar, tab switcher) will need to be identified using their resource IDs or content descriptions, which can be discovered via UI inspection tools (e.g., Android Studio's Layout Inspector or `uiautomatorviewer`).

### 2.3 Error Handling and Feedback

*   **Toast Messages**: Continue using `showToast` for immediate feedback on script execution status and errors.
*   **Logging**: Implement more detailed logging to `Logcat` for debugging purposes.
*   **Robustness**: Implement retry mechanisms for finding elements or waiting for page loads to improve script reliability.

### 2.4 UI Integration (MainActivity.kt)

*   The `MainActivity` will need a way to trigger the execution of a script, potentially allowing the user to select a script file or directly input script commands for testing.

## 3. Implementation Steps

1.  **Refactor `MyAccessibilityService.kt`**: Organize existing code and prepare for new command handlers.
2.  **Implement Selector Logic**: Create a robust way to find `AccessibilityNodeInfo` based on text, ID, content description, and coordinates.
3.  **Implement Browser Commands**: Develop functions for `OPEN_URL`, `OPEN_NEW_TAB`, `OPEN_INCOGNITO`, etc.
4.  **Implement Form Interaction Commands**: Develop functions for `TYPE`, `CHECK`, `UNCHECK`.
5.  **Integrate Control Flow**: Add logic for `IF_EXISTS`, `ELSE`, `ENDIF`, `LOOP`, `ENDLOOP`.
6.  **Update `MainActivity.kt`**: Add UI elements to trigger and manage scripts.
7.  **Testing**: Thoroughly test each command and script scenario.
