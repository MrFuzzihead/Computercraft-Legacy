# Batch I — in-game validation pack

Editor highlighting, the `lua` REPL and the `help` renderer.

## Run

```bat
copy docs\ingame-tests\batch-i\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then, at the shell:

| Script | What it does |
|---|---|
| `i1_editor_repl_help` | Automated: help topics and lookup, the `lua` settings, `cc.pretty`, directory-rooted `require`, `edit.default_extension` / `edit.autocomplete`. |
| `i2_highlighting` | Automated: the **token stream the editor colours from**, including multi-line state. Stages 12 visual checks. |

Both write a transcript (`/batch-i-results.txt`, `/batch-i2-results.txt`) and end with the verdict as
the last line on screen:

```
readlog_manual /batch-i2-results.txt
```

## What is automated, and what is not

CraftOS has no way to read a character's colour back, so **no script can assert that the editor
coloured a line**. The split is therefore:

| Check | How it is covered |
|---|---|
| ROM files compile | `EditorHighlightingTest` (JVM) |
| Lexer continuation protocol (long strings, long comments, blank lines, short strings) | `EditorHighlightingTest` (JVM) + `i2_highlighting` (in-game) |
| The token sequence per line, i.e. *what* the editor will colour | `i2_highlighting` (in-game) |
| Settings round-trip, help topic lookup, `cc.pretty` output, relative `require` | `i1_editor_repl_help` (in-game) |
| **The colours actually appearing on screen**, the section menu, scrolling and the continuation prompt | human eyes — staged by `i2_highlighting` |

## The staged checks in `i2_highlighting`

The script writes files into `/batch-i-visual` and prints a numbered list. In order of importance:

1. **`colours.lua`** — keywords yellow, numbers magenta, strings red, comments green.
2. **`longcomment.lua`** — every line from `--[[` to `]]` is green *including the blank line*. This is
   the continuation state working.
3. **Breaking the comment** — delete a character from `--[[` on the first line and every following
   line must change colour **immediately**. That is the state-propagation loop.
4. **`scrolling.lua`** — a 40-line comment. Scroll to the closing `]]` with the **mouse wheel**:
   the lines must still be green and the screen must not look corrupted. (The corruption was real:
   seeding the state for scrolled-off lines used to draw them at the cursor.) The wheel matters
   here — it scrolls the view without moving the cursor, which is the path that triggers seeding.

   On keys, since it is easy to assume otherwise:

   | Input | Behaviour in `edit` |
   |---|---|
   | Mouse wheel | Scrolls the **view** ±1 line; the cursor stays put. |
   | PageUp / PageDown | Moves the **cursor** a screenful; scrolling is a side effect. Doing nothing visible means the cursor is already at an end. |
   | Up / Down | Moves the cursor one line, scrolling only at the edges. |
5. **`edit.default_extension`** — `edit /batch-i-visual/typed` opens `typed.lua`.
6. **`edit.autocomplete = false`** — TAB does nothing; set it back and TAB completes.
7. **REPL** — multi-line pretty output, the `   > ` continuation prompt for `function g()`, and
   `local x = 1` then `x` printing `1`.
8. **`help speaker`** vs **`help intro`** — headings and the yellow menu bar with left/right section
   jumps for the first; plain text with no menu for the second, because it has no headings. That
   difference is the content-based markdown gate.

## Known limitations, stated rather than hidden

* **`PATCH` does not work** through `http.request` — `HttpURLConnection`'s method whitelist predates
  it. It reports `Unsupported HTTP method: PATCH` (Batch H).
* **`edit` colours with the real lexer**, so a *short* quoted string never carries colour onto the
  next line — that is Lua's own rule, not a bug. Only `[[ ]]` and `--[[ ]]` span lines.
* **PageUp / PageDown never arrive on the GTNH/lwjgl3ify client.** Reported in game and confirmed
  with `keyprobe_manual.lua`: `pageUp`/`pageDown` produce *no key event at all*, while Up, Down, Home
  and End arrive normally. The chain is `SDL key code -> lwjgl3ify Keyboard (LWJGL2 codes) -> key
  event -> keys.pageUp (201)`. Nothing in this repository translates or filters key codes — the ROM's
  `keys` table uses the LWJGL2 codes 201/209, `WidgetTerminal` forwards every `key > 0` it is given,
  and `GuiComputer` does not intercept them — so the event is lost in lwjgl3ify's SDL input path,
  outside this repository. Page scrolling in `edit` therefore works on a Java 8/LWJGL2 client and not
  on this one; the mouse wheel and Up/Down are unaffected. To confirm where it is lost, open
  Options -> Controls, click a keybind and press PageUp: if the game cannot see it either, it is the
  platform, not the mod.
* **`help`'s markdown renderer triggers on `.md` names or on a `#` heading line.** The topics shipped
  in this repository are extensionless plain text and only some use headings, so most of them render
  plain. That is deliberate: upstream's `.md`-only gate would never trigger here.
