import time
import pyautogui
import openpyxl
import keyboard
import re

# ============================================================
#  CONFIG — change only this section
# ============================================================
FILE_PATH      = r"C:\Users\ROHIT DAS\Downloads\Zee autonation data.xlsx"
DATA_START_ROW = 2
TRIGGER_COLUMN = 'Trigger'
KEYMAP_COLUMN  = 'KeyMap'

ALIASES = {
    'A/C NAME' : 'Debit',
    'NEW A/C'  : 'Credit',
    'NARRATION': 'Narration',
    'DATE'     : 'Date',
    'AMOUNT'   : 'Amount',
    'CHEQUE'   : 'Cheque',
}

# ──────────────────────────────────────────────────────────
#  SPEED CONTROLS  ← tune these to fix your software timing
# ──────────────────────────────────────────────────────────

# 1) TYPING speed — delay between each CHARACTER while writing text
#    Increase if software misses letters  (e.g. 0.05, 0.08, 0.1)
TYPING_INTERVAL = 0.04        # seconds between each character

# 2) KEY PRESS delay — pause AFTER every single key press
#    (Enter, Tab, Esc, Arrow up/down, etc.)
#    Increase if software misses arrows/esc/enter  (e.g. 0.1, 0.2, 0.3)
KEY_DELAY = 0.08              # seconds after each key press

# 3) HOTKEY delay — pause AFTER every Ctrl+X / Alt+X combo
HOTKEY_DELAY = 0.15           # seconds after each hotkey

# 4) VALUE delay — pause AFTER finishing typing a full value (after all chars)
#    Increase if software needs time to process typed text before next key
VALUE_POST_DELAY = 0.1        # seconds after finishing each v"..." value

# 5) GLOBAL MULTIPLIER — scales ALL above delays together
#    1.0 = normal, 1.5 = 50% slower, 2.0 = double, 0.5 = half speed
#    This is your single "overall entry speed" dial
SPEED_MULTIPLIER = 1.0

# ──────────────────────────────────────────────────────────
#  Computed delays (do not edit these)
# ──────────────────────────────────────────────────────────
_TYPING   = TYPING_INTERVAL  * SPEED_MULTIPLIER
_KEY      = KEY_DELAY        * SPEED_MULTIPLIER
_HOTKEY   = HOTKEY_DELAY     * SPEED_MULTIPLIER
_VAL_POST = VALUE_POST_DELAY * SPEED_MULTIPLIER

# ============================================================

wb    = openpyxl.load_workbook(FILE_PATH)
sheet = wb.active

headers = {}
for col in range(1, sheet.max_column + 1):
    val = sheet.cell(1, col).value
    if val:
        headers[str(val).strip().upper()] = col

current_row = DATA_START_ROW


# ── resolve alias ──────────────────────────────────────────
def resolve(col_name):
    key = col_name.strip().upper()
    if key in headers:
        return key
    for alias, real in ALIASES.items():
        if alias.upper() == key:
            return real.strip().upper()
    return None


# ── get cell ───────────────────────────────────────────────
def get_cell(row, col_name):
    resolved = resolve(col_name)
    if resolved is None or resolved not in headers:
        print(f"  [!] '{col_name}' not found. Columns: {list(headers.keys())}")
        print(f"      Add it to ALIASES in CONFIG section.")
        return ''
    val = sheet.cell(row, headers[resolved]).value
    return '' if val is None else str(val)


# ── parse KeyMap sequence ──────────────────────────────────
def parse_sequence(seq_str):
    tokens = []
    parts  = re.split(r'\s*\+\s*', seq_str.strip())
    i = 0
    while i < len(parts):
        part = parts[i].strip()

        m = re.match(r'^(\d+)\*(.+)$', part)
        if m:
            tokens.append(('repeat', int(m.group(1)), m.group(2).strip()))
            i += 1; continue

        m = re.match(r'^v"(.+)"$', part)
        if m:
            tokens.append(('value', m.group(1)))
            i += 1; continue

        m = re.match(r'^wait\(([0-9.]+)\)$', part, re.IGNORECASE)
        if m:
            tokens.append(('wait', float(m.group(1))))
            i += 1; continue

        if part.lower() in ('ctrl', 'alt'):
            if i + 1 < len(parts):
                tokens.append(('hotkey', part.lower(), parts[i+1].strip()))
                i += 2
            else:
                tokens.append(('key', part))
                i += 1
            continue

        if part.lower() == 'arrow':
            if i + 1 < len(parts):
                tokens.append(('key', parts[i+1].strip().lower()))
                i += 2
            else:
                i += 1
            continue

        tokens.append(('key', part))
        i += 1

    return tokens


