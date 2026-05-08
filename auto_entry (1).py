import time
import pyautogui
import pyperclip   # pip install pyperclip
import openpyxl
import keyboard
import re

# ============================================================
#  CONFIG — change only this section
# ============================================================
FILE_PATH = r"C:\Users\ROHIT DAS\Downloads\Zee autonation data.xlsx"
DATA_START_ROW = 2         # first row that has data (row 1 = headers)
TRIGGER_COLUMN = 'Trigger' # column name that holds the trigger key sequence
KEYMAP_COLUMN  = 'KeyMap'  # column name that holds the action sequence

# ── DELAY SETTINGS ──────────────────────────────────────────
# All values are in SECONDS.

# Delay BETWEEN each character while typing a value (v"ColumnName")
TYPING_INTERVAL     = 0.04   # e.g. 0.04 = 40ms between chars (fast)

# Delay AFTER finishing typing a full value field
AFTER_TYPING_DELAY  = 0.05   # small pause after each typed value

# Delay BEFORE pressing a navigation key (Arrow, Esc, Enter, Tab, etc.)
# This gives your software time to "settle" before the next key hits.
PRE_NAV_DELAY       = 0.15   # pause BEFORE nav key  (try 0.2–0.5 if still missing)

# Delay AFTER pressing a navigation key
# Lets the software process the nav action before next step.
POST_NAV_DELAY      = 0.15   # pause AFTER nav key   (try 0.2–0.5 if still missing)

# How long the key is physically HELD DOWN before releasing.
# ⬅️  THIS IS THE REAL FIX for repeated nav keys being missed.
# Some software ignores keys that are pressed+released too quickly.
# Increase to 0.05–0.1 if second/third Arrow press is still missed.
KEY_HOLD_DURATION   = 0.03   # seconds the key is held down before keyUp

# Delay AFTER a hotkey combo (Ctrl+S, Alt+X, etc.)
POST_HOTKEY_DELAY   = 0.2    # hotkeys often trigger saves/dialogs — give more time

# Delay for each repeated key press inside N*Key (e.g. 5*Enter)
REPEAT_KEY_DELAY    = 0.15   # gap between each repeated press — increase if missing

# ── WHICH KEYS COUNT AS "NAVIGATION" ─────────────────────────
# These get PRE_NAV_DELAY + POST_NAV_DELAY instead of the standard delay.
NAV_KEYS = {
    'up', 'down', 'left', 'right',
    'escape', 'esc',
    'enter', 'return',
    'tab', 'backspace', 'delete',
    'pageup', 'pagedown', 'home', 'end',
    'f1','f2','f3','f4','f5','f6',
    'f7','f8','f9','f10','f11','f12',
}
# ============================================================


wb = openpyxl.load_workbook(FILE_PATH)
sheet = wb.active

# Build header → column-index map (1-based)
headers = {}
for col in range(1, sheet.max_column + 1):
    val = sheet.cell(1, col).value
    if val:
        headers[str(val).strip().upper()] = col

current_row = DATA_START_ROW


# ── helpers ────────────────────────────────────────────────

def get_cell(row, col_name):
    """Return cell value by column header name (case-insensitive), or '' if missing/empty."""
    key = col_name.strip().upper()
    if key not in headers:
        print(f"  WARNING: Column '{col_name}' not found! Available: {list(headers.keys())}")
        return ''
    val = sheet.cell(row, headers[key]).value
    return '' if val is None else str(val)


def is_nav_key(key_str):
    """Return True if this key should use nav delays instead of standard delay."""
    return key_str.strip().lower() in NAV_KEYS


def press_key(key):
    """
    Press a single key with an explicit hold duration.
    Using keyDown + sleep + keyUp instead of pyautogui.press() ensures
    the software registers every keypress even when they happen close together.
    This is the core fix for repeated nav keys (Arrow, Esc, Enter) being missed.
    """
    pyautogui.keyDown(key)
    time.sleep(KEY_HOLD_DURATION)
    pyautogui.keyUp(key)


