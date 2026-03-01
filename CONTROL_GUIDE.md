# Phone Control Guide - Piece Taker App

This guide explains how to control an Android phone using the Piece Taker app with simple command-based instructions.

---

## Overview

The app captures the current screen's UI elements in a simple, readable format and sends it to a remote server. You can then send back simple commands to interact with the phone.

### Data Format

The app sends UI elements in this format:

```
=== UI TREE ===
Timestamp: 1709296800000
Package: com.whatsapp

--- Elements ---
Button{text='Send', resourceId='com.whatsapp:id/send_button', clickable=true}
EditText{text='', resourceId='com.whatsapp:id/message_input', focusable=true}
TextView{text='John Doe', resourceId='com.whatsapp:id/contact_name', clickable=true}
LinearLayout{resourceId='com.whatsapp:id/chat_list', scrollable=true}
```

Each element shows:
- **Widget Type** (Button, EditText, TextView, etc.)
- **text** - The visible text on the element
- **resourceId** - Unique identifier for the element
- **Boolean flags** - Properties like clickable, focusable, scrollable, longClickable

---

## Command Format

Send commands as plain text, one command per line. Each command follows this format:

```
commandType: parameter
```

### Supported Commands

#### 1. **CLICK** - Tap a button or clickable element
```
click: com.whatsapp:id/send_button
```
- Finds the element by resourceId and performs a click action
- Use this for buttons, links, touchable elements

#### 2. **LONGCLICK** - Long press an element
```
longclick: com.whatsapp:id/message_text
```
- Performs a long click (long press)
- Useful for context menus or selection

#### 3. **WRITE** - Type text in an input field
```
write: hello world
```
- Focuses on the first editable/input field and types the text
- Clear the field first if needed with `clear` command
- Example workflow:
  ```
  click: com.whatsapp:id/message_input
  write: Hello! How are you?
  click: com.whatsapp:id/send_button
  ```

#### 4. **SCROLL** - Scroll up or down
```
scroll: down
scroll: up
```
- Scrolls the first scrollable element in the current view
- **down** - Scroll forward/down
- **up** - Scroll backward/up
- Useful for navigating lists, feeds, or chat histories

---

## Complete Examples

### Example 1: Send a WhatsApp Message

**Step 1: App captures current screen**
```
=== UI TREE ===
Timestamp: 1709296800000
Package: com.whatsapp

--- Elements ---
EditText{text='', resourceId='com.whatsapp:id/message_input', focusable=true}
Button{text='Send', resourceId='com.whatsapp:id/send_button', clickable=true}
TextView{text='Active now', resourceId='com.whatsapp:id/status', ...}
```

**Step 2: Send these commands**
```
write: Hello, how are you?
click: com.whatsapp:id/send_button
```

**Result:** Message is typed and sent!

---

### Example 2: Open an App and Navigate

**Commands:**
```
click: com.android.systemui:id/home
write: instagram
click: com.google.android.apps.nexuslauncher:id/search_result
scroll: down
scroll: down
click: com.instagram.android:id/post_like_button
```

**Explanation:**
1. Click home button
2. Type "instagram" in search
3. Click first search result
4. Scroll down twice in feed
5. Click like button on a post

---

### Example 3: Fill a Form

**Commands:**
```
click: com.example.myapp:id/name_field
write: John Doe
click: com.example.myapp:id/email_field
write: john@example.com
click: com.example.myapp:id/submit_button
```

**Result:** Form is filled and submitted

---

## Finding Resource IDs

When you receive the UI tree from the app, it looks like this:

```
Button{text='Send', resourceId='com.whatsapp:id/send_button', clickable=true}
                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    Use this in your click command!
```

### Tips for Finding Elements:

1. **By Text Content**: If an element has visible text, it will be shown:
   ```
   Button{text='Send', resourceId='com.whatsapp:id/send_button', ...}
   ```
   The text='Send' helps you identify which button it is.

2. **By Element Type**: The widget type is listed first:
   ```
   EditText{...}  <- Input field
   Button{...}    <- Clickable button
   TextView{...}  <- Non-editable text
   LinearLayout{...} <- Container (may be scrollable)
   ```

3. **By Position in List**: Elements are listed top-to-bottom, so you can see the order they appear

---

## Advanced Tips

### Throttling
- The app captures the UI every **500ms** to avoid overload
- Don't send commands faster than this interval

### Element Discovery
- Only **interactive elements** are sent (clickable, focusable, scrollable)
- Buttons, inputs, links, containers are included
- Decorative elements are filtered out to reduce data

### Scrolling Strategy
```
scroll: down
scroll: down
scroll: down
```
- Each scroll command scrolls the first scrollable element on screen
- Repeat as needed to find target content

### Long Interaction Sequences
```
click: com.example:id/menu
scroll: down
click: com.example:id/option_2
write: search_term
click: com.example:id/search_button
scroll: up
click: com.example:id/first_result
```

---

## Error Handling

If a command fails:
1. **Element not found** - Resource ID doesn't match current screen state
   - Solution: Request new UI tree capture, screen may have changed

2. **Text not written** - No editable field found
   - Solution: Click on the input field first, then write

3. **Scroll not working** - No scrollable element on screen
   - Solution: Check if page is already at bottom, or click scrollable area first

---

## Command Sending Method

**HTTP Request Format:**
```
POST to your ngrok URL
Content-Type: text/plain

click: com.whatsapp:id/send_button
write: Hello
click: com.whatsapp:id/send_button
```

**Example cURL:**
```bash
curl -X POST https://your-ngrok-url.ngrok.io \
  -H "Content-Type: text/plain" \
  -d "write: Hello
click: com.whatsapp:id/send_button"
```

---

## Workflow Summary

1. **Capture**: App sends current UI state (Button, EditText, etc. objects)
2. **Analyze**: You read the UI tree to find element resourceIds
3. **Command**: Send simple commands (click, write, scroll)
4. **Execute**: App finds elements by resourceId and performs actions
5. **Repeat**: Request new UI capture after state changes

---

## Quick Reference

| Command | Format | Example |
|---------|--------|---------|
| Click | `click: resourceId` | `click: com.whatsapp:id/send_button` |
| Long Click | `longclick: resourceId` | `longclick: com.whatsapp:id/message_text` |
| Write | `write: text` | `write: Hello world` |
| Scroll Down | `scroll: down` | `scroll: down` |
| Scroll Up | `scroll: up` | `scroll: up` |

---

## Notes

- All commands are **case-insensitive**
- Resource IDs are package-specific (e.g., `com.whatsapp:id/...`)
- The app runs as an **Accessibility Service** for reliable automation
- Commands execute in order, one after another
- Each command completes before the next one starts