# ── execute sequence ───────────────────────────────────────
def execute_sequence(seq_str, row):
    tokens = parse_sequence(seq_str)
    for token in tokens:
        kind = token[0]

        if kind == 'key':
            key = token[1].lower()
            pyautogui.press(key)
            time.sleep(_KEY)                  # ← KEY_DELAY * multiplier

        elif kind == 'repeat':
            _, n, key = token
            for _ in range(n):
                pyautogui.press(key.lower())
                time.sleep(_KEY)              # ← KEY_DELAY * multiplier each press

        elif kind == 'value':
            val = get_cell(row, token[1])
            if val:
                pyautogui.write(val, interval=_TYPING)   # ← TYPING_INTERVAL * multiplier
            time.sleep(_VAL_POST)             # ← VALUE_POST_DELAY * multiplier

        elif kind == 'wait':
            # wait() in KeyMap is NOT scaled by multiplier — it's your explicit timing
            time.sleep(token[1])

        elif kind == 'hotkey':
            _, mod, key = token
            pyautogui.hotkey(mod, key.lower())
            time.sleep(_HOTKEY)               # ← HOTKEY_DELAY * multiplier


# ── trigger helpers ────────────────────────────────────────
def get_row_trigger(row):
    val = get_cell(row, TRIGGER_COLUMN)
    val = re.sub(r'[^0-9]', '', val)
    return val if val else None

def get_row_keymap(row):
    val = get_cell(row, KEYMAP_COLUMN)
    return val.strip() if val.strip() else None

def row_has_data(row):
    first_col = list(headers.keys())[0]
    val = sheet.cell(row, headers[first_col]).value
    return val is not None and str(val).strip() != ''


# ── do one entry ───────────────────────────────────────────
def do_entry():
    global current_row

    if not row_has_data(current_row):
        print("All entries done. Script idle — Ctrl+C to exit.")
        return False

    keymap = None
    for r in range(current_row, 0, -1):
        km = get_row_keymap(r)
        if km:
            keymap = km
            break

    print(f"\nRow {current_row} → running...")
    if not keymap:
        print("  [!] No keymap found — skipping.")
        current_row += 1
        return True

    execute_sequence(keymap, current_row)
    current_row += 1
    print(f"Done | Next row: {current_row}")
    return True


# ── check next / auto-run ──────────────────────────────────
def check_and_fire():
    if not row_has_data(current_row):
        print("All entries done. Script idle — Ctrl+C to exit.")
        return

    trigger = get_row_trigger(current_row)
    if trigger is None:
        print(f"Row {current_row} → no trigger, auto-running in 0.5s...")
        time.sleep(0.5)
        ok = do_entry()
        if ok:
            check_and_fire()
    else:
        print(f"Waiting for trigger: {trigger}")


# ── keyboard hook ──────────────────────────────────────────
typed = []

def on_key(e):
    global typed
    if e.event_type != 'down':
        return
    if e.name in '0123456789':
        typed.append(e.name)
        if len(typed) > 10:
            typed.pop(0)
        expected = get_row_trigger(current_row)
        if expected and typed[-len(expected):] == list(expected):
            print(f"TRIGGERED ({expected})")
            typed.clear()
            do_entry()
            check_and_fire()
    else:
        typed.clear()

keyboard.hook(on_key)

# ── startup ────────────────────────────────────────────────
print("=" * 55)
print("  Auto Entry — Customizable")
print("=" * 55)
print(f"File            : {FILE_PATH}")
print(f"Columns         : {list(headers.keys())}")
print(f"Aliases         : {ALIASES}")
print(f"")
print(f"  SPEED SETTINGS (seconds):")
print(f"  Typing interval  : {TYPING_INTERVAL} × {SPEED_MULTIPLIER} = {_TYPING:.3f}s per char")
print(f"  Key delay        : {KEY_DELAY}  × {SPEED_MULTIPLIER} = {_KEY:.3f}s per key")
print(f"  Hotkey delay     : {HOTKEY_DELAY} × {SPEED_MULTIPLIER} = {_HOTKEY:.3f}s per hotkey")
print(f"  Value post-delay : {VALUE_POST_DELAY} × {SPEED_MULTIPLIER} = {_VAL_POST:.3f}s after value")
print(f"")
print(f"Start           : Row {current_row}")
print()
check_and_fire()
keyboard.wait()