def parse_sequence(seq_str):
    """
    Parse a sequence string like:
      5*Enter + v"Name" + Tab + wait(1) + Ctrl+S
    Returns list of tokens.
    """
    tokens = []
    parts = re.split(r'\s*\+\s*', seq_str.strip())
    i = 0
    while i < len(parts):
        part = parts[i].strip()

        # N*Key  → repeat
        m = re.match(r'^(\d+)\*(.+)$', part)
        if m:
            n, key = int(m.group(1)), m.group(2).strip()
            tokens.append(('repeat', n, key))
            i += 1
            continue

        # v"ColumnName"  → value from excel column
        m = re.match(r'^v"(.+)"$', part)
        if m:
            tokens.append(('value', m.group(1)))
            i += 1
            continue

        # wait(seconds)
        m = re.match(r'^wait\(([0-9.]+)\)$', part, re.IGNORECASE)
        if m:
            tokens.append(('wait', float(m.group(1))))
            i += 1
            continue

        # Ctrl+X  (multi-part hotkey — consume next part too)
        if part.lower() == 'ctrl':
            if i + 1 < len(parts):
                tokens.append(('hotkey', 'ctrl', parts[i+1].strip()))
                i += 2
            else:
                tokens.append(('key', 'ctrl'))
                i += 1
            continue

        # Alt+X
        if part.lower() == 'alt':
            if i + 1 < len(parts):
                tokens.append(('hotkey', 'alt', parts[i+1].strip()))
                i += 2
            else:
                tokens.append(('key', 'alt'))
                i += 1
            continue

        # Arrow up / Arrow down / Arrow left / Arrow right
        if part.lower() == 'arrow':
            if i + 1 < len(parts):
                direction = parts[i+1].strip().lower()
                tokens.append(('key', direction))
                i += 2
            else:
                i += 1
            continue

        # plain key (Enter, Tab, Esc, etc.)
        tokens.append(('key', part))
        i += 1

    return tokens


def estimate_entry_time(seq_str, row):
    """
    Estimate how long (seconds) this entry will take based on current delay settings.
    Printed before each entry so you know the net time per row.
    """
    tokens = parse_sequence(seq_str)
    total = 0.0
    for token in tokens:
        kind = token[0]
        if kind == 'key':
            key = token[1]
            if is_nav_key(key):
                total += PRE_NAV_DELAY + POST_NAV_DELAY
            else:
                total += POST_NAV_DELAY  # non-nav plain keys
        elif kind == 'repeat':
            _, n, key = token
            if is_nav_key(key):
                total += n * (PRE_NAV_DELAY + POST_NAV_DELAY + REPEAT_KEY_DELAY)
            else:
                total += n * REPEAT_KEY_DELAY
        elif kind == 'value':
            val = get_cell(row, token[1])
            total += len(val) * TYPING_INTERVAL + AFTER_TYPING_DELAY
        elif kind == 'wait':
            total += token[1]
        elif kind == 'hotkey':
            total += POST_HOTKEY_DELAY
    return total


def execute_sequence(seq_str, row):
    """Execute a parsed sequence for the given data row."""
    tokens = parse_sequence(seq_str)

    for token in tokens:
        kind = token[0]

        if kind == 'key':
            key = token[1].lower()
            if is_nav_key(key):
                time.sleep(PRE_NAV_DELAY)
                press_key(key)           # keyDown + hold + keyUp
                time.sleep(POST_NAV_DELAY)
            else:
                press_key(key)
                time.sleep(POST_NAV_DELAY)

        elif kind == 'repeat':
            _, n, key = token
            key_lower = key.lower()
            for idx in range(n):
                if is_nav_key(key_lower):
                    # PRE gap on every press — this is what was missing before
                    time.sleep(PRE_NAV_DELAY)
                    press_key(key_lower) # keyDown + hold + keyUp — never skipped
                    time.sleep(POST_NAV_DELAY)
                else:
                    press_key(key_lower)
                # Gap between consecutive presses (applies to all keys in repeat)
                time.sleep(REPEAT_KEY_DELAY)

        elif kind == 'value':
            col_name = token[1]
            val = get_cell(row, col_name)
            if val:
                pyautogui.write(val, interval=TYPING_INTERVAL)
            time.sleep(AFTER_TYPING_DELAY)

        elif kind == 'wait':
            time.sleep(token[1])

        elif kind == 'hotkey':
            _, mod, key = token
            pyautogui.hotkey(mod.lower(), key.lower())
            time.sleep(POST_HOTKEY_DELAY)


# ── trigger system ─────────────────────────────────────────

def get_row_trigger(row):
    """Return trigger string for this row (digits only), or None if empty."""
    val = get_cell(row, TRIGGER_COLUMN)
    val = re.sub(r'[^0-9]', '', val)
    return val if val else None


def get_row_keymap(row):
    """Return keymap sequence string for this row, or None."""
    val = get_cell(row, KEYMAP_COLUMN)
    return val.strip() if val.strip() else None


def do_entry():
    global current_row

    # Find the keymap — walk back to last non-empty keymap row
    keymap = None
    for r in range(current_row, 0, -1):
        km = get_row_keymap(r)
        if km:
            keymap = km
            break

    date = get_cell(current_row, 'Date')
    if not date:
        print("Data khatam ho gaya 🚫")
        return

    est = estimate_entry_time(keymap, current_row) if keymap else 0
    print(f"\nRow {current_row} → executing sequence...")
    print(f"  KeyMap  : {keymap}")
    print(f"  Est time: ~{est:.1f}s")

    start = time.time()
    if keymap:
        # ── THE FIX ────────────────────────────────────────────────
        # keyboard.hook() intercepts pyautogui's OWN key events too.
        # Its background thread races with press_key()'s keyDown+sleep+keyUp.
        # On the second nav key the race causes keyUp to fire before the target
        # app registers keyDown — app sees an orphaned keyUp and silently drops it.
        # Fix: unhook while sending keys, re-hook immediately after.
        keyboard.unhook_all()
        try:
            execute_sequence(keymap, current_row)
        finally:
            keyboard.hook(on_key)   # always re-hook, even if execute crashes
    else:
        print("  ⚠️  No keymap found — skipping.")
    elapsed = time.time() - start

    current_row += 1
    print(f"Done ✅ | Actual: {elapsed:.1f}s | Next row: {current_row}")


# ── keyboard hook ──────────────────────────────────────────

typed = []
no_trigger_mode = False


def check_and_fire():
    """Called after every trigger match or on auto-mode."""
    global no_trigger_mode

    date_col = get_cell(current_row, list(headers.keys())[0])
    if not date_col:
        print("All entries done. Script idle — Ctrl+C to exit.")
        return

    next_trigger = get_row_trigger(current_row)
    if next_trigger is None:
        no_trigger_mode = True
        print(f"Row {current_row} has no trigger → auto-running in 0.5s")
        time.sleep(0.5)
        do_entry()
        check_and_fire()
    else:
        no_trigger_mode = False
        print(f"Waiting for trigger: {next_trigger}")


def on_key(e):
    global typed

    if e.event_type != "down":
        return

    if e.name in ('0','1','2','3','4','5','6','7','8','9'):
        typed.append(e.name)
        if len(typed) > 10:
            typed.pop(0)

        expected = get_row_trigger(current_row)
        if expected and typed[-len(expected):] == list(expected):
            print(f"TRIGGERED 🔥 ({expected})")
            typed.clear()
            do_entry()
            check_and_fire()
    else:
        typed.clear()


keyboard.hook(on_key)

print("=" * 50)
print("  Auto Entry Script — Customizable")
print("=" * 50)
print(f"File       : {FILE_PATH}")
print(f"Columns    : {list(headers.keys())}")
print(f"Start row  : {current_row}")
print()
print("── Current Delay Settings ──────────────────────")
print(f"  Typing interval (per char) : {TYPING_INTERVAL}s")
print(f"  After typing a field       : {AFTER_TYPING_DELAY}s")
print(f"  Key hold duration          : {KEY_HOLD_DURATION}s  ← increase if nav keys missed (try 0.05)")
print(f"  Before nav key (Esc/Arrow) : {PRE_NAV_DELAY}s")
print(f"  After  nav key (Esc/Arrow) : {POST_NAV_DELAY}s")
print(f"  After hotkey (Ctrl/Alt+X)  : {POST_HOTKEY_DELAY}s")
print(f"  Between repeated key press : {REPEAT_KEY_DELAY}s")
print("─────────────────────────────────────────────────")
print()
check_and_fire()
keyboard.wait()
